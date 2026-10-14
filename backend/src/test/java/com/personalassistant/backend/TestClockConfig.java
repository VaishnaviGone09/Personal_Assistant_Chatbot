package com.personalassistant.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Freezes "now" at Wednesday 2026-10-14 09:00 so date-based tests are repeatable. */
@TestConfiguration
public class TestClockConfig {

    public static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    public static final LocalDate TOMORROW = TODAY.plusDays(1);

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    @Primary
    public Clock testClock() {
        return fixedClock();
    }
}
