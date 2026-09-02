package com.juxin.orin.app.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberNormalizerTest {
    @Test
    void acceptsChinaPrefixAndFormatting() {
        assertEquals("13800138000", PhoneNumberNormalizer.normalize("+86 138-0013-8000"));
        assertEquals("13912345678", PhoneNumberNormalizer.normalize("13912345678"));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(ApiException.class, () -> PhoneNumberNormalizer.normalize("12000000000"));
        assertThrows(ApiException.class, () -> PhoneNumberNormalizer.normalize("1380013800"));
    }
}
