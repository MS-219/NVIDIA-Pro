package com.juxin.orin.util;

import java.util.Locale;
import java.util.Set;

/** Semantic Orin power modes shared by settings and the edge heartbeat API. */
public final class OrinPowerMode {

    public static final String DEFAULT = "MAXN_SUPER";
    private static final Set<String> SUPPORTED = Set.of("15W", "25W", DEFAULT);

    private OrinPowerMode() {
    }

    public static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return SUPPORTED.contains(normalized) ? normalized : null;
    }
}
