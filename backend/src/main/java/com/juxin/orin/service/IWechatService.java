package com.juxin.orin.service;

/**
 * 微信登录服务接口
 */
public interface IWechatService {

    /**
     * 通过 code 换取 openid
     * 
     * @param code 小程序登录时获取的 code
     * @return openid
     */
    String code2Session(String code);

    /**
     * 通过 code 获取手机号
     * 
     * @param code 小程序 getPhoneNumber 获取的 code
     * @return 手机号
     */
    String getPhoneNumber(String code);
}
