package org.mbari.jsharktopoda.localization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * All localizations for one video, indexed by elapsed time, plus the selection.
 * Not thread safe: use from the JavaFX application thread only.
 */
public class LocalizationStore {

    private final Map<UUID, LocalizationRecord> records = new HashMap<>();
    private final NavigableMap<Long, Set<UUID>> byTime = new TreeMap<>();
    private final Set<UUID> selected = new LinkedHashSet<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** @return a runnable that removes the listener */
    public Runnable addListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void fire() {
        for (var l : listeners) {
            l.run();
        }
    }

    private void index(LocalizationRecord r) {
        byTime.computeIfAbsent(r.elapsedTimeMillis(), k -> new LinkedHashSet<>()).add(r.uuid());
    }

    private void unindex(LocalizationRecord r) {
        var ids = byTime.get(r.elapsedTimeMillis());
        if (ids != null) {
            ids.remove(r.uuid());
            if (ids.isEmpty()) {
                byTime.remove(r.elapsedTimeMillis());
            }
        }
    }

    private void putQuietly(LocalizationRecord r) {
        var old = records.put(r.uuid(), r);
        if (old != null) {
            unindex(old);
        }
        index(r);
    }

    /** Add, or replace if the uuid already exists. */
    public void put(LocalizationRecord r) {
        putQuietly(r);
        fire();
    }

    public void putAll(Collection<LocalizationRecord> rs) {
        if (rs.isEmpty()) {
            return;
        }
        rs.forEach(this::putQuietly);
        fire();
    }

    /** Replace an existing record. @return false (and do nothing) if the uuid is unknown */
    public boolean update(LocalizationRecord r) {
        return updateAll(List.of(r)) == 1;
    }

    /** @return the number of records that existed and were replaced */
    public int updateAll(Collection<LocalizationRecord> rs) {
        int n = 0;
        for (var r : rs) {
            if (records.containsKey(r.uuid())) {
                putQuietly(r);
                n++;
            }
        }
        if (n > 0) {
            fire();
        }
        return n;
    }

    /** @return the uuids that existed and were removed */
    public List<UUID> remove(Collection<UUID> uuids) {
        var removed = new ArrayList<UUID>();
        for (var id : uuids) {
            var old = records.remove(id);
            if (old != null) {
                unindex(old);
                removed.add(id);
            }
        }
        if (!removed.isEmpty()) {
            selected.removeAll(removed);
            fire();
        }
        return removed;
    }

    public void clear() {
        if (records.isEmpty() && selected.isEmpty()) {
            return;
        }
        records.clear();
        byTime.clear();
        selected.clear();
        fire();
    }

    /** Replace the selection. Unknown uuids are ignored. */
    public void select(Collection<UUID> uuids) {
        var next = new LinkedHashSet<UUID>();
        for (var id : uuids) {
            if (records.containsKey(id)) {
                next.add(id);
            }
        }
        if (!next.equals(selected)) {
            selected.clear();
            selected.addAll(next);
            fire();
        }
    }

    /** @return a copy, in selection order */
    public List<UUID> selected() {
        return new ArrayList<>(selected);
    }

    public boolean isSelected(UUID uuid) {
        return selected.contains(uuid);
    }

    public Optional<LocalizationRecord> get(UUID uuid) {
        return Optional.ofNullable(records.get(uuid));
    }

    public int size() {
        return records.size();
    }

    /** Records whose elapsedTimeMillis is in [fromMillis, toMillis] (inclusive). */
    public List<LocalizationRecord> query(long fromMillis, long toMillis) {
        if (fromMillis > toMillis) {
            return List.of();
        }
        var out = new ArrayList<LocalizationRecord>();
        for (var ids : byTime.subMap(fromMillis, true, toMillis, true).values()) {
            for (var id : ids) {
                out.add(records.get(id));
            }
        }
        return out;
    }
}
