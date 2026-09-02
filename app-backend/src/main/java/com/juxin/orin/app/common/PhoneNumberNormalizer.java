package com.juxin.orin.app.common;

import java.util.regex.Pattern;

/** Normalizes mainland mobile numbers before they reach persistence or SMS. */
public final class PhoneNumberNormalizer {
    private static final Pattern CN_MOBILE = Pattern.compile("1[3-9]\\d{9}");

    private PhoneNumberNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new ApiException(400, "手机号不能为空");
        }
        String value = raw.trim().replaceAll("[\\s-]", "");
        if (value.startsWith("+86")) {
            value = value.substring(3);
        } else if (value.startsWith("0086")) {
            value = value.substring(4);
        }
        if (!CN_MOBILE.matcher(value).matches()) {
            throw new ApiException(400, "请输入有效的中国大陆手机号");
        }
        return value;
    }
}
