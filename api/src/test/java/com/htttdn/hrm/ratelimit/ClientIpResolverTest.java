package com.htttdn.hrm.ratelimit;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(
        new RateLimitProperties(true, List.of("127.0.0.1", "::1", "10.0.0.0/8"),
            RateLimitServiceTest.properties(1, 60).policies()));

    @Test
    void directClientCannotSpoofForwardedFor() {
        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "198.51.100.1")));
    }

    @Test
    void trustedProxySuppliesTheClientAddress() {
        assertEquals("198.51.100.1", resolver.resolve(request("127.0.0.1", "198.51.100.1")));
        assertEquals("198.51.100.1", resolver.resolve(request("0:0:0:0:0:0:0:1", "198.51.100.1")));
    }

    @Test
    void spoofedLeftEntriesAreIgnoredAndTrustedHopsSkipped() {
        assertEquals("198.51.100.1",
            resolver.resolve(request("127.0.0.1", "1.1.1.1, 198.51.100.1, 10.2.3.4")));
    }

    @Test
    void fallsBackToTheConnectionWithoutAUsableHeader() {
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", null)));
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", "not-an-ip")));
    }

    @Test
    void groupsIpv6ByNetworkAndKeepsIpv4() {
        assertEquals("2001:db8:1:2::/64", ClientIpResolver.bucketKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd"));
        assertEquals("2001:db8:1:2::/64", ClientIpResolver.bucketKey("2001:db8:1:2::1"));
        assertEquals("198.51.100.1", ClientIpResolver.bucketKey("198.51.100.1"));
        assertEquals("198.51.100.1", ClientIpResolver.bucketKey("::ffff:198.51.100.1"));
    }

    @Test
    void trustsNothingWhenNoProxyIsConfigured() {
        ClientIpResolver direct = new ClientIpResolver(
            new RateLimitProperties(true, List.of(""), Map.copyOf(RateLimitServiceTest.properties(1, 60).policies())));
        assertEquals("127.0.0.1", direct.resolve(request("127.0.0.1", "198.51.100.1")));
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
