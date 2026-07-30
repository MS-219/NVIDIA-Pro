package com.juxin.orin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record EdgeEnrollRequest(
        String sn,
        @JsonProperty("image_version") String imageVersion,
        @JsonProperty("hardware_fingerprint") String hardwareFingerprint,
        Map<String, Object> telemetry) {
}
