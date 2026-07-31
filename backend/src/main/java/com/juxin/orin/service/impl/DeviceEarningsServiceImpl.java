package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.Device;
import com.juxin.orin.entity.DeviceEarnings;
import com.juxin.orin.entity.ApiMerchant;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.service.IDeviceEarningsService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.InviteLevelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

@Slf4j
@Service
public class DeviceEarningsServiceImpl extends ServiceImpl<DeviceEarningsMapper, DeviceEarnings>
        implements IDeviceEarningsService {

    @Autowired
    private DeviceEarningsMapper earningsMapper;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private com.juxin.orin.service.IDeviceOfflinePeriodService offlinePeriodService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private InviteLevelConfigService inviteLevelConfigService;

    @Autowired
    private com.juxin.orin.service.IInviteService inviteService;

    @Autowired
    private com.juxin.orin.service.IApiMerchantService apiMerchantService;

    @Autowired
    private TransactionOperations transactionOperations;

    private enum SettlementStatus {
        CREDITED,
        DENIED,
        SKIPPED
    }

    private record SettlementResult(SettlementStatus status, BigDecimal amount) {
        private static SettlementResult credited(BigDecimal amount) {
            return new SettlementResult(SettlementStatus.CREDITED, amount);
        }

        private static SettlementResult denied() {
            return new SettlementResult(SettlementStatus.DENIED, BigDecimal.ZERO);
        }

        private static SettlementResult skipped() {
            return new SettlementResult(SettlementStatus.SKIPPED, BigDecimal.ZERO);
        }
    }

    @Override
    public Map<String, BigDecimal> getUserEarnings(Long userId) {
        Map<String, BigDecimal> result = new HashMap<>();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        if (userId != null) {
            result.put("yesterday", earningsMapper.sumByUserAndDate(userId, yesterday));
            result.put("total", earningsMapper.sumByUser(userId));
        } else {
            result.put("yesterday", earningsMapper.sumByDate(yesterday));
            result.put("total", earningsMapper.sumAll());
        }

        return result;
    }

    @Override
    public Map<String, BigDecimal> getSystemEarnings() {
        return getUserEarnings(null);
    }

    @Autowired
    private com.juxin.orin.service.IAppUserService appUserService;

    @Override
    public void generateDailyEarnings() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate settlementDate = now.toLocalDate().minusDays(1);
        LocalDateTime dayStart = settlementDate.atStartOfDay();
        LocalDateTime dayEnd = settlementDate.plusDays(1).atStartOfDay();

        log.info("========== 开始执行每日收益结算: date={} ==========", settlementDate);

        try {
            // 只结算完整参与了该自然日的已绑定设备，当前是否在线不影响判定。
            List<Device> devices = deviceService.lambdaQuery()
                    .isNotNull(Device::getUserId)
                    .isNotNull(Device::getBindTime)
                    .le(Device::getBindTime, dayStart)
                    .list();

            // 收益任务可能比离线扫描更早触发，结算前先补齐开放离线区间。
            offlinePeriodService.ensureOfflinePeriods(devices, getOfflineThresholdSeconds(), now);

            log.info("每日收益待检查设备: date={}, count={}", settlementDate, devices.size());

            int successCount = 0;
            int deniedCount = 0;
            int errorCount = 0;

            for (Device device : devices) {
                try {
                    SettlementResult result = settleDeviceForDate(
                            device.getId(), now, settlementDate, dayStart, dayEnd);
                    if (result.status() == SettlementStatus.CREDITED) {
                        successCount++;
                    } else if (result.status() == SettlementStatus.DENIED) {
                        deniedCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("设备收益结算异常: deviceId={}, sn={}, error={}",
                            device.getId(), device.getSn(), e.getMessage(), e);
                }
            }

            log.info("========== 每日收益结算完成: date={}, 入账={}台, 离线超限={}台, 失败={}台 ==========",
                    settlementDate, successCount, deniedCount, errorCount);

        } catch (Exception e) {
            log.error("收益结算任务执行异常: {}", e.getMessage(), e);
        }
    }

    private SettlementResult settleDeviceForDate(
            Long deviceId,
            LocalDateTime now,
            LocalDate settlementDate,
            LocalDateTime dayStart,
            LocalDateTime dayEnd) {
        SettlementResult result = transactionOperations.execute(status -> {
            // 串行化同一设备的定时结算和手动补偿，避免先查后写重复入账。
            Device device = deviceService.lambdaQuery()
                    .eq(Device::getId, deviceId)
                    .last("FOR UPDATE")
                    .one();
            if (device == null
                    || device.getUserId() == null
                    || device.getBindTime() == null
                    || device.getBindTime().isAfter(dayStart)) {
                return SettlementResult.skipped();
            }

            if (earningsMapper.countByDeviceAndDate(deviceId, settlementDate) > 0) {
                if (device.getLastPayTime() == null || device.getLastPayTime().isBefore(dayEnd)) {
                    device.setLastPayTime(now);
                    deviceService.updateById(device);
                }
                return SettlementResult.skipped();
            }

            if (appUserService.getById(device.getUserId()) == null) {
                if (device.getLastPayTime() == null || device.getLastPayTime().isBefore(dayEnd)) {
                    device.setLastPayTime(now);
                    deviceService.updateById(device);
                }
                log.info("跳过无有效用户的设备收益结算: deviceId={}, userId={}",
                        device.getId(), device.getUserId());
                return SettlementResult.skipped();
            }

            if (exceedsDailyOfflineLimit(device, dayStart, dayEnd)) {
                recordZeroEarnings(device, now, settlementDate);
                return SettlementResult.denied();
            }
            return SettlementResult.credited(processDeviceEarnings(device, now, settlementDate));
        });
        if (result == null) {
            throw new IllegalStateException("设备收益结算未返回结果");
        }
        return result;
    }

    /**
     * 处理单个设备的收益入账。
     */
    BigDecimal processDeviceEarnings(Device device, LocalDateTime now, LocalDate today) {
        // 用户进入回收站后，MyBatis-Plus 的逻辑删除会使 getById 返回 null。
        // 设备绑定会保留以便后续恢复，但删除期间不能继续产生收益或算力。
        com.juxin.orin.entity.AppUser user = appUserService.getById(device.getUserId());
        if (user == null) {
            device.setLastPayTime(now);
            deviceService.updateById(device);
            log.info("跳过无有效用户的设备收益结算: deviceId={}, userId={}",
                    device.getId(), device.getUserId());
            return BigDecimal.ZERO;
        }

        BigDecimal baseEarnings = calculateDailyBaseEarnings(device.getId(), today);
        BigDecimal earnings = baseEarnings;
        BigDecimal rate = BigDecimal.ZERO;

        // 获取用户，根据等级计算实际收益
        if (user != null) {
            int level = user.getLevel() != null ? user.getLevel() : 0;

            // 商户设备：优先使用商户等级
            if (device.getMerchantId() != null) {
                try {
                    ApiMerchant merchant = apiMerchantService.getById(device.getMerchantId());
                    if (merchant != null && merchant.getLevel() != null && merchant.getLevel() > 0) {
                        level = merchant.getLevel();
                    }
                } catch (Exception e) {
                    log.warn("查询商户等级失败: merchantId={}", device.getMerchantId());
                }
            }

            rate = inviteLevelConfigService.getLevelRate(level);

            earnings = baseEarnings.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        // 记录收益
        DeviceEarnings record = new DeviceEarnings();
        record.setDeviceId(device.getId());
        record.setUserId(device.getUserId());
        record.setAmount(earnings);
        record.setDate(today);
        record.setCreateTime(now);
        this.save(record);

        // 更新用户余额
        if (user != null) {
            user.setBalance(user.getBalance().add(earnings));

            int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));
            int quotaAdd = earnings.multiply(new java.math.BigDecimal(hashrateRate)).intValue();

            user.setQuota((user.getQuota() != null ? user.getQuota() : 0) + quotaAdd);
            appUserService.updateById(user);

            // 邀请人分润逻辑 - 无限代级差分润
            BigDecimal currentRateAllocated = rate;
            Long currentLoopUserId = user.getId();
            int loopCount = 0;

            while (loopCount < 20) {
                try {
                    AppUser currentUserNode = appUserService.getById(currentLoopUserId);
                    if (currentUserNode == null || currentUserNode.getInviterId() == null) {
                        break;
                    }

                    AppUser inviter = appUserService.getById(currentUserNode.getInviterId());
                    if (inviter == null) {
                        break;
                    }

                    currentLoopUserId = inviter.getId();
                    loopCount++;

                    int inviterLevel = inviter.getLevel() != null ? inviter.getLevel() : 0;
                    BigDecimal inviterRate = inviteLevelConfigService.getLevelRate(inviterLevel);

                    if (inviterRate.compareTo(currentRateAllocated) > 0) {
                        BigDecimal diffRate = inviterRate.subtract(currentRateAllocated);
                        BigDecimal inviterReward = baseEarnings.multiply(diffRate).setScale(2,
                                java.math.RoundingMode.HALF_UP);

                        if (inviterReward.compareTo(BigDecimal.ZERO) > 0) {
                            inviteService.grantReward(inviter.getId(), user.getId(), "earnings", inviterReward,
                                    device.getId());
                        }

                        currentRateAllocated = inviterRate;
                    }

                } catch (Exception e) {
                    log.error("计算邀请分润循环异常: userId={}, error={}", user.getId(), e.getMessage());
                    break;
                }
            }

            // ==================== 新增：商户（渠道代理）级差结算 ====================
            if (user.getMerchantId() != null) {
                try {
                    ApiMerchant merchant = apiMerchantService.getById(user.getMerchantId());
                    if (merchant != null && merchant.getLevel() != null) {
                        int merchantLevel = merchant.getLevel();
                        BigDecimal merchantRate = inviteLevelConfigService.getLevelRate(merchantLevel);

                        // 如果商户等级比目前已分配出的最高等级还要高，则商户拿走剩余级差
                        if (merchantRate.compareTo(currentRateAllocated) > 0) {
                            BigDecimal diffRate = merchantRate.subtract(currentRateAllocated);
                            BigDecimal merchantReward = baseEarnings.multiply(diffRate).setScale(2,
                                    java.math.RoundingMode.HALF_UP);

                            if (merchantReward.compareTo(BigDecimal.ZERO) > 0) {
                                if (merchant.getBindUserId() != null) {
                                    AppUser bindUser = appUserService.getById(merchant.getBindUserId());
                                    if (bindUser != null) {
                                        bindUser.setBalance(
                                                (bindUser.getBalance() != null ? bindUser.getBalance() : BigDecimal.ZERO)
                                                        .add(merchantReward));
                                        appUserService.updateById(bindUser);
                                        log.info("渠道商分润结算入绑定账号: merchantName={}, bindUserId={}, amount={}",
                                                merchant.getMerchantName(), bindUser.getId(), merchantReward);
                                    } else {
                                        merchant.setBalance(
                                                (merchant.getBalance() != null ? merchant.getBalance() : BigDecimal.ZERO)
                                                        .add(merchantReward));
                                        apiMerchantService.updateById(merchant);
                                        log.warn("渠道商绑定账号不存在，回退累计到商户余额: merchantId={}, bindUserId={}",
                                                merchant.getId(), merchant.getBindUserId());
                                    }
                                } else {
                                    merchant.setBalance(
                                            (merchant.getBalance() != null ? merchant.getBalance() : BigDecimal.ZERO)
                                                    .add(merchantReward));
                                    apiMerchantService.updateById(merchant);
                                    log.info("渠道商分润结算入商户余额: merchantName={}, amount={}",
                                            merchant.getMerchantName(), merchantReward);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("渠道商结算异常: merchantId={}, userId={}, error={}", user.getMerchantId(), user.getId(),
                            e.getMessage());
                }
            }
        }

        // 重置结算时间为当前时间，并叠加聚芯算力值
        device.setLastPayTime(now);
        int currentHashrate = device.getHashrate() != null ? device.getHashrate() : 0;
        device.setHashrate(currentHashrate + 100);
        deviceService.updateById(device);

        log.debug("设备收益结算成功: deviceId={}, userId={}, baseEarnings={}, earnings={}",
                device.getId(), device.getUserId(), baseEarnings, earnings);
        return earnings;
    }

    @Override
    public Map<String, Object> compensateEarnings(int days) {
        log.info("========== 开始执行补偿收益任务: 补偿 {} 天 ==========", days);

        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate latestSettlementDate = now.toLocalDate().minusDays(1);

        int successCount = 0;
        int failCount = 0;
        int skippedCount = 0;
        int totalRecords = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            // 补偿同样按自然日离线规则处理，不能绕过离线处罚。
            List<Device> allDevices = deviceService.lambdaQuery()
                    .isNotNull(Device::getUserId)
                    .isNotNull(Device::getBindTime)
                    .list();
            offlinePeriodService.ensureOfflinePeriods(allDevices, getOfflineThresholdSeconds(), now);

            log.info("补偿收益: 查询到 {} 台已绑定设备", allDevices.size());

            for (Device device : allDevices) {
                try {
                    boolean settledAnyDate = false;
                    for (int day = days - 1; day >= 0; day--) {
                        LocalDate settlementDate = latestSettlementDate.minusDays(day);
                        LocalDateTime dayStart = settlementDate.atStartOfDay();
                        LocalDateTime dayEnd = settlementDate.plusDays(1).atStartOfDay();
                        SettlementResult settlement = settleDeviceForDate(
                                device.getId(), now, settlementDate, dayStart, dayEnd);
                        if (settlement.status() == SettlementStatus.SKIPPED) {
                            continue;
                        }
                        settledAnyDate = true;
                        totalRecords++;
                        totalAmount = totalAmount.add(settlement.amount());
                    }

                    if (settledAnyDate) {
                        successCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("补偿收益异常: deviceId={}, sn={}, error={}",
                            device.getId(), device.getSn(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("补偿收益任务执行异常: {}", e.getMessage(), e);
        }

        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("skippedCount", skippedCount);
        result.put("totalDevices", successCount + failCount + skippedCount);
        result.put("totalRecords", totalRecords);
        result.put("totalAmount", totalAmount.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("days", days);

        log.info("========== 补偿收益任务完成: 成功={}台, 失败={}台, 记录={}条, 总金额=¥{} ==========",
                successCount, failCount, totalRecords, totalAmount);

        return result;
    }

    boolean exceedsDailyOfflineLimit(
            Device device,
            LocalDateTime dayStart,
            LocalDateTime dayEnd) {
        if (device.getType() != null && device.getType() == 1) {
            return false;
        }
        BigDecimal maxOfflineHours = getMaxDailyOfflineHours();
        long offlineSeconds = device.getLastHeartbeatTime() == null
                ? java.time.Duration.between(dayStart, dayEnd).getSeconds()
                : offlinePeriodService.getOfflineSeconds(device.getId(), dayStart, dayEnd);
        BigDecimal maxOfflineSeconds = maxOfflineHours.multiply(BigDecimal.valueOf(3600));
        return BigDecimal.valueOf(offlineSeconds).compareTo(maxOfflineSeconds) > 0;
    }

    private void recordZeroEarnings(Device device, LocalDateTime now, LocalDate settlementDate) {
        DeviceEarnings record = new DeviceEarnings();
        record.setDeviceId(device.getId());
        record.setUserId(device.getUserId());
        record.setAmount(BigDecimal.ZERO.setScale(2));
        record.setDate(settlementDate);
        record.setCreateTime(now);
        save(record);

        device.setLastPayTime(now);
        deviceService.updateById(device);
        log.info("设备当日累计离线超限，收益记为0: deviceId={}, date={}", device.getId(), settlementDate);
    }

    private BigDecimal getMaxDailyOfflineHours() {
        BigDecimal hours;
        try {
            hours = new BigDecimal(configService.getConfig("earnings.maxDailyOfflineHours", "24"));
        } catch (NumberFormatException e) {
            return BigDecimal.valueOf(24);
        }
        if (hours.compareTo(BigDecimal.ZERO) < 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0) {
            return BigDecimal.valueOf(24);
        }
        return hours;
    }

    private int getOfflineThresholdSeconds() {
        try {
            return Math.max(1, Integer.parseInt(configService.getConfig("device.offlineThreshold", "180")));
        } catch (NumberFormatException ignored) {
            return 180;
        }
    }

    BigDecimal calculateDailyBaseEarnings(Long deviceId, LocalDate settlementDate) {
        BigDecimal minRate = getDailyRangeRate("earnings.dailyMinRate");
        BigDecimal maxRate = getDailyRangeRate("earnings.dailyMaxRate");
        if (minRate.compareTo(BigDecimal.ZERO) < 0 || maxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("每天收益金额不能小于 0");
        }
        if (minRate.compareTo(maxRate) > 0) {
            throw new IllegalStateException("每天收益最低金额不能大于最高金额");
        }

        long minHundredths = minRate.movePointRight(2).longValueExact();
        long maxHundredths = maxRate.movePointRight(2).longValueExact();
        long selectedHundredths = minHundredths;
        if (maxHundredths > minHundredths) {
            long deviceSeed = deviceId == null ? 0L : deviceId;
            long seed = (deviceSeed * 0x9E3779B97F4A7C15L) ^ settlementDate.toEpochDay();
            selectedHundredths = new SplittableRandom(seed).nextLong(minHundredths, maxHundredths + 1);
        }

        return BigDecimal.valueOf(selectedHundredths, 2);
    }

    private BigDecimal getDailyRangeRate(String key) {
        String legacyHourlyRate = configService.getConfig("earnings.hourlyRate", "2.4");
        String legacyDailyRate = configService.getConfig("earnings.dailyRate", legacyHourlyRate);
        return new BigDecimal(configService.getConfig(key, legacyDailyRate))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
