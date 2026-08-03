package com.juxin.orin.service;

import com.juxin.orin.entity.UserContract;
import com.juxin.orin.entity.Withdraw;

import java.util.Map;

/**
 * 佣金保服务接口
 */
public interface IBossKgService {

        /**
         * 检查是否启用佣金保
         */
        boolean isEnabled();

        /**
         * 发起签约
         *
         * @param userId      用户ID
         * @param realName    真实姓名
         * @param idCard      身份证号
         * @param mobile      手机号
         * @param cardNo      银行卡号/支付宝账号
         * @param paymentType 签约方式: 0-银行卡 1-支付宝
         * @param idCardFront 身份证人像面照片(Base64或URL)
         * @param idCardBack  身份证国徽面照片(Base64或URL)
         * @return 签约结果, 成功返回null, 失败返回错误信息
         */
        String signContract(Long userId, String realName, String idCard, String mobile,
                        String cardNo, Integer paymentType, String idCardFront, String idCardBack);

        /**
         * 查询签约状态
         *
         * @param realName 真实姓名
         * @param idCard   身份证号
         * @param mobile   手机号
         * @return 签约状态信息
         */
        Map<String, Object> queryContractStatus(String realName, String idCard, String mobile);

        /**
         * 检查用户是否已签约
         *
         * @param userId 用户ID
         * @return 已签约返回true
         */
        boolean isUserContracted(Long userId);

        /**
         * 获取用户签约记录
         *
         * @param userId 用户ID
         * @return 签约记录
         */
        UserContract getUserContract(Long userId);

        /**
         * 发起付款
         *
         * @param withdraw 提现记录
         * @param idCard   身份证号
         * @return 付款结果, 成功返回null, 失败返回错误信息
         */
        String payment(Withdraw withdraw, String idCard);

        /**
         * 查询付款状态
         *
         * @param batchId 商户批次号
         * @param orderId 商户订单号 (可选)
         * @return 付款状态信息
         */
        Map<String, Object> queryPaymentStatus(String batchId, String orderId);

        /**
         * 查询商户账户余额
         *
         * @return 余额(分)
         */
        Long queryBalance();

        /**
         * 处理签约回调
         *
         * @param requestBody 回调请求体
         * @return 处理结果 SUCCESS/FAIL
         */
        String handleContractNotify(String requestBody);

        /**
         * 处理付款回调
         *
         * @param requestBody 回调请求体
         * @return 处理结果 SUCCESS/FAIL
         */
        String handlePaymentNotify(String requestBody);

        /**
         * 获取H5签约链接 (Code 6026)
         *
         * @param userId      用户ID
         * @param realName    真实姓名
         * @param idCard      身份证号
         * @param mobile      手机号
         * @param cardNo      收款账号
         * @param paymentType 签约方式: 0-银行卡 1-支付宝
         * @return H5签约链接, 失败返回null
         */
        String getH5ContractUrl(Long userId, String realName, String idCard, String mobile,
                        String cardNo, Integer paymentType, String idCardFront, String idCardBack);

        /**
         * 解约
         *
         * @param realName 真实姓名
         * @param idCard   身份证号
         * @return 解约结果
         */
        Map<String, Object> cancelContract(String realName, String idCard);
}
