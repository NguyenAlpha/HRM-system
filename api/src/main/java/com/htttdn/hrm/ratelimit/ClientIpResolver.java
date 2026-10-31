package com.htttdn.hrm.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address of the browser behind the request. {@code X-Forwarded-For} is believed only when
 * the connection comes from a proxy listed in {@code rate-limit.trusted-proxies} (the web app's
 * server); anyone else could write any address into it.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedProxies = properties.trustedProxies().stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(IpAddressMatcher::new)
            .toList();
    }

    /**
     * Walks {@code X-Forwarded-For} from the right, skipping trusted proxies; the first other
     * entry is the client. Without a usable header the connecting address is the client.
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddress = withoutPort(request.getRemoteAddr());
        if (!isTrusted(remoteAddress)) {
            return remoteAddress;
        }
        List<String> forwarded = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(FORWARDED_FOR))) {
            for (String entry : header.split(",")) {
                if (!entry.isBlank()) {
                    forwarded.add(withoutPort(entry.trim()));
                }
            }
        }
        for (int index = forwarded.size() - 1; index >= 0; index--) {
            String entry = forwarded.get(index);
            if (!isIpLiteral(entry)) {
                return remoteAddress;
            }
            if (!isTrusted(entry) || index == 0) {
                return entry;
            }
        }
        return remoteAddress;
    }

    /**
     * The rate-limit key for an address: IPv6 is grouped by its /64 network, since providers hand
     * a whole /64 to one subscriber and changing the last bits would otherwise dodge the quota.
     */
    public static String bucketKey(String address) {
        if (!isIpLiteral(address)) {
            return address;
        }
        InetAddress parsed = InetAddress.ofLiteral(address);
        if (!(parsed instanceof Inet6Address)) {
            return parsed.getHostAddress();
        }
        byte[] bytes = parsed.getAddress();
        StringBuilder key = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            int value = ((bytes[group * 2] & 0xff) << 8) | (bytes[group * 2 + 1] & 0xff);
            key.append(Integer.toHexString(value)).append(':');
        }
        return key.append(":/64").toString();
    }

    /**
     * The address without a port. Azure App Service writes the client into {@code X-Forwarded-For} as
     * {@code ip:port}, and Spring Boot makes that the remote address there; with the port the key
     * changed with every connection, so each new connection got a fresh quota.
     */
    static String withoutPort(String address) {
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            return end > 0 ? address.substring(1, end) : address;
        }
        int colon = address.indexOf(':');
        // One colon: IPv4 with a port. More: a bare IPv6 address, kept as it is.
        return colon > 0 && colon == address.lastIndexOf(':') ? address.substring(0, colon) : address;
    }

    private boolean isTrusted(String address) {
        return isIpLiteral(address) && trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private static boolean isIpLiteral(String value) {
        try {
            InetAddress.ofLiteral(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
