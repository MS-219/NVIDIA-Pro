package com.juxin.orin.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.juxin.orin.service.IWechatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 微信登录服务实现
 */
@Service
public class WechatServiceImpl implements IWechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatServiceImpl.class);

    @Value("${wechat.miniapp.appid}")
    private String appid;

    @Value("${wechat.miniapp.secret}")
    private String secret;

    // 微信小程序登录接口
    private static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Override
    public String code2Session(String code) {
        if (code == null || code.isEmpty()) {
            log.error("code 不能为空");
            return null;
        }

        try {
            // 拼接请求 URL
            String url = String.format(
                    "%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    WX_LOGIN_URL, appid, secret, code);

            log.info("调用微信登录接口: appid={}", appid);

            // 发起请求
            String response = HttpUtil.get(url, 5000);
            log.info("微信登录返回: {}", response);

            if (response != null && !response.isEmpty()) {
                JSONObject json = JSONUtil.parseObj(response);

                // 检查错误
                if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
                    log.error("微信登录失败: errcode={}, errmsg={}",
                            json.getInt("errcode"), json.getStr("errmsg"));
                    return null;
                }

                // 返回 openid
                String openid = json.getStr("openid");
                if (openid != null && !openid.isEmpty()) {
                    log.info("微信登录成功: openid={}", openid);
                    return openid;
                }
            }
        } catch (Exception e) {
            log.error("微信登录异常: {}", e.getMessage());
        }

        return null;
    }

    // 微信获取手机号接口
    private static final String WX_PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    @Override
    public String getPhoneNumber(String code) {
        if (code == null || code.isEmpty()) {
            log.error("code 不能为空");
            return null;
        }

        try {
            // 先获取 access_token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("获取 access_token 失败");
                return null;
            }

            // 调用获取手机号接口
            String url = WX_PHONE_URL + "?access_token=" + accessToken;
            String body = JSONUtil.toJsonStr(java.util.Map.of("code", code));

            log.info("调用微信获取手机号接口: code={}", code);

            String response = HttpUtil.post(url, body);
            log.info("微信获取手机号返回: {}", response);

            if (response != null && !response.isEmpty()) {
                JSONObject json = JSONUtil.parseObj(response);

                if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
                    log.error("获取手机号失败: errcode={}, errmsg={}",
                            json.getInt("errcode"), json.getStr("errmsg"));
                    return null;
                }

                JSONObject phoneInfo = json.getJSONObject("phone_info");
                if (phoneInfo != null) {
                    return phoneInfo.getStr("purePhoneNumber");
                }
            }
        } catch (Exception e) {
            log.error("获取手机号异常: {}", e.getMessage());
        }

        return null;
    }

    // 获取 access_token（可缓存）
    private String getAccessToken() {
        try {
            String url = String.format(
                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                    appid, secret);

            String response = HttpUtil.get(url, 5000);
            if (response != null && !response.isEmpty()) {
                JSONObject json = JSONUtil.parseObj(response);
                return json.getStr("access_token");
            }
        } catch (Exception e) {
            log.error("获取 access_token 异常: {}", e.getMessage());
        }
        return null;
    }
}
