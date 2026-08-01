package com.juxin.orin.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the originating client IP across the production reverse-proxy chain.
 * Public addresses are preferred so Docker/private proxy hops cannot mask the
 * device's Internet egress address used for geolocation.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        List<String> candidates = new ArrayList<>();
        addCandidates(candidates, request.getHeader("CF-Connecting-IP"));
        addCandidates(candidates, request.getHeader("X-Real-IP"));
        addCandidates(candidates, request.getHeader("X-Forwarded-For"));
        addCandidates(candidates, request.getHeader("Proxy-Client-IP"));
        addCandidates(candidates, request.getRemoteAddr());

        for (String candidate : candidates) {
            if (isPublicAddress(candidate)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    public static boolean isPublicAddress(String value) {
        InetAddress address = parseLiteralAddress(value);
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            // Carrier-grade NAT is not a geolocatable device address.
            return !(first == 100 && second >= 64 && second <= 127);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(address.getAddress()[0]);
            // fc00::/7 unique-local addresses are private proxy/network hops.
            return (first & 0xFE) != 0xFC;
        }
        return false;
    }

    private static void addCandidates(List<String> candidates, String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return;
        }
        for (String part : headerValue.split(",")) {
            String candidate = normalize(part);
            if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                candidates.add(candidate);
            }
        }
    }

    private static String normalize(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() > 1) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }
        if (candidate.startsWith("[")) {
            int closingBracket = candidate.indexOf(']');
            if (closingBracket > 0) {
                return candidate.substring(1, closingBracket);
            }
        }
        int colon = candidate.lastIndexOf(':');
        if (colon > 0 && candidate.indexOf(':') == colon && candidate.substring(0, colon).contains(".")) {
            String port = candidate.substring(colon + 1);
            if (port.chars().allMatch(Character::isDigit)) {
                candidate = candidate.substring(0, colon);
            }
        }
        int zone = candidate.indexOf('%');
        return zone > 0 ? candidate.substring(0, zone) : candidate;
    }

    private static InetAddress parseLiteralAddress(String value) {
        String candidate = normalize(value);
        if (candidate.isEmpty()) {
            return null;
        }
        if (candidate.contains(".")) {
            String[] parts = candidate.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            for (String part : parts) {
                try {
                    int octet = Integer.parseInt(part);
                    if (part.isEmpty() || octet < 0 || octet > 255) {
                        return null;
                    }
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        } else if (!candidate.contains(":") || !candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }
}
