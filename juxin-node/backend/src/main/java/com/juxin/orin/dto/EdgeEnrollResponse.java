package com.juxin.orin.dto;

public record EdgeEnrollResponse(
        String deviceSn,
        Long deviceId,
        String bindCode,
        String deviceToken) {
}
