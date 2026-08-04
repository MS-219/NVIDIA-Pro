package com.juxin.orin.controller;

import cn.hutool.core.util.StrUtil;
import com.juxin.orin.common.Result;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import com.juxin.orin.mapper.UserContractMapper;
import com.juxin.orin.entity.UserPaymentApply;
import com.juxin.orin.mapper.UserPaymentApplyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 佣金保签约控制器
 * 用于用户端发起签约和查询签约状态
 */
@Slf4j
@RestController
@RequestMapping("/api/bosskg/contract")
public class BossKgContractController {

    @Autowired
    private IBossKgService bossKgService;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private UserContractMapper userContractMapper;

    @Autowired
    private UserPaymentApplyMapper applyMapper;

    /**
     * 从请求中获取用户ID
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        return JwtUtil.getUserId(token);
    }

    /**
     * 检查佣金保是否启用
     */
    @GetMapping("/enabled")
    public Result<Map<String, Object>> checkEnabled() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", bossKgService.isEnabled());
        return Result.success(result);
    }

    /**
     * 获取用户签约状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getContractStatus(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }

        Map<String, Object> result = new HashMap<>();

        if (!bossKgService.isEnabled()) {
            result.put("enabled", false);
            result.put("contracted", false);
            return Result.success(result);
        }

        UserContract contract = bossKgService.getUserContract(userId);

        // 仅签约中的记录需要主动同步。未签约用户必须立即返回待签约，
        // 避免首次进入页面时被第三方接口阻塞。
        if (contract != null && contract.getStatus() == UserContract.STATUS_PROCESSING) {
            String n = contract.getRealName();
            String i = contract.getIdCard();
            String m = contract.getMobile();
            if (StrUtil.isAllNotBlank(n, i, m)) {
                try {
                    bossKgService.queryContractStatus(n, i, m);
                    contract = bossKgService.getUserContract(userId);
                } catch (Exception e) {
                    log.warn("同步异常: {}", e.getMessage());
                }
            }
        }

        result.put("enabled", true);
        result.put("contracted", contract != null && contract.getStatus() == UserContract.STATUS_SUCCESS);
        int status = contract != null ? contract.getStatus() : UserContract.STATUS_PENDING;
        result.put("status", status);
        result.put("statusText", getStatusText(status));
        result.put("failReason", contract != null ? contract.getFailReason() : null);

        return Result.success(result);
    }

    /**
     * 发起签约
     */
    @PostMapping("/sign")
    public Result<String> signContract(
            HttpServletRequest request,
            @RequestBody ContractRequest contractRequest) {

        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (!bossKgService.isEnabled()) {
            return Result.error("佣金保服务未启用");
        }

        // 参数校验
        if (contractRequest.getRealName() == null || contractRequest.getRealName().isEmpty()) {
            return Result.error("请填写真实姓名");
        }
        if (contractRequest.getIdCard() == null || contractRequest.getIdCard().isEmpty()) {
            return Result.error("请填写身份证号");
        }
        if (contractRequest.getMobile() == null || contractRequest.getMobile().isEmpty()) {
            return Result.error("请填写手机号");
        }
        if (contractRequest.getCardNo() == null || contractRequest.getCardNo().isEmpty()) {
            return Result.error("请填写收款账号");
        }

        // 获取用户信息
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 更新用户信息
        user.setBankHolderName(contractRequest.getRealName());
        user.setIdCard(contractRequest.getIdCard());
        if (contractRequest.getIdCardFront() != null) {
            user.setIdCardFront(contractRequest.getIdCardFront());
        }
        if (contractRequest.getIdCardBack() != null) {
            user.setIdCardBack(contractRequest.getIdCardBack());
        }

        // 根据签约方式保存账号
        Integer paymentType = contractRequest.getPaymentType() != null ? contractRequest.getPaymentType() : 0;
        if (paymentType == 0) {
            user.setBankCardNo(contractRequest.getCardNo());
        } else if (paymentType == 1) {
            user.setAlipayAccount(contractRequest.getCardNo());
        }
        appUserMapper.updateById(user);

        // 发起签约
        String error = bossKgService.signContract(
                userId,
                contractRequest.getRealName(),
                contractRequest.getIdCard(),
                contractRequest.getMobile(),
                contractRequest.getCardNo(),
                paymentType,
                contractRequest.getIdCardFront(),
                contractRequest.getIdCardBack());

        if (error != null) {
            return Result.error(error);
        }

        return Result.success("签约请求已提交，请等待审核");
    }

    /**
     * 获取H5签约链接
     */
    @PostMapping("/signing-url")
    public Result<String> getSigningUrl(
            HttpServletRequest request,
            @RequestBody ContractRequest contractRequest) {

        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (!bossKgService.isEnabled()) {
            return Result.error("佣金保服务未启用");
        }

        // 参数校验 (四要素)
        if (StrUtil.isBlank(contractRequest.getRealName()))
            return Result.error("真实姓名为空");
        if (StrUtil.isBlank(contractRequest.getIdCard()))
            return Result.error("身份证号为空");
        if (StrUtil.isBlank(contractRequest.getMobile()))
            return Result.error("手机号为空");
        if (StrUtil.isBlank(contractRequest.getCardNo()))
            return Result.error("收款账号为空");
        if (StrUtil.isBlank(contractRequest.getIdCardFront()))
            return Result.error("请上传身份证人像面");
        if (StrUtil.isBlank(contractRequest.getIdCardBack()))
            return Result.error("请上传身份证国徽面");

        // 更新用户信息
        AppUser user = appUserMapper.selectById(userId);
        if (user != null) {
            user.setBankHolderName(contractRequest.getRealName());
            user.setIdCard(contractRequest.getIdCard());
            user.setIdCardFront(contractRequest.getIdCardFront());
            user.setIdCardBack(contractRequest.getIdCardBack());
            Integer pt = contractRequest.getPaymentType() != null ? contractRequest.getPaymentType() : 0;
            if (pt == 0)
                user.setBankCardNo(contractRequest.getCardNo());
            else
                user.setAlipayAccount(contractRequest.getCardNo());
            appUserMapper.updateById(user);
        }

        // 获取H5链接
        try {
            String url = bossKgService.getH5ContractUrl(
                    userId,
                    contractRequest.getRealName(),
                    contractRequest.getIdCard(),
                    contractRequest.getMobile(),
                    contractRequest.getCardNo(),
                    contractRequest.getPaymentType(),
                    contractRequest.getIdCardFront(),
                    contractRequest.getIdCardBack());

            if (url == null) {
                return Result.error("获取签约链接失败");
            }

            return Result.success(url);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains(":")) {
                // 提取冒号后面的用户友好消息
                String userMsg = msg.substring(msg.indexOf(":") + 1);
                return Result.error(userMsg);
            }
            return Result.error("获取签约链接失败");
        }
    }

    /**
     * 刷新签约状态 (主动查询佣金保平台)
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshContractStatus(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (!bossKgService.isEnabled()) {
            return Result.error("佣金保服务未启用");
        }

        // 获取本地签约记录
        UserContract contract = bossKgService.getUserContract(userId);
        if (contract == null) {
            return Result.error("未找到签约记录");
        }

        // 查询佣金保平台
        Map<String, Object> queryResult = bossKgService.queryContractStatus(
                contract.getRealName(),
                contract.getIdCard(),
                contract.getMobile());

        return Result.success(queryResult);
    }

    /**
     * 解约
     */
    @PostMapping("/cancel")
    public Result<Map<String, Object>> cancelContract(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (!bossKgService.isEnabled()) {
            return Result.error("佣金保服务未启用");
        }

        // 获取本地签约记录
        UserContract contract = bossKgService.getUserContract(userId);
        if (contract == null) {
            return Result.error("未找到签约记录，无法解约");
        }

        if (contract.getStatus() == UserContract.STATUS_CANCELLED) {
            return Result.error("已经是解约状态，无需重复操作");
        }

        if (contract.getStatus() != UserContract.STATUS_SUCCESS) {
            return Result.error("当前状态不可解约，仅已签约状态可发起解约");
        }

        // 获取姓名和身份证号（签约记录里可能为空，从用户信息表兜底）
        String realName = contract.getRealName();
        String idCard = contract.getIdCard();

        if (StrUtil.isBlank(realName) || StrUtil.isBlank(idCard)) {
            AppUser user = appUserMapper.selectById(userId);
            if (user != null) {
                if (StrUtil.isBlank(realName)) {
                    realName = user.getBankHolderName();
                }
                if (StrUtil.isBlank(idCard)) {
                    idCard = user.getIdCard();
                }
            }
        }

        if (StrUtil.isBlank(realName)) {
            return Result.error("无法获取实名姓名，请联系客服");
        }
        if (StrUtil.isBlank(idCard)) {
            return Result.error("无法获取身份证号，请联系客服");
        }

        // 调用佣金保解约接口
        Map<String, Object> result = bossKgService.cancelContract(realName, idCard);

        return Result.success(result);
    }

    /**
     * 获取状态文字描述
     */
    private String getStatusText(Integer status) {
        if (status == null) {
            return "未签约";
        }
        switch (status) {
            case UserContract.STATUS_PENDING:
                return "待签约";
            case UserContract.STATUS_SUCCESS:
                return "已签约";
            case UserContract.STATUS_FAILED:
                return "签约失败";
            case UserContract.STATUS_PROCESSING:
                return "签约中";
            case UserContract.STATUS_CANCELLED:
                return "已解约";
            default:
                return "未知状态";
        }
    }

    /**
     * 签约请求参数
     */
    @lombok.Data
    public static class ContractRequest {
        private String realName; // 真实姓名
        private String idCard; // 身份证号
        private String mobile; // 手机号
        private String cardNo; // 收款账号(银行卡/支付宝)
        private Integer paymentType; // 0-银行卡 1-支付宝
        private String idCardFront; // 身份证人像面照片
        private String idCardBack; // 身份证国徽面照片
    }

    /**
     * 修改卡号请求参数
     */
    @lombok.Data
    public static class UpdateCardRequest {
        private String cardNo; // 新的收款账号(银行卡/支付宝)
        private Integer paymentType; // 0-银行卡 1-支付宝
    }

    @GetMapping("/pending-apply")
    public Result<UserPaymentApply> getPendingApply(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }
        UserPaymentApply apply = applyMapper.selectOne(new LambdaQueryWrapper<UserPaymentApply>()
                .eq(UserPaymentApply::getUserId, userId)
                .eq(UserPaymentApply::getStatus, 0)
                .orderByDesc(UserPaymentApply::getCreateTime)
                .last("LIMIT 1"));
        return Result.success(apply); // 如果没有则返回null
    }

    /**
     * 申请修改收款卡号
     * 提交申请记录至后台审核，不直接修改用户信息
     */
    @PostMapping("/update-card")
    public Result<String> updateCardNo(
            HttpServletRequest request,
            @RequestBody UpdateCardRequest updateRequest) {

        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("请先登录");
        }

        if (!bossKgService.isEnabled()) {
            return Result.error("佣金保服务未启用");
        }

        // 检查用户是否有签约记录（无论签约状态，都允许修改卡号）
        UserContract contract = bossKgService.getUserContract(userId);
        if (contract == null) {
            return Result.error("请先提交签约申请后再修改卡号");
        }

        // 参数校验
        if (StrUtil.isBlank(updateRequest.getCardNo())) {
            return Result.error("请填写新的收款账号");
        }

        Integer paymentType = updateRequest.getPaymentType() != null ? updateRequest.getPaymentType() : 0;

        Long count = applyMapper.selectCount(new LambdaQueryWrapper<UserPaymentApply>()
                .eq(UserPaymentApply::getUserId, userId)
                .eq(UserPaymentApply::getStatus, 0));
        if (count > 0) {
            return Result.error("您已有一条正在审核中的变更申请，请耐心等待");
        }

        // 插入申请记录
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        UserPaymentApply apply = new UserPaymentApply();
        apply.setUserId(userId);
        apply.setPaymentType(paymentType);
        apply.setNewCardNo(updateRequest.getCardNo());
        apply.setStatus(0);
        
        // 记录旧信息快照
        Map<String, String> oldInfo = new HashMap<>();
        oldInfo.put("bankCardNo", user.getBankCardNo());
        oldInfo.put("alipayAccount", user.getAlipayAccount());
        apply.setOldInfo(cn.hutool.json.JSONUtil.toJsonStr(oldInfo));
        
        applyMapper.insert(apply);

        log.info("用户{}申请修改卡号，新卡号末四位：{}，等待审核", userId,
                updateRequest.getCardNo().length() > 4
                        ? updateRequest.getCardNo().substring(updateRequest.getCardNo().length() - 4)
                        : updateRequest.getCardNo());

        return Result.success("修改申请已提交，请等待后台审核");
    }
}
