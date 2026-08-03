package com.juxin.orin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.juxin.orin.config.BossKgConfig;
import com.juxin.orin.entity.AppUser;
import com.juxin.orin.entity.UserContract;
import com.juxin.orin.entity.Withdraw;
import com.juxin.orin.mapper.AppUserMapper;
import com.juxin.orin.mapper.UserContractMapper;
import com.juxin.orin.mapper.WithdrawMapper;
import com.juxin.orin.service.IBossKgService;
import com.juxin.orin.util.DESUtil;
import com.juxin.orin.util.RSAUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 佣金保服务实现类
 */
@Slf4j
@Service
public class BossKgServiceImpl implements IBossKgService {

    @Autowired
    private BossKgConfig bossKgConfig;

    @Autowired
    private UserContractMapper userContractMapper;

    @Autowired
    private WithdrawMapper withdrawMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    // ========== FunCode 常量 ==========
    private static final String FUN_CODE_SIGN_CONTRACT = "6010"; // 无感签约
    private static final String FUN_CODE_QUERY_CONTRACT = "6011"; // 签约查询
    private static final String FUN_CODE_PAYMENT = "6001"; // 批量付款
    private static final String FUN_CODE_QUERY_PAYMENT = "6002"; // 付款查询
    private static final String FUN_CODE_QUERY_BALANCE = "6003"; // 余额查询
    private static final String FUN_CODE_SIGN_H5 = "6026"; // H5有感签约
    private static final String FUN_CODE_CANCEL_CONTRACT = "6036"; // 解约

    @Override
    public boolean isEnabled() {
        return bossKgConfig.isEnabled();
    }

