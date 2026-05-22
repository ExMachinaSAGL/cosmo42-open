package ch.exmachina.cosmo42.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {

    @Test
    void systemClockProvidesDefaultZoneClock() {
        TimeConfig config = new TimeConfig();

        Clock clock = config.systemClock();

        assertThat(clock).isNotNull();
        assertThat(clock.getZone()).isEqualTo(Clock.systemDefaultZone().getZone());
    }
}