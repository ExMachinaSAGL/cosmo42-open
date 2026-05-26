package ch.exmachina.cosmo42.testsupport;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class FakeClock {

    private FakeClock() {
    }

    public static Clock fixedAt(LocalDateTime instant) {
        return Clock.fixed(
                instant.atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
    }

}
