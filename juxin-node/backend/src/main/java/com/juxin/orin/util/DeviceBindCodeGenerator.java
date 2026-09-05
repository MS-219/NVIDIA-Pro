package com.juxin.orin.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

public final class DeviceBindCodeGenerator {

    private static final String PREFIX = "JD";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private DeviceBindCodeGenerator() {
    }

    public static String fromSeed(String seed) {
        if (seed == null || seed.isBlank()) {
            throw new IllegalArgumentException("binding code seed is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(
                    digest.digest(seed.getBytes(StandardCharsets.UTF_8)));
            return PREFIX + hash.substring(0, 6).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String randomCode() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int index = 0; index < 6; index++) {
            code.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
