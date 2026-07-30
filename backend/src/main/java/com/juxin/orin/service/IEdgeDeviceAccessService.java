package com.juxin.orin.service;

import com.juxin.orin.dto.EdgeEnrollRequest;
import com.juxin.orin.dto.EdgeEnrollResponse;
import com.juxin.orin.entity.Device;

public interface IEdgeDeviceAccessService {

    String DEVICE_TOKEN_HEADER = "X-Orin-Device-Token";

    EdgeEnrollResponse enroll(EdgeEnrollRequest request, String clientIp);

    Device authenticate(String rawToken);

    void requireOwnedSn(Device device, String requestedSn);

    void requireMatchingHardwareFingerprint(Device device, String hardwareFingerprint);
}
