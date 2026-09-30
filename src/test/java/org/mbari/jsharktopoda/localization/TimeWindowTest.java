package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;

public class TimeWindowTest {

    @Test
    public void defaultIs200Millis() {
        assertEquals(200L, TimeWindow.DEFAULT_MILLIS);
    }

    @Test
    public void windowIsCenteredOnCurrentTime() {
        assertEquals(900L, TimeWindow.from(1000, 200));
        assertEquals(1100L, TimeWindow.to(1000, 200));
    }

    @Test
    public void fractionalTimesRoundInward() {
        assertEquals(901L, TimeWindow.from(1000.4, 200));   // ceil(900.4)
        assertEquals(1100L, TimeWindow.to(1000.4, 200));    // floor(1100.4)
    }

    @Test
    public void nearStartOfVideoMayBeNegative() {
        assertEquals(-100L, TimeWindow.from(0, 200));
        assertEquals(100L, TimeWindow.to(0, 200));
    }
}
