package org.mbari.jsharktopoda.localization;

/**
 * How long a localization stays on screen around its elapsedTimeMillis.
 */
public final class TimeWindow {

    /**
     * Total width of the display window, centered on a localization's elapsedTimeMillis
     * (so +/- 100 ms). Used until the user sets a window in the settings dialog
     * ({@link LocalizationSettings}). 50 ms is too short to see comfortably.
     */
    public static final long DEFAULT_MILLIS = 200L;

    private TimeWindow() {
    }

    /** First integer millisecond of the window around {@code currentMillis}. */
    public static long from(double currentMillis, long windowMillis) {
        return (long) Math.ceil(currentMillis - windowMillis / 2.0);
    }

    /** Last integer millisecond of the window around {@code currentMillis}. */
    public static long to(double currentMillis, long windowMillis) {
        return (long) Math.floor(currentMillis + windowMillis / 2.0);
    }
}
