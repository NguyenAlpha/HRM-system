package com.htttdn.hrm.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The business clock: "today" is the date in Vietnam whatever time zone the server runs in, so an
 * effective date starts at midnight Vietnam time. Inject it and call {@code LocalDate.now(clock)}
 * instead of {@code LocalDate.now()}. Instants are unaffected and stay in UTC.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
