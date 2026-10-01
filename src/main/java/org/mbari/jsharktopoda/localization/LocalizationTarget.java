package org.mbari.jsharktopoda.localization;

import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.List;
import java.util.UUID;

/**
 * The per-video receiver of localization commands that originate from the remote app.
 * Called on the JavaFX application thread.
 */
public interface LocalizationTarget {

    /** Already validated. A uuid that exists is replaced. */
    void add(List<LocalizationRecord> records);

    /** Partial remote updates; omitted fields keep their value; unknown uuids are ignored. */
    void update(List<Localization> partials);

    void remove(List<UUID> uuids);

    void clear();

    /** Replaces the selection; unknown uuids are ignored. */
    void select(List<UUID> uuids);
}
