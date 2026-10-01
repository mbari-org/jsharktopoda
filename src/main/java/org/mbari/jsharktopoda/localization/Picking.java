package org.mbari.jsharktopoda.localization;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Chooses which localization a click refers to. Coordinates are unscaled video pixels.
 */
public final class Picking {

    private Picking() {
    }

    public static Optional<UUID> pick(Collection<LocalizationRecord> candidates, double px, double py) {
        LocalizationRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        long bestArea = Long.MAX_VALUE;
        for (var r : candidates) {
            if (px < r.x() || px > r.x() + r.width() || py < r.y() || py > r.y() + r.height()) {
                continue;
            }
            double distance = Math.min(
                    Math.min(px - r.x(), r.x() + r.width() - px),
                    Math.min(py - r.y(), r.y() + r.height() - py));
            long area = (long) r.width() * r.height();
            if (distance < bestDistance || (distance == bestDistance && area < bestArea)) {
                best = r;
                bestDistance = distance;
                bestArea = area;
            }
        }
        return Optional.ofNullable(best).map(LocalizationRecord::uuid);
    }
}
