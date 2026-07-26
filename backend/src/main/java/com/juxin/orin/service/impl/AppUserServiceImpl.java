package com.juxin.orin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.juxin.orin.entity.Device;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.ISystemConfigService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements IAppUserService {

    @Autowired
    private ISystemConfigService configService;

    @Autowired
    @Lazy
    private IDeviceService deviceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public String wxLogin(String openid) {
        AppUser user = getByOpenid(openid);

        if (user == null) {
            if (baseMapper.countDeletedByOpenid(openid) > 0) {
                throw new IllegalStateException("账号已删除，请联系管理员从回收站恢复");
            }
            // 新用户自动注册
            user = new AppUser();
            user.setId(generateUniqueId()); // 生成 6 位唯一随机 ID
            user.setOpenid(openid);
            user.setNickname("微信用户");
            user.setBalance(BigDecimal.ZERO);
            user.setQuota(0);
            user.setCreateTime(LocalDateTime.now());
            this.save(user);
        }

        // 生成 JWT Token
        return JwtUtil.generateToken(user.getId(), user.getOpenid(), "app");
    }

    @Override
    public AppUser getByOpenid(String openid) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUser::getOpenid, openid);
        return this.getOne(wrapper);
    }

    /**
     * 生成唯一的 6 位随机数字 ID
     */
    private Long generateUniqueId() {
        java.util.Random random = new java.util.Random();
        while (true) {
            // 生成 100000 - 999999 之间的随机数
            long id = 100000 + random.nextInt(900000);
            // 检查数据库中是否存在该 ID
            if (baseMapper.countByIdIncludingDeleted(id) == 0) {
                return id;
            }
        }
    }

    @Override
    public void updateLevel(Long userId) {
        if (userId == null)
            return;

        // 1. 获取该用户
        AppUser user = this.getById(userId);
        if (user == null || Boolean.TRUE.equals(user.getLevelManual())) {
            return; // 锁定状态不自动更新
        }

        // 2. 获取等级配置阈值
        int[] thresholds = new int[6];
        for (int i = 1; i <= 5; i++) {
            String val = configService.getConfig("invite.level" + i + ".threshold", null);
            thresholds[i] = (val != null) ? Integer.parseInt(val) : (new int[] { 0, 1, 100, 300, 1000, 3000 })[i];
        }

        // 3. 统计该用户【名下全团队】总设备数 (递归下级)
        List<Device> allDevices = deviceService.lambdaQuery().isNotNull(Device::getUserId).list();
        List<AppUser> allUsers = this.list();
        Map<Long, Long> userInviterMap = new java.util.HashMap<>();
        for (AppUser u : allUsers) {
            userInviterMap.put(u.getId(), u.getInviterId());
        }

        int teamTotal = 0;
        for (Device d : allDevices) {
            Long currentId = d.getUserId();
            int depth = 0;
            while (currentId != null && depth < 50) {
                if (currentId.equals(userId)) {
                    teamTotal++;
                    break;
                }
                currentId = userInviterMap.get(currentId);
                depth++;
            }
        }

        // 4. 计算新等级
        int newLevel = 0;
        for (int i = 5; i >= 1; i--) {
            if (teamTotal >= thresholds[i]) {
                newLevel = i;
                break;
            }
        }

        // 5. 仅更新该用户
        if (user.getLevel() == null || user.getLevel() != newLevel) {
            this.lambdaUpdate()
                    .set(AppUser::getLevel, newLevel)
                    .eq(AppUser::getId, userId)
                    .update();
        }
    }

    @Override
    public void updateAllUserLevels() {
        // 1. 获取等级配置阈值
        int[] thresholds = new int[6];
        for (int i = 1; i <= 5; i++) {
            String val = configService.getConfig("invite.level" + i + ".threshold", null);
            thresholds[i] = (val != null) ? Integer.parseInt(val) : (new int[] { 0, 1, 100, 300, 1000, 3000 })[i];
        }

        // 2. 获取所有用户
        List<AppUser> allUsers = this.list();
        Map<Long, AppUser> userMap = new java.util.HashMap<>();
        for (AppUser u : allUsers) {
            userMap.put(u.getId(), u);
        }

        // 3. 统计每个用户自身拥有的设备数
        List<Device> allBoundDevices = deviceService.lambdaQuery().isNotNull(Device::getUserId).list();
        Map<Long, Integer> userOwnDevices = new java.util.HashMap<>();
        for (Device d : allBoundDevices) {
            if (d.getUserId() != null) {
                Long uid = Long.valueOf(d.getUserId().toString());
                userOwnDevices.put(uid, userOwnDevices.getOrDefault(uid, 0) + 1);
            }
        }

        // 4. 计算全员团队总设备数
        Map<Long, Integer> teamDeviceCount = new java.util.HashMap<>();
        for (AppUser user : allUsers) {
            Long uid = user.getId();
            int ownCount = userOwnDevices.getOrDefault(uid, 0);
            if (ownCount > 0) {
                Long currentId = uid;
                int depth = 0;
                while (currentId != null && depth < 50) {
                    teamDeviceCount.put(currentId, teamDeviceCount.getOrDefault(currentId, 0) + ownCount);
                    AppUser current = userMap.get(currentId);
                    currentId = (current != null && current.getInviterId() != null)
                            ? Long.valueOf(current.getInviterId().toString())
                            : null;
                    depth++;
                }
            }
        }

        // 5. 更新所有符合条件的用户
        for (AppUser user : allUsers) {
            if (Boolean.TRUE.equals(user.getLevelManual())) {
                continue;
            }
            int totalDevices = teamDeviceCount.getOrDefault(user.getId(), 0);
            int newLevel = 0;
            for (int i = 5; i >= 1; i--) {
                if (totalDevices >= thresholds[i]) {
                    newLevel = i;
                    break;
                }
            }
            if (user.getLevel() == null || user.getLevel() != newLevel) {
                this.lambdaUpdate()
                        .set(AppUser::getLevel, newLevel)
                        .eq(AppUser::getId, user.getId())
                        .update();
            }
        }
    }

    @Autowired
    private com.juxin.orin.service.IApiMerchantService apiMerchantService;

    @Override
    public AppUser syncExternalUser(Long merchantId, String externalUserId, String nickname, String avatarUrl) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUser::getMerchantId, merchantId)
                .eq(AppUser::getExternalUserId, externalUserId);
        AppUser user = this.getOne(wrapper);

        if (user == null) {
            user = new AppUser();
            user.setId(generateUniqueId());
            user.setOpenid(buildExternalOpenid(merchantId, externalUserId));
            user.setMerchantId(merchantId);
            user.setExternalUserId(externalUserId);
            user.setNickname(nickname != null ? nickname : "外部用户");
            user.setAvatarUrl(avatarUrl != null ? avatarUrl : "");
            user.setBalance(BigDecimal.ZERO);
            user.setQuota(0);
            user.setCreateTime(LocalDateTime.now());

            // 继承商户等级，确保外部用户按商户费率结算收益
            try {
                com.juxin.orin.entity.ApiMerchant merchant = apiMerchantService.getById(merchantId);
                if (merchant != null && merchant.getLevel() != null) {
                    user.setLevel(merchant.getLevel());
                }
            } catch (Exception e) {
                // 查询商户失败不影响用户创建
            }

            this.save(user);
        } else if (nickname != null || avatarUrl != null) {
            // 更新资料
            if (user.getOpenid() == null || user.getOpenid().isEmpty()) {
                user.setOpenid(buildExternalOpenid(merchantId, externalUserId));
            }
            if (nickname != null)
                user.setNickname(nickname);
            if (avatarUrl != null)
                user.setAvatarUrl(avatarUrl);
            this.updateById(user);
        }
        return user;
    }

    private String buildExternalOpenid(Long merchantId, String externalUserId) {
        String safeMerchantId = merchantId != null ? merchantId.toString() : "0";
        String safeExternalUserId = externalUserId != null ? externalUserId.trim() : "";
        return "ext_" + safeMerchantId + "_" + safeExternalUserId;
    }

    @Override
    public IPage<AppUser> getRecycleBin(Integer page, Integer size, String keyword) {
        int safePage = page != null && page > 0 ? page : 1;
        int safeSize = size != null && size > 0 ? Math.min(size, 100) : 10;
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return baseMapper.selectRecyclePage(new Page<>(safePage, safeSize), normalizedKeyword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveToRecycleBin(Long userId) {
        Integer activeWithdrawals = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withdraw WHERE user_id = ? AND status IN (0, 1, 4)",
                Integer.class,
                userId);
        if (activeWithdrawals != null && activeWithdrawals > 0) {
            throw new IllegalStateException("该用户存在待审核、待打款或失败待处理的提现，暂不能删除");
        }

        Integer activeOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exchange_order WHERE user_id = ? AND status IN (0, 1, 2)",
                Integer.class,
                userId);
        if (activeOrders != null && activeOrders > 0) {
            throw new IllegalStateException("该用户存在未完成的兑换订单，暂不能删除");
        }
        return baseMapper.moveToRecycleBin(userId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreFromRecycleBin(Long userId) {
        return baseMapper.restoreFromRecycleBin(userId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRecycleBin() {
        List<Long> userIds = baseMapper.selectRecycleUserIds();
        if (userIds.isEmpty()) {
            return 0;
        }

        String placeholders = placeholders(userIds.size());
        Object[] args = userIds.toArray();
        ensureNoBlockingRecords(placeholders, args);

        jdbcTemplate.update("UPDATE device SET user_id = NULL, business_id = NULL, bind_time = NULL "
                + "WHERE user_id IN (" + placeholders + ")", args);
        jdbcTemplate.update("UPDATE app_user SET inviter_id = NULL WHERE inviter_id IN (" + placeholders + ")", args);
        jdbcTemplate.update("UPDATE api_merchant SET bind_user_id = NULL WHERE bind_user_id IN (" + placeholders + ")",
                args);
        jdbcTemplate.update("UPDATE exchange_order SET inviter_id = NULL "
                + "WHERE inviter_id IN (" + placeholders + ")", args);

        jdbcTemplate.update("DELETE FROM exchange_logistics WHERE order_id IN "
                + "(SELECT id FROM exchange_order WHERE user_id IN (" + placeholders + "))", args);
        deleteByUserId("exchange_order", placeholders, args);
        deleteByUserId("user_address", placeholders, args);
        deleteByUserId("user_contract", placeholders, args);
        deleteByUserId("user_feedback", placeholders, args);
        deleteByUserId("user_payment_apply", placeholders, args);
        deleteByUserId("withdraw", placeholders, args);
        deleteByUserId("device_earnings", placeholders, args);
        jdbcTemplate.update("DELETE FROM invite_reward WHERE inviter_id IN (" + placeholders + ")", args);
        jdbcTemplate.update("DELETE FROM invite_reward WHERE invitee_id IN (" + placeholders + ")", args);

        return baseMapper.permanentlyDelete(userIds);
    }

    private void ensureNoBlockingRecords(String placeholders, Object[] args) {
        Integer activeWithdrawals = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withdraw WHERE user_id IN (" + placeholders + ") AND status IN (0, 1, 4)",
                Integer.class,
                args);
        if (activeWithdrawals != null && activeWithdrawals > 0) {
            throw new IllegalStateException("回收站中仍有用户存在未完成提现，请先处理后再清空");
        }

        Integer activeOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exchange_order WHERE user_id IN (" + placeholders + ") AND status IN (0, 1, 2)",
                Integer.class,
                args);
        if (activeOrders != null && activeOrders > 0) {
            throw new IllegalStateException("回收站中仍有用户存在未完成兑换订单，请先处理后再清空");
        }
    }

    private void deleteByUserId(String table, String placeholders, Object[] args) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id IN (" + placeholders + ")", args);
    }

    private String placeholders(int count) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < count; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
