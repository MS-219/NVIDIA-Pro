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
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private InviteLevelConfigService inviteLevelConfigService;

    @Autowired
    private com.juxin.orin.service.IInviteService inviteService;

    @Autowired
    private com.juxin.orin.service.IApiMerchantService apiMerchantService;

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
        LocalDate today = now.toLocalDate();

        log.info("========== 开始执行收益结算任务 ==========");

        try {
            // 从系统配置读取心跳超时时间（秒）
            String heartbeatTimeoutStr = configService.getConfig("device.heartbeatTimeout", "120");

            int heartbeatTimeoutSeconds = Integer.parseInt(heartbeatTimeoutStr);

            LocalDateTime activeThreshold = now.minusSeconds(heartbeatTimeoutSeconds);

            // 获取参与正常收益结算的实体/边缘设备：排除虚拟设备(type=1)
            List<Device> realDevices = deviceService.lambdaQuery()
                    .eq(Device::getStatus, 1)
                    .ge(Device::getLastHeartbeatTime, activeThreshold)
                    .isNotNull(Device::getUserId)
                    .isNotNull(Device::getLastPayTime)
                    .and(w -> w.isNull(Device::getType).or().ne(Device::getType, 1))
                    .list();

            // 获取虚拟设备：不检查心跳时间，始终参与结算
            List<Device> virtualDevices = deviceService.lambdaQuery()
                    .eq(Device::getStatus, 1)
                    .eq(Device::getType, 1)
                    .isNotNull(Device::getUserId)
                    .isNotNull(Device::getLastPayTime)
                    .list();

            log.info("查询到设备: 实体/边缘设备={}台, 虚拟设备={}台", realDevices.size(), virtualDevices.size());

            // 合并设备列表
            List<Device> onlineDevices = new java.util.ArrayList<>();
            onlineDevices.addAll(realDevices);
            onlineDevices.addAll(virtualDevices);

            int successCount = 0;
            int errorCount = 0;

            for (Device device : onlineDevices) {
                try {
                    // 每台设备连续运行满一天后结算一次。
                    LocalDateTime lastPay = device.getLastPayTime();
                    if (lastPay.plusDays(1).isAfter(now)) {
                        continue;
                    }

                    // 兼容从小时结算升级的当天数据，防止切换后重复入账。
                    if (earningsMapper.countByDeviceAndDate(device.getId(), today) > 0) {
                        device.setLastPayTime(now);
                        deviceService.updateById(device);
                        continue;
                    }

                    processDeviceEarnings(device, now, today);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.error("设备收益结算异常: deviceId={}, sn={}, error={}",
                            device.getId(), device.getSn(), e.getMessage(), e);
                }
            }

            log.info("========== 收益结算任务完成: 成功={}台, 失败={}台 ==========", successCount, errorCount);

        } catch (Exception e) {
            log.error("收益结算任务执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理单个设备的收益结算（独立事务）
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public BigDecimal processDeviceEarnings(Device device, LocalDateTime now, LocalDate today) {
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
        LocalDate today = now.toLocalDate();

        int successCount = 0;
        int failCount = 0;
        int skippedCount = 0;
        int totalRecords = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            // 获取所有已绑定且在线的设备（真实设备 + 虚拟设备）
            List<Device> allDevices = deviceService.lambdaQuery()
                    .eq(Device::getStatus, 1)
                    .isNotNull(Device::getUserId)
                    .isNotNull(Device::getLastPayTime)
                    .list();

            log.info("补偿收益: 查询到 {} 台已绑定且在线的设备", allDevices.size());

            for (Device device : allDevices) {
                try {
                    com.juxin.orin.entity.AppUser user = appUserService.getById(device.getUserId());
                    if (user == null) {
                        device.setLastPayTime(now);
                        deviceService.updateById(device);
                        skippedCount++;
                        log.info("补偿收益跳过无有效用户的设备: deviceId={}, userId={}",
                                device.getId(), device.getUserId());
                        continue;
                    }
                    // 每天调用一次结算，补偿记录按对应日期入账。
                    for (int day = days - 1; day >= 0; day--) {
                        BigDecimal creditedAmount = processDeviceEarnings(device, now, today.minusDays(day));
                        totalRecords++;
                        totalAmount = totalAmount.add(creditedAmount);
                    }

                    successCount++;
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