    @Override
    @Transactional
    public String signContract(Long userId, String realName, String idCard, String mobile,
            String cardNo, Integer paymentType, String idCardFront, String idCardBack) {
        log.info("开始签约 - userId:{}, realName:{}, idCard:{}, paymentType:{}", userId, realName, maskIdCard(idCard),
                paymentType);

        try {
            // 检查是否已签约
            UserContract existingContract = userContractMapper.findByUserAndProvider(userId,
                    bossKgConfig.getProviderId());
            if (existingContract != null && existingContract.getStatus() == UserContract.STATUS_SUCCESS) {
                log.info("用户已签约 - userId:{}", userId);
                return null; // 已签约,直接返回成功
            }

            // 构建请求数据
            JSONObject reqData = new JSONObject();
            reqData.set("name", realName);
            reqData.set("cardNo", cardNo);
            reqData.set("idCard", idCard);
            reqData.set("mobile", mobile);
            reqData.set("paymentType", paymentType);
            reqData.set("providerId", bossKgConfig.getProviderId());

            // 身份证照片处理
            if (bossKgConfig.isRequireIdCardPic()) {
                String frontPicHex = convertImageToHex(idCardFront);
                String backPicHex = convertImageToHex(idCardBack);
                if (frontPicHex != null) {
                    reqData.set("idCardPic1", frontPicHex);
                }
                if (backPicHex != null) {
                    reqData.set("idCardPic2", backPicHex);
                }
            }

            // 签约回调地址
            if (StrUtil.isNotBlank(bossKgConfig.getContractNotifyUrl())) {
                reqData.set("notifyUrl", bossKgConfig.getContractNotifyUrl());
            }

            // 发送请求
            JSONObject response = sendRequest(FUN_CODE_SIGN_CONTRACT, reqData);
            if (response == null) {
                return "请求佣金保平台失败";
            }

            String resCode = response.getStr("resCode");
            String resMsg = response.getStr("resMsg");
            log.info("签约响应 - resCode:{}, resMsg:{}", resCode, resMsg);

            // 处理响应
            if ("0000".equals(resCode)) {
                // 保存/更新签约记录
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_PROCESSING, null);
                return null; // 成功
            } else if ("6016".equals(resCode)) {
                // 该实名已在佣金保签约。本地如果归属其他账号，不能直接把当前账号置为已签约。
                UserContract activeContract = userContractMapper.findActiveByIdCardAndProvider(idCard,
                        bossKgConfig.getProviderId());
                if (activeContract != null && activeContract.getUserId() != null
                        && !activeContract.getUserId().equals(userId)) {
                    saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                            UserContract.STATUS_FAILED, "该实名已绑定其他账号，请联系客服迁移实名提现账号");
                    return "该实名已绑定其他账号，请联系客服迁移实名提现账号";
                }
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_SUCCESS, null);
                return null; // 视为成功
            } else if ("6037".equals(resCode)) {
                // 该用户签约中
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_PROCESSING, null);
                return null; // 视为成功,等待回调
            } else if ("6324".equals(resCode)) {
                // 手机号已被其他实名人员注册
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_FAILED, "手机号冲突");
                return "该手机号已被其他实名用户注册，请更换手机号后重试";
            } else {
                // 签约失败
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_FAILED, resMsg);
                return resMsg;
            }

        } catch (Exception e) {
            log.error("签约异常 - userId:{}", userId, e);
            return "签约请求异常: " + e.getMessage();
        }
    }

    @Override
    @Transactional
    public String getH5ContractUrl(Long userId, String realName, String idCard, String mobile,
            String cardNo, Integer paymentType, String idCardFront, String idCardBack) {
        log.info("提取H5签约链接 - userId:{}, realName:{}, idCard:{}", userId, realName, maskIdCard(idCard));

        try {
            // 构建请求数据
            JSONObject reqData = new JSONObject();
            reqData.set("userName", realName);
            reqData.set("cardNo", cardNo);
            reqData.set("idCard", idCard);
            reqData.set("mobile", mobile);
            reqData.set("paymentType", paymentType != null ? paymentType : 0);
            String frontPicHex = convertImageToHex(idCardFront);
            String backPicHex = convertImageToHex(idCardBack);
            if (StrUtil.isBlank(frontPicHex) || StrUtil.isBlank(backPicHex)) {
                throw new RuntimeException("IDENTITY_IMAGE_INVALID:身份证照片读取失败，请重新上传");
            }
            if (StrUtil.isBlank(bossKgConfig.getMiniAppId())) {
                throw new RuntimeException("MINI_APP_ID_MISSING:佣金保小程序 AppID 未配置");
            }
            reqData.set("idCardFrontPic", frontPicHex);
            reqData.set("idCardBackPic", backPicHex);
            reqData.set("appid", bossKgConfig.getMiniAppId());
            reqData.set("redirectBtnName", "返回提现页面");
            reqData.set("redirectUrl", "/pages/withdraw/withdraw");
            reqData.set("redirectType", "RE_LAUNCH");

            // 签约成功回调地址
            if (StrUtil.isNotBlank(bossKgConfig.getContractNotifyUrl())) {
                reqData.set("notifyUrl", bossKgConfig.getContractNotifyUrl());
            }

            // 发送请求
            JSONObject response = sendRequest(FUN_CODE_SIGN_H5, reqData);
            log.info("签约接口返回 - resCode:{}, resMsg:{}", response.getStr("resCode"), response.getStr("resMsg"));
            if (response == null) {
                return null;
            }

            String resCode = response.getStr("resCode");
            if ("0000".equals(resCode)) {
                String encryptedResData = response.getStr("resData");
                JSONObject resDataObj = decryptResData(encryptedResData);

                String h5Url = null;
                if (resDataObj != null) {
                    // 尝试从解密后的对象中获取 URL，有的版本在 resData 字段，有的在 url 字段
                    h5Url = resDataObj.getStr("resData");
                    if (StrUtil.isBlank(h5Url)) {
                        h5Url = resDataObj.getStr("url");
                    }
                } else if (StrUtil.isNotBlank(encryptedResData) && encryptedResData.startsWith("http")) {
                    // 特殊情况：有的接口直接把 URL 放在 resData 里没加密（虽少见但存在）
                    h5Url = encryptedResData;
                }

                if (StrUtil.isNotBlank(h5Url)) {
                    // 保存基础信息为待签约状态，以便后续同步状态
                    saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                            UserContract.STATUS_PENDING, null);
                    return h5Url;
                }
            }

            // 处理特殊错误码，抛出带消息的异常让 Controller 捕获
            String resMsg = response.getStr("resMsg");
            if ("6324".equals(resCode)) {
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_FAILED, "手机号冲突");
                throw new RuntimeException("PHONE_CONFLICT:该手机号已被其他实名用户注册，请更换手机号后重试");
            } else if ("6016".equals(resCode)) {
                // 已签约，同步状态
                UserContract activeContract = userContractMapper.findActiveByIdCardAndProvider(idCard,
                        bossKgConfig.getProviderId());
                if (activeContract != null && activeContract.getUserId() != null
                        && !activeContract.getUserId().equals(userId)) {
                    saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                            UserContract.STATUS_FAILED, "该实名已绑定其他账号，请联系客服迁移实名提现账号");
                    throw new RuntimeException("REALNAME_BOUND_OTHER:该实名已绑定其他账号，请联系客服迁移实名提现账号");
                }
                saveOrUpdateContract(userId, realName, idCard, mobile, cardNo, paymentType,
                        UserContract.STATUS_SUCCESS, null);
                throw new RuntimeException("ALREADY_SIGNED:您已完成签约，无需重复操作");
            }

            log.error("获取H5签约链接解析失败 - resCode:{}, resMsg:{}", resCode, resMsg);
            throw new RuntimeException("SIGN_FAIL:" + (resMsg != null ? resMsg : "获取签约链接失败"));

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains(":")) {
                throw e; // 带标识的异常继续上抛
            }
            log.error("获取H5签约链接异常", e);
            return null;
        } catch (Exception e) {
            log.error("获取H5签约链接异常", e);
            return null;
        }
    }

    @Override
    public Map<String, Object> queryContractStatus(String realName, String idCard, String mobile) {
        log.info("查询签约状态 - realName:{}, idCard:{}", realName, maskIdCard(idCard));

        Map<String, Object> result = new HashMap<>();
        try {
            JSONObject reqData = new JSONObject();
            reqData.set("name", realName);
            reqData.set("idCard", idCard);
            reqData.set("mobile", mobile);
            reqData.set("providerId", bossKgConfig.getProviderId());

            JSONObject response = sendRequest(FUN_CODE_QUERY_CONTRACT, reqData);
            if (response == null) {
                result.put("success", false);
                result.put("message", "请求失败");
                return result;
            }

            String resCode = response.getStr("resCode");
            log.info("董哥-状态接口返回码: {}, 消息: {}", resCode, response.getStr("resMsg"));
            if ("0000".equals(resCode)) {
                JSONObject resData = decryptResData(response.getStr("resData"));
                if (resData != null) {
                    int state = resData.getInt("state");
                    log.info("董哥-解密真实状态(1代表已签约): {}, 姓名: {}", state, realName);
                    String retMsg = resData.getStr("retMsg");

                    // 同步到本地数据库
                    saveOrUpdateContractSync(realName, idCard, mobile, state, retMsg);

                    result.put("success", true);
                    result.put("state", state);
                    result.put("message", retMsg);
                    return result;
                }
            }

            result.put("success", false);
            result.put("message", response.getStr("resMsg"));
            return result;

        } catch (Exception e) {
            log.error("查询签约状态异常", e);
            result.put("success", false);
            result.put("message", "查询异常: " + e.getMessage());
            return result;
        }
    }

    @Override
    public boolean isUserContracted(Long userId) {
        UserContract contract = userContractMapper.findByUserAndProvider(userId, bossKgConfig.getProviderId());
        return contract != null && contract.getStatus() == UserContract.STATUS_SUCCESS;
    }

    @Override
    public Map<String, Object> cancelContract(String realName, String idCard) {
        log.info("发起解约 - realName:{}, idCard:{}", realName, maskIdCard(idCard));

        Map<String, Object> result = new HashMap<>();
        try {
            JSONObject reqData = new JSONObject();
            // 解约接口字段名可能与签约接口不同，同时传两套确保兼容
            reqData.set("name", realName);
            reqData.set("userName", realName);
            reqData.set("idCard", idCard);
            reqData.set("idCardNo", idCard);
            reqData.set("providerId", bossKgConfig.getProviderId());
            reqData.set("providerID", bossKgConfig.getProviderId());

            JSONObject response = sendRequest(FUN_CODE_CANCEL_CONTRACT, reqData);
            if (response == null) {
                result.put("success", false);
                result.put("message", "请求失败");
                return result;
            }

            String resCode = response.getStr("resCode");
            log.info("解约接口返回码: {}, 消息: {}", resCode, response.getStr("resMsg"));

            if ("0000".equals(resCode)) {
                // 解密响应数据
                String resDataStr = response.getStr("resData");
                if (StrUtil.isNotBlank(resDataStr)) {
                    JSONObject resData = decryptResData(resDataStr);
                    if (resData != null) {
                        String state = resData.getStr("state");
                        String retMsg = resData.getStr("retMsg");
                        log.info("解约结果 - state:{}, retMsg:{}", state, retMsg);

                        if ("1".equals(state)) {
                            // 解约成功，更新本地数据库
                            userContractMapper.updateStatusByIdCardAndProvider(idCard, bossKgConfig.getProviderId(),
                                    UserContract.STATUS_CANCELLED);
                            result.put("success", true);
                            result.put("message", "解约成功");
                            return result;
                        } else {
                            result.put("success", false);
                            result.put("message", retMsg != null ? retMsg : "解约失败");
                            return result;
                        }
                    }
                }
                // 没有 resData 但 resCode=0000，也认为成功
                userContractMapper.updateStatusByIdCardAndProvider(idCard, bossKgConfig.getProviderId(),
                        UserContract.STATUS_CANCELLED);
                result.put("success", true);
                result.put("message", "解约成功");
                return result;
            }

            result.put("success", false);
            result.put("message", response.getStr("resMsg"));
            return result;

        } catch (Exception e) {
            log.error("解约异常", e);
            result.put("success", false);
            result.put("message", "解约异常: " + e.getMessage());
            return result;
        }
    }

    @Override
    public UserContract getUserContract(Long userId) {
        return userContractMapper.findByUserAndProvider(userId, bossKgConfig.getProviderId());
    }

    @Override
    @Transactional
    public String payment(Withdraw withdraw, String idCard) {
        log.info("开始付款 - withdrawId:{}, amount:{}, fee:{}, actualAmount:{}",
                withdraw.getId(), withdraw.getAmount(), withdraw.getFee(), withdraw.getActualAmount());

        try {
            // 生成批次号和订单号
            String batchId = StrUtil.isNotBlank(withdraw.getBossKgBatchId())
                    ? withdraw.getBossKgBatchId()
                    : generateBatchId();
            String orderId = generateMerchantOrderId(withdraw.getId(), batchId);

            // 先持久化批次号。发生网络超时时仍可使用原批次主动查询，避免重复打款。
            withdraw.setBossKgBatchId(batchId);
            withdraw.setBossKgState(1);
            if (withdrawMapper.updateById(withdraw) != 1) {
                return "保存佣金保付款批次失败，请刷新后重试";
            }

            // 金额转换(元->分) - 使用扣除手续费后的实际到账金额
            long amountFen = withdraw.getActualAmount().multiply(new BigDecimal("100")).longValue();

            // 构建付款项
            JSONObject payItem = new JSONObject();
            payItem.set("merOrderId", orderId);
            payItem.set("amt", amountFen);
            payItem.set("payeeName", withdraw.getRealName());
            payItem.set("payeeAcc",
                    withdraw.getBankCardNo() != null ? withdraw.getBankCardNo() : withdraw.getAlipayAccount());
            payItem.set("idCard", idCard);
            payItem.set("mobile", withdraw.getMobile());
            payItem.set("paymentType", withdraw.getBankCardNo() != null ? 0 : 1); // 0-银行卡 1-支付宝
            payItem.set("memo", bossKgConfig.getDefaultMemo());

            if (StrUtil.isNotBlank(bossKgConfig.getPaymentNotifyUrl())) {
                payItem.set("notifyUrl", bossKgConfig.getPaymentNotifyUrl());
            }

            JSONArray payItems = new JSONArray();
            payItems.add(payItem);

            // 构建请求
            JSONObject reqData = new JSONObject();
            reqData.set("merBatchId", batchId);
            reqData.set("taskId", bossKgConfig.getTaskId());
            reqData.set("providerId", bossKgConfig.getProviderId());
            reqData.set("payItems", payItems);

            // 发送请求
            JSONObject response = sendRequest(FUN_CODE_PAYMENT, reqData);
            if (response == null) {
                return "请求佣金保平台失败";
            }

            String resCode = response.getStr("resCode");
            String resMsg = response.getStr("resMsg");
            log.info("付款响应 - resCode:{}, resMsg:{}", resCode, resMsg);

            if ("0000".equals(resCode)) {
                // 解析响应数据
                JSONObject resData = decryptResData(response.getStr("resData"));
                if (resData != null) {
                    JSONArray payResultList = resData.getJSONArray("payResultList");
                    if (payResultList != null && !payResultList.isEmpty()) {
                        JSONObject payResult = payResultList.getJSONObject(0);
                        String orderNo = payResult.getStr("orderNo"); // 平台订单号
                        String itemResCode = payResult.getStr("resCode");
                        String itemResMsg = payResult.getStr("resMsg");

                        if ("0000".equals(itemResCode)) {
                            // 更新提现记录
                            withdraw.setBossKgBatchId(batchId);
                            withdraw.setBossKgOrderNo(orderNo);
                            withdraw.setBossKgState(1); // 付款中
                            withdrawMapper.updateById(withdraw);
                            return null; // 成功
                        } else {
                            clearRejectedPaymentReservation(withdraw);
                            return itemResMsg;
                        }
                    }
                }
                // 即使没有明确的子订单结果,也可能是成功的
                withdraw.setBossKgBatchId(batchId);
                withdraw.setBossKgState(1);
                withdrawMapper.updateById(withdraw);
                return null;

            } else if ("6019".equals(resCode)) {
                clearRejectedPaymentReservation(withdraw);
                return "商户余额不足,请联系管理员充值";
            } else if ("6021".equals(resCode)) {
                clearRejectedPaymentReservation(withdraw);
                return "用户未签约,请先完成签约";
            } else if ("6100".equals(resCode)) {
                // 微信新模式,需要用户手动确认
                withdraw.setBossKgBatchId(batchId);
                withdraw.setBossKgState(6); // 待确认
                withdrawMapper.updateById(withdraw);
                return "需要在微信中确认收款";
            } else {
                clearRejectedPaymentReservation(withdraw);
                return resMsg;
            }

        } catch (Exception e) {
            log.error("付款异常 - withdrawId:{}", withdraw.getId(), e);
            return "付款请求异常: " + e.getMessage();
        }
    }

    @Override
    public Map<String, Object> queryPaymentStatus(String batchId, String orderId) {
        log.info("查询付款状态 - batchId:{}, orderId:{}", batchId, orderId);

        Map<String, Object> result = new HashMap<>();
        try {
            JSONObject queryItem = new JSONObject();
            if (StrUtil.isNotBlank(orderId)) {
                queryItem.set("orderNo", orderId);
            }

            JSONArray queryItems = new JSONArray();
            queryItems.add(queryItem);

            JSONObject reqData = new JSONObject();
            reqData.set("merBatchId", batchId);
            reqData.set("queryItems", queryItems);

            JSONObject response = sendRequest(FUN_CODE_QUERY_PAYMENT, reqData);
            if (response == null) {
                result.put("success", false);
                result.put("message", "请求失败");
                return result;
            }

            String resCode = response.getStr("resCode");
            if ("0000".equals(resCode)) {
                JSONObject resData = decryptResData(response.getStr("resData"));
                if (resData != null) {
                    JSONArray queryItemsResult = resData.getJSONArray("queryItems");
                    if (queryItemsResult != null && !queryItemsResult.isEmpty()) {
                        JSONObject item = queryItemsResult.getJSONObject(0);
                        result.put("success", true);
                        result.put("state", item.getInt("state"));
                        result.put("orderNo", item.getStr("orderNo"));
                        result.put("amt", item.getLong("amt"));
                        result.put("fee", item.getLong("fee"));
                        result.put("userFee", item.getLong("userFee"));
                        result.put("userDueAmt", item.getLong("userDueAmt"));
                        result.put("resMsg", item.getStr("resMsg"));
                        return result;
                    }
                }
            }

            result.put("success", false);
            result.put("resCode", response.getStr("resCode"));
            result.put("message", response.getStr("resMsg"));
            return result;

        } catch (Exception e) {
            log.error("查询付款状态异常", e);
            result.put("success", false);
            result.put("message", "查询异常: " + e.getMessage());
            return result;
        }
    }

    @Override
    public Long queryBalance() {
        log.info("查询商户余额");

        try {
            JSONObject reqData = new JSONObject();
            reqData.set("providerId", bossKgConfig.getProviderId());

            JSONObject response = sendRequest(FUN_CODE_QUERY_BALANCE, reqData);
            if (response == null) {
                return null;
            }

            String resCode = response.getStr("resCode");
            if ("0000".equals(resCode)) {
                JSONObject resData = decryptResData(response.getStr("resData"));
                if (resData != null) {
                    return resData.getLong("balance");
                }
            }

            log.warn("查询余额失败 - resCode:{}, resMsg:{}", resCode, response.getStr("resMsg"));
            return null;

        } catch (Exception e) {
            log.error("查询余额异常", e);
            return null;
        }
    }

    @Override
    @Transactional
    public String handleContractNotify(String requestBody) {
        log.info("处理签约回调");

        try {
            JSONObject request = JSONUtil.parseObj(requestBody);

            if (!bossKgConfig.getMerId().equals(request.getStr("merId"))) {
                log.warn("签约回调商户号不匹配");
                return "FAIL";
            }

            // 验签
            if (!verifySign(request)) {
                log.warn("签约回调验签失败");
                return "FAIL";
            }

            // 解密数据
            JSONObject resData = decryptResData(request.getStr("resData"));
            if (resData == null) {
                log.warn("签约回调解密失败");
                return "FAIL";
            }

            String idCard = resData.getStr("idCard");
            Integer state = resData.getInt("state");
            String retMsg = resData.getStr("retMsg");

            log.info("签约回调数据 - idCard:{}, state:{}, retMsg:{}", maskIdCard(idCard), state, retMsg);

            // 更新签约状态
            UserContract contract = userContractMapper.findSyncTargetByIdCardAndProvider(idCard,
                    bossKgConfig.getProviderId());
            if (contract != null) {
                if (hasOtherActiveContract(idCard, contract)) {
                    markContractBoundToOther(contract);
                    return "SUCCESS";
                }
                if (state == 1) {
                    contract.setStatus(UserContract.STATUS_SUCCESS);
                    contract.setContractTime(LocalDateTime.now());
                } else if (state == 4) {
                    contract.setStatus(UserContract.STATUS_FAILED);
                    contract.setFailReason(retMsg);
                } else if (state == 3) {
                    contract.setStatus(UserContract.STATUS_PROCESSING);
                }
                contract.setUpdateTime(LocalDateTime.now());
                userContractMapper.updateById(contract);
            }

            return "SUCCESS";

        } catch (Exception e) {
            log.error("处理签约回调异常", e);
            return "FAIL";
        }
    }

    @Override
    @Transactional
    public String handlePaymentNotify(String requestBody) {
        log.info("处理付款回调");

        try {
            JSONObject request = JSONUtil.parseObj(requestBody);

            if (!bossKgConfig.getMerId().equals(request.getStr("merId"))) {
                log.warn("付款回调商户号不匹配");
                return "FAIL";
            }

            // 验签
            if (!verifySign(request)) {
                log.warn("付款回调验签失败");
                return "FAIL";
            }

            // 解密数据
            JSONObject resData = decryptResData(request.getStr("resData"));
            if (resData == null) {
                log.warn("付款回调解密失败");
                return "FAIL";
            }

            String merOrderId = resData.getStr("merOrderId");
            String orderNo = resData.getStr("orderNo");
            Integer state = resData.getInt("state");
            Long amt = resData.getLong("amt");
            Long fee = resData.getLong("fee");
            Long userFee = resData.getLong("userFee");
            Long userDueAmt = resData.getLong("userDueAmt");
            String resMsg = resData.getStr("resMsg");

            log.info("付款回调数据 - merOrderId:{}, orderNo:{}, state:{}, amt:{}", merOrderId, orderNo, state, amt);

            // 从订单号中提取提现ID (格式: W{withdrawId}_{timestamp})
            Long withdrawId = extractWithdrawId(merOrderId);
            if (withdrawId == null) {
                log.warn("无法解析提现ID - merOrderId:{}", merOrderId);
                return "FAIL";
            }

            // 更新提现记录
            Withdraw withdraw = withdrawMapper.selectById(withdrawId);
            if (withdraw != null) {
                boolean repeatedTerminalCallback = state != null
                        && state.equals(withdraw.getBossKgState())
                        && StrUtil.isNotBlank(orderNo)
                        && orderNo.equals(withdraw.getBossKgOrderNo())
                        && (state == 3 || state == 4 || state == 7);

                withdraw.setBossKgOrderNo(orderNo);
                withdraw.setBossKgState(state);

                // 转换金额(分->元)
                if (fee != null) {
                    withdraw.setBossKgFee(new BigDecimal(fee).divide(new BigDecimal("100")));
                }
                if (userFee != null) {
                    withdraw.setBossKgUserFee(new BigDecimal(userFee).divide(new BigDecimal("100")));
                }
                if (userDueAmt != null) {
                    withdraw.setBossKgActualAmount(new BigDecimal(userDueAmt).divide(new BigDecimal("100")));
                }

                // 更新提现状态
                if (state == 3) {
                    // 付款成功
                    withdraw.setStatus(3); // 已打款
                    withdraw.setProcessTime(LocalDateTime.now());
                    withdraw.setBossKgFailed(false);
                } else if (state == 4) {
                    // 付款失败 - 设为失败状态，停止自动重试
                    withdraw.setStatus(4); // 标记为失败
                    withdraw.setBossKgFailed(true);
                    if (!repeatedTerminalCallback) {
                        withdraw.setPaymentFailCount(
                                (withdraw.getPaymentFailCount() != null ? withdraw.getPaymentFailCount() : 0) + 1);
                    }
                    withdraw.setRejectReason(resMsg);
                    // 保留佣金保信息作为记录，但清空 batchId 以便可以重新打款
                    withdraw.setBossKgBatchId(null);
                }

                withdrawMapper.updateById(withdraw);
            }

            return "SUCCESS";

        } catch (Exception e) {
            log.error("处理付款回调异常", e);
            return "FAIL";
        }
    }

    // ========== 私有方法 ==========

    /**
     * 发送请求到佣金保平台
     */
    private JSONObject sendRequest(String funCode, JSONObject reqData) {
        try {
            // 生成请求ID
            String reqId = UUID.randomUUID().toString().replace("-", "");
            String requestReqId = reqId.length() > 30 ? reqId.substring(0, 30) : reqId;

            // 加密请求数据
            String reqDataJson = reqData.toString();
            String encryptedReqData = DESUtil.encrypt(reqDataJson, bossKgConfig.getDesKey());

            // 构建请求体
            JSONObject request = new JSONObject();
            request.set("reqId", requestReqId);
            request.set("funCode", funCode);
            request.set("merId", bossKgConfig.getMerId());
            request.set("version", bossKgConfig.getVersion());
            request.set("reqData", encryptedReqData);

            // 签名 (根据文档 3.3 节，仅对 Base64 编码后的密文进行签名)
            String signData = encryptedReqData;
            String sign = RSAUtil.sign(signData, bossKgConfig.getMerchantPrivateKey());
            request.set("sign", sign);

            log.info("发送请求 - funCode:{}, reqId:{}", funCode, reqId);

            // 发送HTTP请求
            HttpResponse httpResponse = HttpRequest.post(bossKgConfig.getApiUrl())
                    .contentType("application/json;charset=utf-8")
                    .timeout(bossKgConfig.getReadTimeout())
                    .body(request.toString())
                    .execute();

            if (httpResponse.isOk()) {
                String responseBody = httpResponse.body();
                log.debug("响应: {}", responseBody);
                JSONObject response = JSONUtil.parseObj(responseBody);
                if (!matchesRequest(response, requestReqId, funCode)) {
                    log.error("佣金保响应与请求不匹配 - funCode:{}, reqId:{}", funCode, requestReqId);
                    return null;
                }
                if (StrUtil.isNotBlank(response.getStr("resData")) && !verifySign(response)) {
                    log.error("佣金保同步响应验签失败 - funCode:{}, reqId:{}", funCode, requestReqId);
                    return null;
                }
                return response;
            } else {
                log.error("请求失败 - status:{}", httpResponse.getStatus());
                return null;
            }

        } catch (Exception e) {
            log.error("发送请求异常 - funCode:{}", funCode, e);
            return null;
        }
    }

    /**
     * 解密响应数据
     */
    private JSONObject decryptResData(String encryptedResData) {
        try {
            if (StrUtil.isBlank(encryptedResData)) {
                return null;
            }
            String decrypted = DESUtil.decrypt(encryptedResData, bossKgConfig.getDesKey());
            if (StrUtil.isNotBlank(decrypted) && !decrypted.trim().startsWith("{")) {
                // 如果不是 JSON 格式，构造一个包含原字符串的 JSONObject 返回，兼容现有逻辑
                JSONObject wrap = new JSONObject();
                wrap.set("resData", decrypted.trim());
                return wrap;
            }
            return JSONUtil.parseObj(decrypted);
        } catch (Exception e) {
            log.error("解密响应数据失败", e);
            return null;
        }
    }

    /**
     * 验证响应签名
     */
    private boolean verifySign(JSONObject response) {
        try {
            String sign = response.getStr("sign");
            String resData = response.getStr("resData");
            if (StrUtil.isBlank(sign) || StrUtil.isBlank(resData)) {
                return false;
            }
            return RSAUtil.verify(resData, sign, bossKgConfig.getPlatformPublicKey());
        } catch (Exception e) {
            log.error("验签异常", e);
            return false;
        }
    }

    private boolean matchesRequest(JSONObject response, String reqId, String funCode) {
        return reqId.equals(response.getStr("reqId"))
                && funCode.equals(response.getStr("funCode"))
                && bossKgConfig.getMerId().equals(response.getStr("merId"))
                && bossKgConfig.getVersion().equals(response.getStr("version"));
    }

    /**
     * 保存或更新签约记录
     */
    private void saveOrUpdateContract(Long userId, String realName, String idCard, String mobile,
            String cardNo, Integer paymentType, Integer status, String failReason) {
        UserContract contract = userContractMapper.findByUserAndProvider(userId, bossKgConfig.getProviderId());
        if (contract == null) {
            contract = new UserContract();
            contract.setUserId(userId);
            contract.setProviderId(bossKgConfig.getProviderId());
            contract.setCreateTime(LocalDateTime.now());
        }

        contract.setRealName(realName);
        contract.setIdCard(idCard);
        contract.setMobile(mobile);
        contract.setPaymentType(paymentType);
        contract.setStatus(status);
        contract.setFailReason(failReason);
        contract.setUpdateTime(LocalDateTime.now());

        if (paymentType == UserContract.PAY_TYPE_BANK) {
            contract.setBankCardNo(cardNo);
        } else if (paymentType == UserContract.PAY_TYPE_ALIPAY) {
            contract.setAlipayAccount(cardNo);
        }

        if (status == UserContract.STATUS_SUCCESS) {
            contract.setContractTime(LocalDateTime.now());
        }

        if (contract.getId() == null) {
            userContractMapper.insert(contract);
        } else {
            userContractMapper.updateById(contract);
        }
    }

    /**
     * 生成批次号
     */
    private String generateBatchId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String random = String.format("%08d", (int) (Math.random() * 100000000));
        return timestamp + random;
    }

    private String generateMerchantOrderId(Long withdrawId, String batchId) {
        String suffix = batchId.length() > 10 ? batchId.substring(batchId.length() - 10) : batchId;
        String orderId = "W" + withdrawId + "_" + suffix;
        return orderId.length() > 32 ? orderId.substring(0, 32) : orderId;
    }

    private void clearRejectedPaymentReservation(Withdraw withdraw) {
        withdraw.setBossKgBatchId(null);
        withdrawMapper.updateById(withdraw);
    }

    /**
     * 从订单号中提取提现ID
     */
    private Long extractWithdrawId(String merOrderId) {
        try {
            if (merOrderId != null && merOrderId.startsWith("W")) {
                String idPart = merOrderId.substring(1, merOrderId.indexOf("_"));
                return Long.parseLong(idPart);
            }
        } catch (Exception e) {
            log.error("解析提现ID失败 - merOrderId:{}", merOrderId, e);
        }
        return null;
    }

    /**
     * 将图片URL转换为16进制字符串
     */
    private String convertImageToHex(String imageSource) {
        try {
            if (StrUtil.isBlank(imageSource)) {
                return null;
            }

            byte[] imageBytes;

            if (imageSource.startsWith("http://") || imageSource.startsWith("https://")) {
                // URL图片
                URL url = java.net.URI.create(imageSource).toURL();
                try (InputStream is = url.openStream()) {
                    BufferedImage image = ImageIO.read(is);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", baos);
                    imageBytes = baos.toByteArray();
                }
            } else {
                // Base64图片
                String base64Data = imageSource;
                if (imageSource.contains(",")) {
                    base64Data = imageSource.substring(imageSource.indexOf(",") + 1);
                }
                imageBytes = Base64.getDecoder().decode(base64Data);
            }

            // 转换为16进制
            StringBuilder sb = new StringBuilder();
            for (byte b : imageBytes) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("转换图片失败 - source:{}", imageSource, e);
            return null;
        }
    }

    /**
     * 脱敏身份证号
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 根据三要素查找并同步状态
     */
    private void saveOrUpdateContractSync(String realName, String idCard, String mobile, int onlineState,
            String retMsg) {
        UserContract contract = userContractMapper.findSyncTargetByIdCardAndProvider(idCard,
                bossKgConfig.getProviderId());
        if (contract == null && onlineState != 0) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AppUser> userWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            userWrapper.eq(AppUser::getIdCard, idCard);
            AppUser u = appUserMapper.selectOne(userWrapper);
            if (u != null) {
                contract = new UserContract();
                contract.setUserId(u.getId());
                contract.setProviderId(bossKgConfig.getProviderId());
                contract.setRealName(realName);
                contract.setIdCard(idCard);
                contract.setMobile(mobile);
                contract.setCreateTime(LocalDateTime.now());
                userContractMapper.insert(contract);
                log.info("同步补全：已为用户ID:{} 创建本地签约项", u.getId());
            }
        }

        if (contract != null) {
            if (hasOtherActiveContract(idCard, contract)) {
                markContractBoundToOther(contract);
                return;
            }
            int localStatus = onlineState;
            if (onlineState == 4)
                localStatus = UserContract.STATUS_FAILED;
            if (onlineState == 0) {
                // 平台返回"未签约"状态时的处理逻辑：
                // - 签约中 -> 待签约（签约流程可能未完成）
                // - 签约失败/已解约 -> 保留原状态（用户需要看到失败/解约原因，手动点击重新申请）
                // - 其他 -> 设为待签约
                if (contract.getStatus() == UserContract.STATUS_PROCESSING) {
                    localStatus = UserContract.STATUS_PENDING;
                } else if (contract.getStatus() == UserContract.STATUS_FAILED
                        || contract.getStatus() == UserContract.STATUS_CANCELLED) {
                    localStatus = contract.getStatus(); // 保留原状态
                } else {
                    localStatus = UserContract.STATUS_PENDING;
                }
            }
            contract.setStatus(localStatus);
            if (StrUtil.isNotBlank(retMsg))
                contract.setFailReason(retMsg);
            contract.setUpdateTime(LocalDateTime.now());
            userContractMapper.updateById(contract);
            log.info("同步成功 - 用户:{}, 状态:{}", realName, localStatus);
        }
    }

    private boolean hasOtherActiveContract(String idCard, UserContract contract) {
        UserContract activeContract = userContractMapper.findActiveByIdCardAndProvider(idCard,
                bossKgConfig.getProviderId());
        if (activeContract == null || contract == null) {
            return false;
        }
        if (activeContract.getId() != null && activeContract.getId().equals(contract.getId())) {
            return false;
        }
        if (activeContract.getUserId() != null && activeContract.getUserId().equals(contract.getUserId())) {
            return false;
        }
        return true;
    }

    private void markContractBoundToOther(UserContract contract) {
        contract.setStatus(UserContract.STATUS_FAILED);
        contract.setFailReason("该实名已绑定其他账号，请联系客服迁移实名提现账号");
        contract.setUpdateTime(LocalDateTime.now());
        userContractMapper.updateById(contract);
        log.warn("签约同步被拦截 - userId:{}, idCard:{}, reason: same identity bound to another active user",
                contract.getUserId(), maskIdCard(contract.getIdCard()));
    }
}
