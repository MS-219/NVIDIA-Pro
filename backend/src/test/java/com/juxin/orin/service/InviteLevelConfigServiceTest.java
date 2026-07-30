package com.juxin.orin.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InviteLevelConfigServiceTest {

    private final ISystemConfigService configService = mock(ISystemConfigService.class);
    private final InviteLevelConfigService levelConfigService = new InviteLevelConfigService(configService);

    @Test
    void defaultsToFiveLevelsForExistingInstallations() {
        when(configService.getConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "5")).thenReturn("5");

        assertEquals(5, levelConfigService.getLevelCount());
    }

    @Test
    void readsRatesAboveTheLegacyFifthLevel() {
        when(configService.getConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "5")).thenReturn("6");
        when(configService.getConfig("invite.level6.rate", "0.1")).thenReturn("0.97");

        assertEquals(new BigDecimal("0.97"), levelConfigService.getLevelRate(6));
    }

    @Test
    void clampsRemovedManualLevelsToTheHighestConfiguredRate() {
        when(configService.getConfig(InviteLevelConfigService.LEVEL_COUNT_KEY, "5")).thenReturn("2");
        when(configService.getConfig("invite.level2.rate", "0.80")).thenReturn("0.80");

        assertEquals(new BigDecimal("0.80"), levelConfigService.getLevelRate(5));
    }
}
