package com.juxin.orin.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    @Test
    void forwardedPublicIpWinsOverDockerProxyAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "172.18.0.1");
        request.addHeader("X-Forwarded-For", "114.114.114.114, 172.18.0.1");
        request.setRemoteAddr("172.18.0.2");

        assertEquals("114.114.114.114", ClientIpResolver.resolve(request));
    }

    @Test
    void cloudflareAddressHasPriorityWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "223.5.5.5");
        request.addHeader("X-Forwarded-For", "114.114.114.114, 172.18.0.1");

        assertEquals("223.5.5.5", ClientIpResolver.resolve(request));
    }

    @Test
    void directPublicRemoteAddressCannotBeReplacedByForwardedSpoof() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "223.6.6.6");
        request.addHeader("X-Forwarded-For", "114.114.114.114");

        assertEquals("223.6.6.6", ClientIpResolver.resolve(request));
    }

    @Test
    void privateAndCarrierNatAddressesAreNotPublic() {
        assertFalse(ClientIpResolver.isPublicAddress("192.168.1.20"));
        assertFalse(ClientIpResolver.isPublicAddress("172.18.0.1"));
        assertFalse(ClientIpResolver.isPublicAddress("100.64.10.20"));
        assertFalse(ClientIpResolver.isPublicAddress("fc00::10"));
        assertTrue(ClientIpResolver.isPublicAddress("114.114.114.114"));
    }

    @Test
    void fallsBackToFirstPrivateAddressWhenNoPublicAddressExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.1.20, 172.18.0.1");
        request.setRemoteAddr("172.18.0.2");

        assertEquals("192.168.1.20", ClientIpResolver.resolve(request));
    }
}
