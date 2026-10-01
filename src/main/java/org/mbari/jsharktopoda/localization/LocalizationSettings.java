package org.mbari.jsharktopoda.localization;

import javafx.scene.paint.Color;

/**
 * Settings that apply to localizations created by the user in the player.
 */
public final class LocalizationSettings {

    /** Used when no default concept has been set. */
    public static final String FALLBACK_CONCEPT = "object";

    private static volatile String defaultConcept = FALLBACK_CONCEPT;

    private static volatile long timeWindowMillis = TimeWindow.DEFAULT_MILLIS;

    /** Highest fill opacity of a selected box, whatever opacity a user picks. */
    public static final double MAX_SELECTED_OPACITY = 0.35;

    /** Highest fill opacity of a box that isn't selected, whatever opacity a user picks. */
    public static final double MAX_UNSELECTED_OPACITY = 0.1;

    /** Color of a box while it is being edited (imgfx's own default, orange at 19% opacity). */
    public static final Color DEFAULT_EDIT_COLOR = Color.valueOf("#FFA50030");

    /** Highest fill opacity of a box being edited: that of the default edit color. */
    public static final double MAX_EDIT_OPACITY = DEFAULT_EDIT_COLOR.getOpacity();

    private static volatile Color editColor = DEFAULT_EDIT_COLOR;

    // null means "use the color of the localization itself"
    private static volatile Color selectedColor;
    private static volatile Color unselectedColor;

    private LocalizationSettings() {
    }

    /** @return the concept given to localizations created in the player; never blank */
    public static String getDefaultConcept() {
        return defaultConcept;
    }

    /** @param concept the new default; null or blank restores {@link #FALLBACK_CONCEPT} */
    public static void setDefaultConcept(String concept) {
        defaultConcept = (concept == null || concept.isBlank()) ? FALLBACK_CONCEPT : concept.strip();
    }

    /** @return total time, in ms, that a localization is shown around its elapsed time; always positive */
    public static long getTimeWindowMillis() {
        return timeWindowMillis;
    }

    /** @param millis the new window; zero or negative restores {@link TimeWindow#DEFAULT_MILLIS} */
    public static void setTimeWindowMillis(long millis) {
        timeWindowMillis = millis > 0 ? millis : TimeWindow.DEFAULT_MILLIS;
    }

    /** @return the color forced on selected boxes, or null to use each localization's own color */
    public static Color getSelectedColor() {
        return selectedColor;
    }

    /** @param color null restores each localization's own color */
    public static void setSelectedColor(Color color) {
        selectedColor = color;
    }

    /** @return the color forced on boxes that aren't selected, or null to use each localization's own color */
    public static Color getUnselectedColor() {
        return unselectedColor;
    }

    /** @param color null restores each localization's own color */
    public static void setUnselectedColor(Color color) {
        unselectedColor = color;
    }

    /** @return the color as text that {@link Color#web(String)} reads back, or null for null */
    public static String toText(Color color) {
        return color == null ? null : color.toString();
    }

    /** @return the color in the text, or null if it's null or unreadable */
    public static Color fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Color.web(text);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @return the color of a box while it is being edited; never null */
    public static Color getEditColor() {
        return editColor;
    }

    /** @param color null restores {@link #DEFAULT_EDIT_COLOR} */
    public static void setEditColor(Color color) {
        editColor = color == null ? DEFAULT_EDIT_COLOR : color;
    }

    /** @return the edit color with its opacity held to {@link #MAX_EDIT_OPACITY} */
    public static Color getClampedEditColor() {
        var c = editColor;
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(c.getOpacity(), MAX_EDIT_OPACITY));
    }
}
