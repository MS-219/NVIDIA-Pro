package com.juxin.orin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.juxin.orin.entity.AppUser;

public interface IAppUserService extends IService<AppUser> {

    /**
     * 微信登录/注册
     * 
     * @param openid 微信 OpenID
     * @return JWT Token
     */
    String wxLogin(String openid);

    /**
     * 微信登录/注册，并返回本次登录使用的用户快照。
     */
    WxLoginResult wxLoginWithUser(String openid);

    /**
     * 根据 OpenID 获取用户
     */
    AppUser getByOpenid(String openid);

    /**
     * 自动更新所有用户的分润等级
     */
    void updateAllUserLevels();

    /**
     * 将超出当前配置范围的用户等级收敛到最高有效等级。
     */
    void clampUserLevels(int maxLevel);

    /**
     * 更新指定用户的等级
     */
    void updateLevel(Long userId);

    /**
     * 根据商户ID和外部用户ID同步用户
     */
    AppUser syncExternalUser(Long merchantId, String externalUserId, String nickname, String avatarUrl);

    /** 获取回收站用户。 */
    IPage<AppUser> getRecycleBin(Integer page, Integer size, String keyword);

    /** 将平台用户移入回收站。 */
    boolean moveToRecycleBin(Long userId);

    /** 恢复回收站用户。 */
    boolean restoreFromRecycleBin(Long userId);

    /** 永久删除回收站全部用户及其关联数据。 */
    int clearRecycleBin();

    record WxLoginResult(String token, AppUser user, boolean isNewUser) {
    }
}
