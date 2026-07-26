package com.juxin.orin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.juxin.orin.common.Result;
import com.juxin.orin.config.BossKgConfig;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.mapper.DeviceEarningsMapper;
import com.juxin.orin.mapper.InviteRewardMapper;
import com.juxin.orin.mapper.UserContractMapper;
import com.juxin.orin.service.IAppUserService;
import com.juxin.orin.service.IDeviceService;
import com.juxin.orin.service.IAiTaskService;
import com.juxin.orin.service.IWechatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 小程序用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class AppUserController {

    @Autowired
    private IAppUserService appUserService;

    @Autowired
    private IWechatService wechatService;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IAiTaskService aiTaskService;

    @Autowired
    private DeviceEarningsMapper earningsMapper;

    @Autowired
    private InviteRewardMapper inviteRewardMapper;

    @Autowired
    private com.juxin.orin.service.IInviteService inviteService;

    @Autowired
    private com.juxin.orin.service.ISystemConfigService configService;

    @Autowired
    private com.juxin.orin.service.IBossKgService bossKgService;

    @Autowired
    private UserContractMapper userContractMapper;

    @Autowired
    private BossKgConfig bossKgConfig;

    @Autowired
    private com.juxin.orin.service.IWithdrawService withdrawService;

    /**
     * 微信一键登录
     * 请求参数:
     * - code: 小程序 wx.login() 获取的 code (必填)
     * - nickname: 用户昵称 (可选)
     * - avatarUrl: 用户头像 (可选)
     * - inviteCode: 邀请码 (可选，新用户注册时使用)
     */
    @PostMapping("/wxLogin")
    public Result<Object> wxLogin(@RequestBody Map<String, String> params) {
        String code = params.get("code");
        String nickname = params.get("nickname");
        String avatarUrl = params.get("avatarUrl");
        String inviteCode = params.get("inviteCode");

        // code 必填
        if (code == null || code.isEmpty()) {
            return Result.error("code 不能为空");
        }

        // 调用微信接口换取 openid
        String openid = wechatService.code2Session(code);

        if (openid == null || openid.isEmpty()) {
            return Result.error("微信登录失败，请重试");
        }

        // 检查是否是新用户
        AppUser existingUser = appUserService.getByOpenid(openid);
        boolean isNewUser = (existingUser == null);

        // 登录或注册用户
        String token;
        try {
            token = appUserService.wxLogin(openid);
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
        AppUser user = appUserService.getByOpenid(openid);

        // 如果是新用户且有邀请码，处理邀请关系
        if (isNewUser && inviteCode != null && !inviteCode.isEmpty()) {
            try {
                inviteService.handleNewUserInvite(user.getId(), inviteCode);
            } catch (Exception e) {
                // 邀请关系绑定失败不影响登录
                org.slf4j.LoggerFactory.getLogger(AppUserController.class)
                        .warn("邀请关系绑定失败: userId={}, inviteCode={}, error={}",
                                user.getId(), inviteCode, e.getMessage());
            }
        }

        // 如果传了头像和昵称，更新用户信息
        if ((nickname != null && !nickname.isEmpty()) ||
                (avatarUrl != null && !avatarUrl.isEmpty())) {
            boolean needUpdate = false;

            if (nickname != null && !nickname.isEmpty() &&
                    (user.getNickname() == null || user.getNickname().equals("微信用户"))) {
                user.setNickname(nickname);
                needUpdate = true;
            }

            if (avatarUrl != null && !avatarUrl.isEmpty() &&
                    (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty())) {
                user.setAvatarUrl(avatarUrl);
                needUpdate = true;
            }

            if (needUpdate) {
                appUserService.updateById(user);
            }
        }

        return Result.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "isNewUser", isNewUser,
                "nickname", user.getNickname() != null ? user.getNickname() : "",
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "level", user.getLevel() != null ? user.getLevel() : 0));
    }

    /**
     * 更新用户头像和昵称
     * 安全修复：需要JWT Token验证，用户只能更新自己的资料
     */
    @PostMapping("/updateProfile")
    public Result<String> updateProfile(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        Long tokenUserId = com.juxin.orin.util.JwtUtil.getUserId(token);
        if (tokenUserId == null) {
            return Result.error("无效的Token");
        }

        Object userIdObj = params.get("userId");
        if (userIdObj == null) {
            return Result.error("用户ID不能为空");
        }
        Long userId = Long.valueOf(userIdObj.toString());

        // 验证用户只能更新自己的资料
        if (!tokenUserId.equals(userId)) {
            return Result.error("无权修改其他用户资料");
        }

        String nickname = (String) params.get("nickname");
        String avatarUrl = (String) params.get("avatarUrl");

        // 使用 LambdaUpdate 仅更新传入的字段，防止覆盖其他属性（如手机号、余额等）
        com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper<AppUser> updateWrapper = appUserService
                .lambdaUpdate().eq(AppUser::getId, userId);

        boolean hasUpdate = false;
        if (nickname != null && !nickname.trim().isEmpty()) {
            updateWrapper.set(AppUser::getNickname, nickname.trim());
            hasUpdate = true;
        }
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            updateWrapper.set(AppUser::getAvatarUrl, avatarUrl.trim());
            hasUpdate = true;
        }

        if (!hasUpdate) {
            return Result.success("无需更新");
        }

        boolean success = updateWrapper.update();
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info/{id}")
    public Result<AppUser> getInfo(@PathVariable Long id) {
        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 强制根据余额同步聚芯算力值并保存到数据库
        if (user.getBalance() != null) {
            try {
                int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));
                int calculatedQuota = user.getBalance().multiply(new java.math.BigDecimal(hashrateRate)).intValue();

                // 如果数据库中的值与计算值不一致，则更新数据库
                if (user.getQuota() == null || user.getQuota() != calculatedQuota) {
                    user.setQuota(calculatedQuota);
                    appUserService.updateById(user);
                    System.out.println(
                            "Syncing quota for user " + id + ": " + user.getQuota() + " -> " + calculatedQuota);
                }
            } catch (Exception e) {
                // 降级处理
                if (user.getQuota() == null)
                    user.setQuota(0);
            }
        }
        return Result.success(user);
    }

    /**
     * 更新用户信息（管理员专用接口）
     * 安全修复：需要管理员权限
     */
    @PostMapping("/update")
    public Result<String> update(
            @RequestBody AppUser user,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可用
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限执行此操作");
        }

        if (user.getId() == null) {
            return Result.error("用户ID不能为空");
        }

        if (user.getUserType() != null
                && !"personal".equals(user.getUserType())
                && !"company".equals(user.getUserType())) {
            return Result.error("用户类型不正确");
        }
        if (user.getRemark() != null && user.getRemark().length() > 500) {
            return Result.error("备注最多500个字符");
        }

        // 校验邀请人ID合法性
        if (user.getInviterId() != null) {
            if (user.getInviterId().equals(user.getId())) {
                return Result.error("邀请人不能是用户自己");
            }
            AppUser inviter = appUserService.getById(user.getInviterId());
            if (inviter == null) {
                return Result.error("指定的邀请人ID不存在");
            }
        }

        boolean success = appUserService.updateById(user);
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 更新用户资产（管理员专用接口）
     */
    @PostMapping("/updateAsset")
    public Result<String> updateAsset(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限执行此操作");
        }

        Object userIdObj = params.get("userId");
        Object balanceObj = params.get("balance");
        if (userIdObj == null) {
            return Result.error("用户ID不能为空");
        }
        if (balanceObj == null) {
            return Result.error("账户余额不能为空");
        }

        Long userId = Long.valueOf(userIdObj.toString());
        BigDecimal balance;
        try {
            balance = new BigDecimal(balanceObj.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return Result.error("账户余额格式不正确");
        }
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("账户余额不能小于0");
        }
        if (balance.compareTo(new BigDecimal("999999")) > 0) {
            return Result.error("账户余额不能超过999999");
        }

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));
        int quota = balance.multiply(new BigDecimal(hashrateRate)).intValue();

        boolean success = appUserService.lambdaUpdate()
                .eq(AppUser::getId, userId)
                .set(AppUser::getBalance, balance)
                .set(AppUser::getQuota, quota)
                .update();
        return success ? Result.success("资产更新成功") : Result.error("资产更新失败");
    }

    /**
     * 手动设置用户等级（后台管理员操作，优先级高于自动升级）
     */
    @PostMapping("/updateLevel")
    public Result<String> updateLevel(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Integer level = Integer.valueOf(params.get("level").toString());
        // 允许外部传入 levelManual，默认 true (为了兼容旧逻辑)
        Boolean levelManual = params.get("levelManual") != null ? (Boolean) params.get("levelManual") : true;

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (level < 0 || level > 5) {
            return Result.error("等级必须在 0-5 之间");
        }

        user.setLevel(level);
        user.setLevelManual(levelManual); // 设置手动标记

        boolean success = appUserService.updateById(user);

        // 如果解除锁定，立即同步该用户的等级
        if (success) {
            try {
                appUserService.updateLevel(userId);
            } catch (Exception e) {
                // 防止等级重算失败影响保存响应
            }
        }

        return success ? Result.success("等级设置成功，系统已自动同步晋升状态") : Result.error("设置失败");
    }

    /**
     * 设置用户是否禁止提现（管理员专用）
     */
    @PostMapping("/toggleWithdraw")
    public Result<String> toggleWithdraw(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可用
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限执行此操作");
        }

        Long userId = Long.valueOf(params.get("userId").toString());
        Boolean disabled = Boolean.valueOf(params.get("disabled").toString());

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        user.setWithdrawDisabled(disabled);
        boolean success = appUserService.updateById(user);

        return success ? Result.success(disabled ? "已禁止该用户提现" : "已恢复该用户提现权限") : Result.error("操作失败");
    }

    /**
     * 管理员迁移同一实名的提现签约主体账号
     */
    @PostMapping("/transfer-contract")
    @Transactional
    public Result<String> transferContract(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String authError = validateAdminToken(token);
        if (authError != null) {
            return Result.error(authError);
        }

        Long sourceUserId = getLongParam(params, "sourceUserId");
        Long targetUserId = getLongParam(params, "targetUserId");
        if (sourceUserId == null || targetUserId == null) {
            return Result.error("来源账号和目标账号不能为空");
        }
        if (sourceUserId.equals(targetUserId)) {
            return Result.error("来源账号和目标账号不能相同");
        }

        AppUser sourceUser = appUserService.getById(sourceUserId);
        AppUser targetUser = appUserService.getById(targetUserId);
        if (sourceUser == null) {
            return Result.error("来源账号不存在");
        }
        if (targetUser == null) {
            return Result.error("目标账号不存在");
        }

        long processingWithdrawCount = withdrawService.lambdaQuery()
                .eq(com.juxin.orin.entity.Withdraw::getUserId, sourceUserId)
                .in(com.juxin.orin.entity.Withdraw::getStatus, java.util.Arrays.asList(0, 1, 4))
                .count();
        if (processingWithdrawCount > 0) {
            return Result.error("来源账号存在待处理或失败可重试的提现记录，请先处理后再迁移");
        }

        UserContract sourceContract = bossKgService.getUserContract(sourceUserId);
        UserContract targetContract = bossKgService.getUserContract(targetUserId);

        String idCard = firstNotBlank(
                sourceContract != null ? sourceContract.getIdCard() : null,
                sourceUser.getIdCard(),
                targetContract != null ? targetContract.getIdCard() : null,
                targetUser.getIdCard());
        String realName = firstNotBlank(
                sourceContract != null ? sourceContract.getRealName() : null,
                sourceUser.getBankHolderName(),
                targetContract != null ? targetContract.getRealName() : null,
                targetUser.getBankHolderName());

        if (isBlank(idCard) || isBlank(realName)) {
            return Result.error("来源账号缺少实名或身份证信息，无法迁移");
        }

        String identityError = validateSameIdentity(idCard, realName, sourceUser, targetUser, sourceContract,
                targetContract);
        if (identityError != null) {
            return Result.error(identityError);
        }

        Integer paymentType = targetContract != null && targetContract.getPaymentType() != null
                ? targetContract.getPaymentType()
                : sourceContract != null && sourceContract.getPaymentType() != null
                        ? sourceContract.getPaymentType()
                        : UserContract.PAY_TYPE_BANK;
        String mobile = firstNotBlank(
                targetContract != null ? targetContract.getMobile() : null,
                targetUser.getPhone(),
                sourceContract != null ? sourceContract.getMobile() : null,
                sourceUser.getPhone());
        String cardNo = paymentType == UserContract.PAY_TYPE_ALIPAY
                ? firstNotBlank(
                        targetContract != null ? targetContract.getAlipayAccount() : null,
                        targetUser.getAlipayAccount(),
                        sourceContract != null ? sourceContract.getAlipayAccount() : null,
                        sourceUser.getAlipayAccount())
                : firstNotBlank(
                        targetContract != null ? targetContract.getBankCardNo() : null,
                        targetUser.getBankCardNo(),
                        sourceContract != null ? sourceContract.getBankCardNo() : null,
                        sourceUser.getBankCardNo());
        if (isBlank(mobile)) {
            return Result.error("缺少签约手机号，无法迁移");
        }
        if (isBlank(cardNo)) {
            return Result.error("缺少签约收款账号，无法迁移");
        }

        java.util.List<UserContract> sameContracts = userContractMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserContract>()
                        .eq(UserContract::getIdCard, idCard)
                        .eq(UserContract::getProviderId, bossKgConfig.getProviderId()));

        LocalDateTime now = LocalDateTime.now();
        for (UserContract contract : sameContracts) {
            if (!targetUserId.equals(contract.getUserId())) {
                contract.setStatus(UserContract.STATUS_CANCELLED);
                contract.setUpdateTime(now);
                userContractMapper.updateById(contract);
            }
        }

        UserContract nextContract = targetContract;
        if (nextContract == null) {
            nextContract = new UserContract();
            nextContract.setUserId(targetUserId);
            nextContract.setProviderId(bossKgConfig.getProviderId());
            nextContract.setCreateTime(now);
        }
        nextContract.setRealName(realName.trim());
        nextContract.setIdCard(idCard.trim());
        nextContract.setMobile(mobile.trim());
        nextContract.setPaymentType(paymentType);
        nextContract.setStatus(UserContract.STATUS_SUCCESS);
        nextContract.setFailReason(null);
        nextContract.setContractTime(now);
        nextContract.setUpdateTime(now);
        if (paymentType == UserContract.PAY_TYPE_ALIPAY) {
            nextContract.setAlipayAccount(cardNo.trim());
            nextContract.setBankCardNo(null);
        } else {
            nextContract.setBankCardNo(cardNo.trim());
            nextContract.setAlipayAccount(null);
        }
        if (nextContract.getId() == null) {
            userContractMapper.insert(nextContract);
        } else {
            userContractMapper.updateById(nextContract);
        }

        sourceUser.setWithdrawDisabled(true);
        targetUser.setWithdrawDisabled(false);
        targetUser.setBankHolderName(realName.trim());
        targetUser.setIdCard(idCard.trim());
        if (!isBlank(mobile)) {
            targetUser.setPhone(mobile.trim());
        }
        if (!isBlank(cardNo)) {
            if (paymentType == UserContract.PAY_TYPE_ALIPAY) {
                targetUser.setAlipayAccount(cardNo.trim());
                targetUser.setBankCardNo(null);
            } else {
                targetUser.setBankCardNo(cardNo.trim());
                targetUser.setAlipayAccount(null);
            }
        }
        appUserService.updateById(sourceUser);
        appUserService.updateById(targetUser);
        appUserService.lambdaUpdate()
                .eq(AppUser::getId, targetUserId)
                .set(paymentType == UserContract.PAY_TYPE_ALIPAY, AppUser::getBankCardNo, null)
                .set(paymentType != UserContract.PAY_TYPE_ALIPAY, AppUser::getAlipayAccount, null)
                .update();

        return Result.success("实名提现账号已迁移到用户ID " + targetUserId + "，来源账号已禁提");
    }

    /**
     * 更新用户的邀请人
     */
    @PostMapping("/updateInviter")
    public Result<String> updateInviter(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        // 允许 inviterId 为 null 或 0 (表示清除邀请人)
        Long inviterId = null;
        if (params.get("inviterId") != null && !params.get("inviterId").toString().isEmpty()) {
            inviterId = Long.valueOf(params.get("inviterId").toString());
            if (inviterId == 0) {
                inviterId = null;
            }
        }

        if (userId.equals(inviterId)) {
            return Result.error("邀请人不能是用户自己");
        }

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (inviterId != null) {
            AppUser inviter = appUserService.getById(inviterId);
            if (inviter == null) {
                return Result.error("指定的邀请人不存在");
            }
        }

        user.setInviterId(inviterId);
        boolean success = appUserService.updateById(user);

        return success ? Result.success("邀请人更新成功") : Result.error("更新失败");
    }

    /**
     * 绑定手机号
     * 通过微信 getPhoneNumber 获取的 code 换取手机号
     */
    @PostMapping("/bindPhone")
    public Result<Object> bindPhone(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String code = (String) params.get("code");

        if (code == null || code.isEmpty()) {
            return Result.error("code 不能为空");
        }

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 调用微信接口获取手机号
        String phone = wechatService.getPhoneNumber(code);
        if (phone == null || phone.isEmpty()) {
            return Result.error("获取手机号失败");
        }

        user.setPhone(phone);
        boolean success = appUserService.updateById(user);

        if (success) {
            return Result.success(Map.of("phone", phone));
        } else {
            return Result.error("绑定失败");
        }
    }

    /**
     * 获取用户列表（分页）- 包含设备数、任务数和邀请人信息（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/list")
    public Result<Object> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, name = "userType") String requestedUserType,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可访问
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限访问此接口");
        }

        Page<AppUser> pageParam = new Page<>(page, size);

        // 构建查询条件
        com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<AppUser> queryWrapper = appUserService
                .lambdaQuery();

        // 开放平台同步过来的外部用户不进入平台用户管理视图
        queryWrapper.isNull(AppUser::getMerchantId);

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(q -> q
                    .like(AppUser::getNickname, keyword)
                    .or()
                    .like(AppUser::getOpenid, keyword)
                    .or()
                    .like(AppUser::getPhone, keyword)
                    .or()
                    .like(AppUser::getId, keyword));
        }

        // 筛选条件
        if (filter != null && !filter.isEmpty()) {
            if ("hasDevice".equals(filter)) {
                queryWrapper.exists("SELECT 1 FROM device WHERE device.user_id = app_user.id");
            } else if ("hasBalance".equals(filter)) {
                queryWrapper.gt(AppUser::getBalance, 0);
            }
        }

        if (requestedUserType != null && !requestedUserType.isEmpty()) {
            if (!"personal".equals(requestedUserType) && !"company".equals(requestedUserType)) {
                return Result.error("用户类型不正确");
            }
            queryWrapper.eq(AppUser::getUserType, requestedUserType);
        }

        // 按注册时间倒序
        queryWrapper.orderByDesc(AppUser::getCreateTime);

        Page<AppUser> result = queryWrapper.page(pageParam);

        // 获取算力兑换比例配置
        int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));

        // 填充额外信息
        for (AppUser user : result.getRecords()) {
            // 强制根据余额同步聚芯算力值显示
            if (user.getBalance() != null) {
                user.setQuota(user.getBalance().multiply(new java.math.BigDecimal(hashrateRate)).intValue());
            }

            user.setDeviceCount(
                    deviceService.lambdaQuery().eq(com.juxin.orin.entity.Device::getUserId, user.getId()).count().intValue());
            user.setTaskCount(
                    aiTaskService.lambdaQuery().eq(com.juxin.orin.entity.AiTask::getUserId, user.getId()).count().intValue());

            // 填充邀请人昵称和头像
            if (user.getInviterId() != null) {
                AppUser inviter = appUserService.getById(user.getInviterId());
                if (inviter != null) {
                    user.setInviterNickname(inviter.getNickname());
                    user.setInviterAvatarUrl(inviter.getAvatarUrl());
                }
            }

            // 填充签约状态
            com.juxin.orin.entity.UserContract contract = bossKgService.getUserContract(user.getId());
            if (contract != null) {
                user.setContractStatus(contract.getStatus());
                user.setContractStatusText(getStatusText(contract.getStatus()));
            } else {
                user.setContractStatus(0);
                user.setContractStatusText("待签约");
            }
        }

        return Result.success(result);
    }

    /** 将平台用户移入回收站。 */
    @PostMapping("/delete/{id}")
    public Result<String> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        AppUser user = appUserService.getById(id);
        if (user == null || user.getMerchantId() != null) {
            return Result.error("用户不存在或不可删除");
        }
        try {
            boolean success = appUserService.moveToRecycleBin(id);
            return success ? Result.success("用户已移入回收站") : Result.error("删除失败");
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 获取回收站用户，按删除时间倒序。 */
    @GetMapping("/recycle-bin")
    public Result<Object> recycleBin(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }

        com.baomidou.mybatisplus.core.metadata.IPage<AppUser> result = appUserService.getRecycleBin(page, size, keyword);
        for (AppUser user : result.getRecords()) {
            user.setDeviceCount(deviceService.lambdaQuery()
                    .eq(com.juxin.orin.entity.Device::getUserId, user.getId())
                    .count()
                    .intValue());
        }
        return Result.success(result);
    }

    /** 恢复回收站中的用户。 */
    @PostMapping("/restore/{id}")
    public Result<String> restoreUser(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }
        boolean success = appUserService.restoreFromRecycleBin(id);
        return success ? Result.success("用户已恢复") : Result.error("用户不存在或已恢复");
    }

    /** 永久清空回收站。 */
    @DeleteMapping("/recycle-bin")
    public Result<String> clearRecycleBin(
            @RequestHeader(value = "Authorization", required = false) String token) {
        String error = validateAdminToken(token);
        if (error != null) {
            return Result.error(error);
        }
        try {
            int deletedCount = appUserService.clearRecycleBin();
            return Result.success("已永久删除 " + deletedCount + " 位用户");
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取用户总数（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/count")
    public Result<Long> count(
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限访问此接口");
        }

        return Result.success(appUserService.lambdaQuery().isNull(AppUser::getMerchantId).count());
    }

    /**
     * 获取用户统计数据（管理员专用）
     * 安全修复：需要管理员权限
     */
    @GetMapping("/stats")
    public Result<Object> stats(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            if (token == null || token.isEmpty()) {
                return Result.error("未登录，请先登录");
            }
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
                return Result.error("登录已过期，请重新登录");
            }
            String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
            if (!"admin".equals(userType)) {
                return Result.error("无权限访问此接口");
            }

            java.util.List<AppUser> platformUsers = appUserService.lambdaQuery()
                    .isNull(AppUser::getMerchantId)
                    .list();
            long totalUsers = platformUsers.size();

            java.util.Set<Long> platformUserIds = platformUsers.stream()
                    .map(AppUser::getId)
                    .collect(java.util.stream.Collectors.toSet());

            long hasDeviceCount = platformUserIds.isEmpty()
                    ? 0
                    : deviceService.lambdaQuery()
                            .in(com.juxin.orin.entity.Device::getUserId, platformUserIds)
                            .list()
                            .stream()
                            .map(com.juxin.orin.entity.Device::getUserId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .count();

            // 获取算力兑换比例配置
            int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));

            // 计算总余额和总配额
            java.math.BigDecimal totalBalance = java.math.BigDecimal.ZERO;
            int totalQuota = 0;
            for (AppUser u : platformUsers) {
                if (u.getBalance() != null) {
                    totalBalance = totalBalance.add(u.getBalance());
                    // 统计显示的算力总额也基于余额动态计算
                    totalQuota += u.getBalance().multiply(new java.math.BigDecimal(hashrateRate)).intValue();
                }
            }

            return Result.success(java.util.Map.of(
                    "totalUsers", totalUsers,
                    "hasDeviceCount", hasDeviceCount,
                    "totalBalance", totalBalance.toString(),
                    "totalQuota", totalQuota));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("调试报错信息: " + e.getMessage());
        }
    }

    /**
     * 获取用户详情（包含设备列表和收益信息）
     * 安全修复：需要JWT Token验证，普通用户只能查看自己的信息
     */
    @GetMapping("/detail/{id}")
    public Result<Object> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：必须提供有效Token
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }

        // 去除 Bearer 前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 验证Token有效性
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }

        Long tokenUserId = com.juxin.orin.util.JwtUtil.getUserId(token);
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);

        if (tokenUserId == null) {
            return Result.error("无效的Token");
        }

        // 权限验证：普通用户只能查看自己的信息，管理员可以查看任何用户
        if (!"admin".equals(userType) && !tokenUserId.equals(id)) {
            return Result.error("无权访问其他用户信息");
        }

        AppUser user = appUserService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 获取用户的设备列表
        java.util.List<com.juxin.orin.entity.Device> devices = deviceService.lambdaQuery()
                .eq(com.juxin.orin.entity.Device::getUserId, id)
                .list();

        // 当前设备列表只展示这些设备归属于该用户期间产生的收益，避免设备换绑后串入旧用户收益。
        java.util.List<java.util.Map<String, Object>> deviceList = new java.util.ArrayList<>();

        for (com.juxin.orin.entity.Device device : devices) {
            java.util.Map<String, Object> deviceInfo = new java.util.HashMap<>();
            deviceInfo.put("id", device.getId());
            deviceInfo.put("sn", device.getSn());
            deviceInfo.put("name", device.getName());
            deviceInfo.put("status", device.getStatus());
            deviceInfo.put("location", device.getLocation());
            deviceInfo.put("lastHeartbeatTime", device.getLastHeartbeatTime());
            deviceInfo.put("bindTime", device.getBindTime());

            java.math.BigDecimal deviceEarnings = earningsMapper.sumByDeviceAndUser(device.getId(), id);
            deviceInfo.put("earnings", deviceEarnings != null ? deviceEarnings : java.math.BigDecimal.ZERO);

            deviceList.add(deviceInfo);
        }

        // 累计收益按收益记录归属统计，不依赖设备当前是否仍绑定，并包含收益型邀请分润。
        java.math.BigDecimal deviceEarnings = earningsMapper.sumByUser(id);
        if (deviceEarnings == null) {
            deviceEarnings = java.math.BigDecimal.ZERO;
        }
        java.math.BigDecimal rewardEarnings = inviteRewardMapper.sumEarningsRewardByInviter(id);
        if (rewardEarnings == null) {
            rewardEarnings = java.math.BigDecimal.ZERO;
        }
        java.math.BigDecimal totalEarnings = deviceEarnings.add(rewardEarnings);

        // 获取创作任务数
        int taskCount = aiTaskService.lambdaQuery().eq(com.juxin.orin.entity.AiTask::getUserId, id).count().intValue();

        // 获取邀请人信息
        String inviterNickname = null;
        String inviterAvatarUrl = null;
        if (user.getInviterId() != null) {
            AppUser inviter = appUserService.getById(user.getInviterId());
            if (inviter != null) {
                inviterNickname = inviter.getNickname();
                inviterAvatarUrl = inviter.getAvatarUrl();
            }
        }

        // 获取算力兑换比例配置
        int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", user.getId());
        result.put("openid", user.getOpenid());
        result.put("nickname", user.getNickname());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("phone", user.getPhone());
        result.put("balance", user.getBalance());
        // 强制根据余额同步聚芯算力值显示, 覆盖数据库中的配额字段
        result.put("quota",
                user.getBalance() != null
                        ? user.getBalance().multiply(new java.math.BigDecimal(hashrateRate)).intValue()
                        : 0);
        result.put("inviterId", user.getInviterId());
        result.put("inviterNickname", inviterNickname);
        result.put("inviterAvatarUrl", inviterAvatarUrl);
        result.put("createTime", user.getCreateTime());
        result.put("deviceCount", devices.size());
        result.put("taskCount", taskCount);
        result.put("devices", deviceList);
        result.put("deviceEarnings", deviceEarnings);
        result.put("rewardEarnings", rewardEarnings);
        result.put("totalEarnings", totalEarnings);

        // 填充签约状态
        com.juxin.orin.entity.UserContract contract = bossKgService.getUserContract(id);
        if (contract != null) {
            result.put("contractStatus", contract.getStatus());
            result.put("contractStatusText", getStatusText(contract.getStatus()));
            result.put("contractRealName", contract.getRealName());
            result.put("contractIdCard", contract.getIdCard());
            result.put("contractMobile", contract.getMobile());
        } else {
            result.put("contractStatus", 0);
            result.put("contractStatusText", "待签约");
        }

        return Result.success(result);
    }

    /**
     * 充值配额
     */
    @PostMapping("/recharge-quota")
    public Result<String> rechargeQuota(@RequestBody java.util.Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        Integer amount = Integer.valueOf(params.get("amount").toString());

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        int currentQuota = user.getQuota() != null ? user.getQuota() : 0;
        user.setQuota(currentQuota + amount);

        // 获取算力兑换比例配置
        int hashrateRate = Integer.parseInt(configService.getConfig("earnings.hashratePerYuan", "100"));

        // 同步增加余额
        java.math.BigDecimal balanceAdd = new java.math.BigDecimal(amount)
                .divide(new java.math.BigDecimal(hashrateRate), 2, java.math.RoundingMode.HALF_UP);
        user.setBalance((user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO).add(balanceAdd));

        boolean success = appUserService.updateById(user);

        return success ? Result.success("充值成功") : Result.error("充值失败");
    }

    /**
     * 手动触发全员等级重算（管理员专用）
     */
    @GetMapping("/refresh-levels")
    public Result<String> refreshLevels(
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 安全验证：仅管理员可用
        if (token == null || token.isEmpty()) {
            return Result.error("未登录，请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return Result.error("登录已过期，请重新登录");
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        if (!"admin".equals(userType)) {
            return Result.error("无权限执行此操作");
        }

        appUserService.updateAllUserLevels();
        return Result.success("全员等级重算已完成");
    }

    /**
     * 管理员强制同步用户的签约状态
     */
    @PostMapping("/syncContract/{userId}")
    public Result<String> syncContract(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String token) {

        // 验证管理员权限
        if (token == null || token.isEmpty())
            return Result.error("未登录");
        if (token.startsWith("Bearer "))
            token = token.substring(7);
        if (!com.juxin.orin.util.JwtUtil.validateToken(token))
            return Result.error("登录已过期");
        if (!"admin".equals(com.juxin.orin.util.JwtUtil.getUserType(token)))
            return Result.error("无权限");

        AppUser user = appUserService.getById(userId);
        if (user == null)
            return Result.error("用户不存在");

        // 尝试获取三要素
        String name = user.getBankHolderName(); // 优先取持卡人姓名
        if (name == null || name.isEmpty())
            name = user.getNickname(); // 兜底昵称(虽然通常不准)
        String idCard = user.getIdCard();
        String mobile = user.getPhone();

        if (idCard == null || idCard.isEmpty() || mobile == null || mobile.isEmpty()) {
            return Result.error("用户身份证号或手机号缺失，无法同步。请联系用户在小程序端完善信息。");
        }

        try {
            bossKgService.queryContractStatus(name, idCard, mobile);
            // 再次获取确认
            com.juxin.orin.entity.UserContract contract = bossKgService.getUserContract(userId);
            if (contract != null) {
                return Result.success("同步成功，当前状态：" + getStatusText(contract.getStatus()));
            } else {
                return Result.error("同步完成但未查询到签约记录(可能姓名不匹配)");
            }
        } catch (Exception e) {
            return Result.error("同步失败: " + e.getMessage());
        }
    }

    private String getStatusText(Integer status) {
        if (status == null)
            return "待签约";
        switch (status) {
            case 1:
                return "已签约";
            case 2:
                return "签约失败";
            case 3:
                return "签约中";
            case 5:
                return "已解约";
            default:
                return "待签约";
        }
    }

    private String validateAdminToken(String token) {
        if (token == null || token.isEmpty()) {
            return "未登录，请先登录";
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!com.juxin.orin.util.JwtUtil.validateToken(token)) {
            return "登录已过期，请重新登录";
        }
        String userType = com.juxin.orin.util.JwtUtil.getUserType(token);
        String role = com.juxin.orin.util.JwtUtil.getRole(token);
        if (!"admin".equals(userType) || "factory".equals(role)) {
            return "无权限执行此操作";
        }
        return null;
    }

    private Long getLongParam(Map<String, Object> params, String key) {
        if (params == null || params.get(key) == null || params.get(key).toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(params.get(key).toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String validateSameIdentity(String idCard, String realName, AppUser sourceUser, AppUser targetUser,
            UserContract sourceContract, UserContract targetContract) {
        String[][] values = {
                { "来源账号身份证", sourceUser.getIdCard(), idCard },
                { "目标账号身份证", targetUser.getIdCard(), idCard },
                { "来源签约身份证", sourceContract != null ? sourceContract.getIdCard() : null, idCard },
                { "目标签约身份证", targetContract != null ? targetContract.getIdCard() : null, idCard },
                { "来源账号实名", sourceUser.getBankHolderName(), realName },
                { "目标账号实名", targetUser.getBankHolderName(), realName },
                { "来源签约实名", sourceContract != null ? sourceContract.getRealName() : null, realName },
                { "目标签约实名", targetContract != null ? targetContract.getRealName() : null, realName }
        };
        for (String[] item : values) {
            if (!isBlank(item[1]) && !item[1].trim().equals(item[2].trim())) {
                return item[0] + "与本次迁移实名不一致，不能迁移";
            }
        }
        return null;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
