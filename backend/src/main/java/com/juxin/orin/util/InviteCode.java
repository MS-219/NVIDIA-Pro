package com.juxin.orin.util;

/** 已发布的微信小程序分享 JX 加至少六位的用户 ID。 */
public final class InviteCode {
    private InviteCode() {}

    public static Long userId(String value) {
        if (value == null) return null;
        String code = value.trim();
        if (!code.matches("(?i)JX[0-9]{6,19}")) return null;
        try {
            long id = Long.parseLong(code.substring(2));
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
