package com.juxin.orin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceBindCodeGeneratorTest {

    @Test
    void seededCodesUseTheJuxinDevicePrefixAndRemainStable() {
        String first = DeviceBindCodeGenerator.fromSeed("ORIN-0123456789ABCDEF");
        String repeated = DeviceBindCodeGenerator.fromSeed("ORIN-0123456789ABCDEF");
        String other = DeviceBindCodeGenerator.fromSeed("ORIN-FEDCBA9876543210");

        assertTrue(first.matches("JD[A-F0-9]{6}"));
        assertEquals(first, repeated);
        assertNotEquals(first, other);
    }

    @Test
    void affiliateCodesUseTheSameJuxinFormat() {
        assertTrue(DeviceBindCodeGenerator.randomCode().matches("JD[A-Z2-9]{6}"));
    }

    @Test
    void seededCodesRejectMissingIdentity() {
        assertThrows(IllegalArgumentException.class, () -> DeviceBindCodeGenerator.fromSeed(" "));
    }
}
