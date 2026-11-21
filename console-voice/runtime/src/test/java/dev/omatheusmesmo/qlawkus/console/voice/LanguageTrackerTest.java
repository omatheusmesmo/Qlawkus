package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguageTrackerTest {

    @Test
    void noLanguageIsStableBeforeAnyUtterance() {
        assertTrue(new LanguageTracker().stable().isEmpty());
    }

    @Test
    void oneShortUtteranceNeverSetsTheSessionLanguage() {
        LanguageTracker tracker = new LanguageTracker();

        tracker.observe("pt");

        assertTrue(tracker.stable().isEmpty());
    }

    @Test
    void twoAgreeingUtterancesMakeTheLanguageStable() {
        LanguageTracker tracker = new LanguageTracker();

        tracker.observe("pt");
        tracker.observe("pt");

        assertEquals(Optional.of("pt"), tracker.stable());
    }

    @Test
    void theMajorityOfTheRecentUtterancesWinsOverASingleMisdetection() {
        LanguageTracker tracker = new LanguageTracker();
        tracker.observe("pt");
        tracker.observe("pt");
        tracker.observe("en");
        tracker.observe("pt");
        tracker.observe("en");

        assertEquals(Optional.of("pt"), tracker.stable());
    }

    @Test
    void oldUtterancesFallOutOfTheWindowSoAGenuineSwitchIsFollowed() {
        LanguageTracker tracker = new LanguageTracker();
        for (int i = 0; i < 5; i++) {
            tracker.observe("en");
        }
        tracker.observe("pt");
        tracker.observe("pt");
        assertEquals(Optional.of("en"), tracker.stable());

        tracker.observe("pt");
        assertEquals(Optional.of("pt"), tracker.stable());
    }

    @Test
    void aTieGoesToTheMostRecentLanguage() {
        LanguageTracker tracker = new LanguageTracker();
        tracker.observe("pt");
        tracker.observe("en");
        tracker.observe("pt");
        tracker.observe("en");

        assertEquals(Optional.of("en"), tracker.stable());
    }
}
