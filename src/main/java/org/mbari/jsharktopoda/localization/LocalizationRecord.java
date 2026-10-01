package org.mbari.jsharktopoda.localization;

import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable localization. x, y, width, height are unscaled video pixels.
 */
public record LocalizationRecord(UUID uuid,
                                 String concept,
                                 long elapsedTimeMillis,
                                 long durationMillis,
                                 int x,
                                 int y,
                                 int width,
                                 int height,
                                 String color) {

    public static final String DEFAULT_COLOR = "#FFFFFF";

    /**
     * vcr4j's Localization initializes its color to this, so a color omitted from a JSON message
     * arrives as this value, never null. In a partial update it therefore means "not specified".
     * (Cost: a remote app can't set a localization to exactly this color with an update.)
     */
    static final String VCR4J_UNSPECIFIED_COLOR = "#DDDDDD";

    public LocalizationRecord {
        Objects.requireNonNull(uuid, "uuid");
        concept = concept == null ? "" : concept;
        color = (color == null || color.isBlank()) ? DEFAULT_COLOR : color;
    }

    /** @return empty if the remote localization is missing required fields or has a non-positive size */
    public static Optional<LocalizationRecord> fromRemote(Localization l) {
        if (l == null
                || l.getUuid() == null
                || l.getElapsedTimeMillis() == null
                || l.getX() == null
                || l.getY() == null
                || l.getWidth() == null
                || l.getHeight() == null
                || l.getWidth() <= 0
                || l.getHeight() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new LocalizationRecord(l.getUuid(),
                l.getConcept(),
                l.getElapsedTimeMillis(),
                l.getDurationMillis() == null ? 0L : l.getDurationMillis(),
                l.getX(),
                l.getY(),
                l.getWidth(),
                l.getHeight(),
                l.getColor()));
    }

    /**
     * Apply a partial remote update: fields that are null (or a non-positive width/height)
     * keep their current value. The uuid never changes.
     */
    public LocalizationRecord mergedWith(Localization p) {
        return new LocalizationRecord(uuid,
                p.getConcept() != null ? p.getConcept() : concept,
                p.getElapsedTimeMillis() != null ? p.getElapsedTimeMillis() : elapsedTimeMillis,
                p.getDurationMillis() != null ? p.getDurationMillis() : durationMillis,
                p.getX() != null ? p.getX() : x,
                p.getY() != null ? p.getY() : y,
                p.getWidth() != null && p.getWidth() > 0 ? p.getWidth() : width,
                p.getHeight() != null && p.getHeight() > 0 ? p.getHeight() : height,
                p.getColor() != null && !p.getColor().isBlank()
                        && !VCR4J_UNSPECIFIED_COLOR.equalsIgnoreCase(p.getColor()) ? p.getColor() : color);
    }

    public Localization toRemote() {
        return new Localization(uuid, concept, elapsedTimeMillis, durationMillis, x, y, width, height, color);
    }
}
