package dev.omatheusmesmo.qlawkus.it.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.omatheusmesmo.qlawkus.console.management.TimeLabel;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** One instant becomes the machine value, the exact local reading and the zone the page states. */
class TimeLabelTest {

  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

  @Test
  void anInstantKeepsItsIsoValueAndReadsInTheGivenZone() {
    TimeLabel label = TimeLabel.of(Instant.parse("2026-10-01T12:00:00Z"), SAO_PAULO);

    assertEquals("2026-10-01T12:00:00Z", label.iso());
    assertEquals("2026-10-01 09:00:00", label.local());
    assertEquals("America/Sao_Paulo", label.zone());
  }

  @Test
  void aParsedIsoStringEqualsTheInstantItNames() {
    assertEquals(TimeLabel.of(Instant.parse("2026-10-01T12:00:00Z"), SAO_PAULO),
        TimeLabel.parse("2026-10-01T12:00:00Z", SAO_PAULO));
  }

  @Test
  void nothingOrAnUnreadableValueGivesNoLabel() {
    assertNull(TimeLabel.of(null, SAO_PAULO));
    assertNull(TimeLabel.parse(null, SAO_PAULO));
    assertNull(TimeLabel.parse("yesterday", SAO_PAULO));
  }
}
