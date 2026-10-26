package com.htttdn.hrm.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The business clock: "today" is the date in Vietnam whatever time zone the server runs in, so an
 * effective date starts at midnight Vietnam time. Inject it and call {@code LocalDate.now(clock)}
 * instead of {@code LocalDate.now()}, and {@code Instant.now(clock)} instead of {@code Instant.now()},
 * so tests can fix the time with {@code Clock.fixed}. Instants are unaffected and stay in UTC.
 */
@Configuration
public class ClockConfig {

    /** The id as a constant string, for annotations such as {@code @Scheduled(zone = ...)}. */
    public static final String BUSINESS_ZONE_ID = "Asia/Ho_Chi_Minh";
    public static final ZoneId BUSINESS_ZONE = ZoneId.of(BUSINESS_ZONE_ID);

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
