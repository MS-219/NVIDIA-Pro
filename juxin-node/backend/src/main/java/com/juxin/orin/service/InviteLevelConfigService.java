package com.juxin.orin.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Centralized access to the ordered, administrator-configurable invite levels.
 */
@Service
public class InviteLevelConfigService {

    public static final String LEVEL_COUNT_KEY = "invite.level.count";
    public static final int DEFAULT_LEVEL_COUNT = 5;
    public static final int MAX_LEVEL_COUNT = 100;

    private static final String LEVEL_PREFIX = "invite.level";
    private static final String[] DEFAULT_NAMES = { "A", "B", "C", "D", "E" };
    private static final String[] DEFAULT_RATES = { "0.70", "0.80", "0.85", "0.90", "0.95" };
    private static final int[] DEFAULT_THRESHOLDS = { 1, 100, 300, 1000, 3000 };

    private final ISystemConfigService configService;

    public InviteLevelConfigService(ISystemConfigService configService) {
        this.configService = configService;
    }

    public int getLevelCount() {
        String configured = configService.getConfig(LEVEL_COUNT_KEY, String.valueOf(DEFAULT_LEVEL_COUNT));
        try {
            int count = Integer.parseInt(configured);
            return Math.max(0, Math.min(count, MAX_LEVEL_COUNT));
        } catch (NumberFormatException ignored) {
            return DEFAULT_LEVEL_COUNT;
        }
    }

    public int normalizeLevel(Integer level) {
        if (level == null || level <= 0) {
            return 0;
        }
        return Math.min(level, getLevelCount());
    }

    public String getLevelName(int level) {
        if (level <= 0) {
            return "普通";
        }
        return configService.getConfig(levelKey(level, ".name"), defaultName(level));
    }

    public BigDecimal getLevelRate(Integer level) {
        int normalizedLevel = normalizeLevel(level);
        if (normalizedLevel == 0) {
            return new BigDecimal(configService.getConfig("invite.earningsRate", "0.1"));
        }
        return new BigDecimal(configService.getConfig(
                levelKey(normalizedLevel, ".rate"), defaultRate(normalizedLevel)));
    }

    public int getLevelThreshold(int level) {
        return Integer.parseInt(configService.getConfig(
                levelKey(level, ".threshold"), String.valueOf(defaultThreshold(level))));
    }

    public static String levelKey(int level, String suffix) {
        return LEVEL_PREFIX + level + suffix;
    }

    private String defaultName(int level) {
        return level <= DEFAULT_NAMES.length ? DEFAULT_NAMES[level - 1] : "等级" + level;
    }

    private String defaultRate(int level) {
        return level <= DEFAULT_RATES.length ? DEFAULT_RATES[level - 1] : "0.1";
    }

    private int defaultThreshold(int level) {
        return level <= DEFAULT_THRESHOLDS.length ? DEFAULT_THRESHOLDS[level - 1] : 0;
    }
}
