# Localizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render, create, select, move/resize and delete rectangular localizations on top of jsharktopoda's video, driven by (and reported to) a remote app over the UDP protocol already implemented in vcr4j-remote.

**Architecture:** A per-video `LocalizationStore` (pure Java, time-indexed, FX-thread confined) is the single source of truth. A per-window `LocalizationOverlay` materializes only the records inside the current time window as imgfx `Localization` nodes on top of imgfx's autoscaling `MediaPaneController`. A `LocalizationCommandRouter` feeds incoming vcr4j commands into the store via a per-video `VideoLocalizations` target; a `RemoteNotifier` sends user-originated changes back to the remote app. Remote-originated changes never echo.

**Tech Stack:** Java 27, JavaFX 27, Gradle (Kotlin DSL), JUnit 4, RxJava 3, vcr4j-remote 5.3.8, imgfx 0.0.20, JPMS (`module-info.java`).

**Spec:** `docs/superpowers/specs/2026-09-30-localizations-design.md` (read it first). Protocol/UI requirements: `/Users/brian/workspace/github/mbari-org/Sharktopoda/Requirements/` (`UI.md`, `UDP_Remote_Protocol.md`).

## Global Constraints

- Localization coordinates are unscaled video pixels, origin upper-left, +Y down.
- Must handle tens of thousands of localizations per video: the scene graph holds only records inside the time window.
- **Time window: 200 ms default, a constant** (`TimeWindow.DEFAULT_MILLIS`), centered on `elapsedTimeMillis` (±100 ms); it must become a Preference later and the constant's comment says so. `durationMillis` is stored but ignored for display.
- Delete shortcut: Cmd-Delete on macOS, Ctrl-Delete on Windows/Linux (implemented with JavaFX `isShortcutDown()`; the macOS "delete" key is `KeyCode.BACK_SPACE`, so accept `DELETE` and `BACK_SPACE`).
- New-localization defaults until Preferences exist: concept `""`, color `#FFFFFF`.
- Commands from the remote app never pause the video and are never echoed back. User gestures on the video pause playback.
- `LocalizationStore` and everything that touches it run on the JavaFX application thread only (no locking). `SharkVideoController.hasVideo` runs on the UDP thread and must only use concurrent structures.
- Unknown video → vcr4j already replies `failed` / `"No video for uuid"` via `VideoController.hasVideo` (vcr4j 5.3.8). `update` for an unknown localization is ignored; `add` for an existing UUID replaces it.
- Any new JDK/JavaFX module must be added to both `build.gradle.kts` and `src/main/java/module-info.java` (the module is `open module jsharktopoda`).
- The repo has no UI test infrastructure and none is added; JavaFX-dependent classes (overlay, stage wiring) are verified manually. Logic that can be pure Java (store, record, router, notifier, picking, time window) is unit tested with JUnit 4 under `src/test/java/org/mbari/jsharktopoda/localization/`.
- Work on branch `feature/localizations`. Stage files explicitly (`git add <paths>`); never `git add -A` — the working tree has unrelated gradle-wrapper changes that are not part of this work.
- Commit messages end with: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`

## Review Focus

Inputs the spec implies but the obvious tests would miss, most likely first. Each has a test in the owning task.

1. **Remote `update` with omitted fields** (e.g. only `x`/`y`, or no `color`) must keep the stored values, not null them or reset the color to white. → Task 2 (`LocalizationRecordTest.mergedWith*`), Task 5 (`VideoLocalizationsTest.updateKeepsOmittedFields`).
2. **Malformed localizations inside an `add`** (missing `uuid`, missing/zero `width`/`height`, `null` list) must be dropped without throwing, and the valid ones in the same message still applied. → Task 2, Task 3 (`routesOnlyValidRecordsOnAdd`, `nullPayloadListsAreIgnored`).
3. **Commands for a video that is still loading** must be applied when the window opens (the store exists before the overlay). → Task 5 (`commandsBeforeAttachAreKept`), Task 8 manual step.
4. **Resizing the window while a box is selected/being edited** must keep boxes aligned with the video and not corrupt stored coordinates. → Task 8 (rebuild on scale change), Task 9 manual step.
5. **`add` for an existing UUID / `select` or `remove` of UUIDs that don't exist**: no duplicate nodes, no exceptions, selection never contains unknown ids. → Task 2 (`putReplacesAndReindexes`, `selectReplacesAndIgnoresUnknown`, `removeDropsFromSelectionAndIgnoresUnknown`), Task 5.

---

## File Structure

Create (all under `src/main/java/org/mbari/jsharktopoda/localization/` unless noted):

| File | Responsibility |
|---|---|
| `LocalizationRecord.java` | Immutable localization value + conversion to/from vcr4j `Localization` (validation, partial-update merge). |
| `LocalizationStore.java` | Per-video map + time index + selection set + change listeners. No JavaFX. |
| `LocalizationTarget.java` | Interface the router calls (add/update/remove/clear/select). |
| `LocalizationCommandRouter.java` | vcr4j command observable → `LocalizationTarget`, on an injected `Executor`. |
| `RemoteNotifier.java` | Builds and sends `add/update/remove/select` commands to the remote app off the FX thread. |
| `VideoLocalizations.java` | Per-video composition: store + `LocalizationTarget` impl + overlay attach. |
| `Picking.java`, `TimeWindow.java`, `DeleteShortcut.java` | Small pure helpers (hit-test, time window math, key rule). |
| `LocalizationOverlay.java` | imgfx-based rendering, selection, editing, creation, deletion (JavaFX). |
| `scripts/udp_send.py`, `scripts/udp_listen.py` | Manual-verification helpers (repo root). |
| Tests | `src/test/java/org/mbari/jsharktopoda/localization/*Test.java` |

Modify: `build.gradle.kts`, `src/main/java/module-info.java`, `MoviePane.fxml`, `MoviePaneController.java`, `MovieStageController.java`, `SharkVideoController.java`, `JSharktopoda.java`, `README.md`, `CLAUDE.md`.

---

### Task 1: Toolchain and imgfx dependency

imgfx 0.0.20 is compiled for Java 26/27 class files (major 70/71), so jsharktopoda must build with JDK 27 and JavaFX 27 (JDK 27 is installed at `/Library/Java/JavaVirtualMachines/jdk-27.jdk`).

**Files:**
- Modify: `build.gradle.kts`, `src/main/java/module-info.java`, `README.md` (Prerequisites), `CLAUDE.md` (Commands intro)

**Interfaces:**
- Produces: `org.mbari.imgfx.*` types importable from `org.mbari.jsharktopoda` code.

- [ ] **Step 1: Create the branch and commit already-finished prerequisite work**

```bash
cd /Users/brian/workspace/github/mbari-org/jsharktopoda
git checkout -b feature/localizations
git add src/main/java/module-info.java CLAUDE.md docs/superpowers/specs docs/superpowers/plans
git commit -m "docs: add CLAUDE.md, localization spec and plan; require jdk.unsupported for Gson

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
(`build.gradle.kts` has the vcr4j 5.3.8 bump and is committed with Step 5 below.)

- [ ] **Step 2: Edit `build.gradle.kts`**

Change the toolchain, JavaFX version, add imgfx, align ikonli with imgfx's 12.4.0:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

javafx {
    version = "27"
    // modules list unchanged
}
```
and in `dependencies { ... }`:
```kotlin
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-material-pack:12.4.0")
    implementation("org.mbari.imgfx:imgfx:0.0.20")
```
(replace the two existing 12.3.1 ikonli lines).

- [ ] **Step 3: Edit `module-info.java`**

Add after `requires org.mbari.jcommons;`:
```java
  requires org.mbari.imgfx;
```

- [ ] **Step 4: Verify it compiles, tests pass, and the app starts**

Run: `./gradlew clean compileJava test -q`
Expected: no errors (warnings OK).

Run: `./gradlew run` and confirm the toolbar window appears (Open File works), then quit.
Expected: no `ModuleNotFoundException` / `UnsupportedClassVersionError` / `LayerInstantiationException`.
If a JavaFX version-mismatch error appears (`25` vs `27` classes on the module path), run `./gradlew dependencies --configuration runtimeClasspath | grep -i javafx` and exclude/align so only 27 is present.

- [ ] **Step 5: Update docs and commit**

In `README.md` change "**Java 25**" to "**Java 27**". In `CLAUDE.md` change "Requires Java 25" to "Requires Java 27" and add to the "Build/module notes" list: "imgfx (`org.mbari.imgfx:imgfx`) provides the autoscaling video pane and rectangle drawing/editing used for localizations; it is compiled for Java 27, which is why the toolchain is 27."

```bash
git add build.gradle.kts src/main/java/module-info.java README.md CLAUDE.md
git commit -m "build: move to JDK/JavaFX 27, add imgfx, vcr4j 5.3.8

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `LocalizationRecord` and `LocalizationStore`

**Files:**
- Create: `src/main/java/org/mbari/jsharktopoda/localization/LocalizationRecord.java`
- Create: `src/main/java/org/mbari/jsharktopoda/localization/LocalizationStore.java`
- Test: `src/test/java/org/mbari/jsharktopoda/localization/LocalizationRecordTest.java`
- Test: `src/test/java/org/mbari/jsharktopoda/localization/LocalizationStoreTest.java`

**Interfaces:**
- Produces:
  - `record LocalizationRecord(UUID uuid, String concept, long elapsedTimeMillis, long durationMillis, int x, int y, int width, int height, String color)`; constants `DEFAULT_COLOR = "#FFFFFF"`, `DEFAULT_CONCEPT = ""`; `static Optional<LocalizationRecord> fromRemote(Localization)`; `LocalizationRecord mergedWith(Localization partial)`; `Localization toRemote()`.
  - `LocalizationStore`: `void put(LocalizationRecord)`, `void putAll(Collection<LocalizationRecord>)`, `boolean update(LocalizationRecord)`, `int updateAll(Collection<LocalizationRecord>)`, `List<UUID> remove(Collection<UUID>)`, `void clear()`, `void select(Collection<UUID>)`, `List<UUID> selected()`, `boolean isSelected(UUID)`, `Optional<LocalizationRecord> get(UUID)`, `int size()`, `List<LocalizationRecord> query(long fromMillis, long toMillis)` (inclusive), `Runnable addListener(Runnable)` (returns a remover). Listeners fire once per mutating call, and only if something changed.
  - `Localization` here is `org.mbari.vcr4j.remote.control.commands.localization.Localization` (nullable `Long`/`Integer` fields).

- [ ] **Step 1: Write the failing record test**

```java
package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.UUID;

public class LocalizationRecordTest {

    private final UUID id = UUID.randomUUID();

    private Localization full() {
        return new Localization(id, "sponge", 1000L, 25L, 10, 20, 30, 40, "#FF0000");
    }

    @Test
    public void fromRemoteCopiesAllFields() {
        var r = LocalizationRecord.fromRemote(full()).orElseThrow();
        assertEquals(new LocalizationRecord(id, "sponge", 1000, 25, 10, 20, 30, 40, "#FF0000"), r);
    }

    @Test
    public void fromRemoteAppliesDefaults() {
        var l = new Localization(id, null, 5L, null, 1, 2, 3, 4, null);
        var r = LocalizationRecord.fromRemote(l).orElseThrow();
        assertEquals("", r.concept());
        assertEquals(0L, r.durationMillis());
        assertEquals(LocalizationRecord.DEFAULT_COLOR, r.color());
    }

    @Test
    public void fromRemoteRejectsMalformed() {
        assertTrue(LocalizationRecord.fromRemote(null).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(null, "c", 1L, 0L, 1, 2, 3, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", null, 0L, 1, 2, 3, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", 1L, 0L, null, 2, 3, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", 1L, 0L, 1, 2, 0, 4, null)).isEmpty());
        assertTrue(LocalizationRecord.fromRemote(new Localization(id, "c", 1L, 0L, 1, 2, 3, -1, null)).isEmpty());
    }

    @Test
    public void mergedWithKeepsExistingForOmittedFields() {
        var base = LocalizationRecord.fromRemote(full()).orElseThrow();
        var partial = new Localization(id, null, null, null, 99, null, null, null, null);
        var merged = base.mergedWith(partial);
        assertEquals(99, merged.x());
        assertEquals(20, merged.y());
        assertEquals(30, merged.width());
        assertEquals("sponge", merged.concept());
        assertEquals("#FF0000", merged.color());
        assertEquals(1000L, merged.elapsedTimeMillis());
    }

    @Test
    public void mergedWithAppliesProvidedFieldsAndIgnoresNonPositiveSize() {
        var base = LocalizationRecord.fromRemote(full()).orElseThrow();
        var partial = new Localization(id, "coral", 2000L, 7L, null, null, 0, 55, "#00FF00");
        var merged = base.mergedWith(partial);
        assertEquals("coral", merged.concept());
        assertEquals(2000L, merged.elapsedTimeMillis());
        assertEquals(7L, merged.durationMillis());
        assertEquals(30, merged.width());   // 0 is invalid, keep old
        assertEquals(55, merged.height());
        assertEquals("#00FF00", merged.color());
    }

    @Test
    public void toRemoteRoundTrips() {
        var r = LocalizationRecord.fromRemote(full()).orElseThrow();
        assertEquals(r, LocalizationRecord.fromRemote(r.toRemote()).orElseThrow());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "org.mbari.jsharktopoda.localization.LocalizationRecordTest" -q`
Expected: compilation FAIL (`LocalizationRecord` does not exist).

- [ ] **Step 3: Implement `LocalizationRecord`**

```java
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

    /** Concept for user-created localizations. Preferences will supply this later. */
    public static final String DEFAULT_CONCEPT = "";

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
                p.getColor() != null && !p.getColor().isBlank() ? p.getColor() : color);
    }

    public Localization toRemote() {
        return new Localization(uuid, concept, elapsedTimeMillis, durationMillis, x, y, width, height, color);
    }
}
```

- [ ] **Step 4: Run the record tests, expect PASS**

Run: `./gradlew test --tests "org.mbari.jsharktopoda.localization.LocalizationRecordTest" -q`

- [ ] **Step 5: Write the failing store test**

```java
package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalizationStoreTest {

    private final LocalizationStore store = new LocalizationStore();

    private static LocalizationRecord rec(UUID id, long t) {
        return new LocalizationRecord(id, "c", t, 0, 10, 20, 30, 40, "#FFFFFF");
    }

    @Test
    public void putAndGet() {
        var id = UUID.randomUUID();
        store.put(rec(id, 100));
        assertEquals(100, store.get(id).orElseThrow().elapsedTimeMillis());
        assertEquals(1, store.size());
    }

    @Test
    public void putReplacesAndReindexes() {
        var id = UUID.randomUUID();
        store.put(rec(id, 100));
        store.put(rec(id, 500));
        assertEquals(1, store.size());
        assertTrue(store.query(0, 200).isEmpty());
        assertEquals(1, store.query(400, 600).size());
    }

    @Test
    public void updateIgnoresUnknown() {
        var id = UUID.randomUUID();
        assertFalse(store.update(rec(id, 1)));
        assertEquals(0, store.size());
        store.put(rec(id, 1));
        assertTrue(store.update(rec(id, 2)));
        assertEquals(2, store.get(id).orElseThrow().elapsedTimeMillis());
    }

    @Test
    public void queryIsInclusiveAndHandlesSharedTimes() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        store.put(rec(a, 100));
        store.put(rec(b, 100));
        store.put(rec(c, 200));
        assertEquals(2, store.query(100, 100).size());
        assertEquals(3, store.query(100, 200).size());
        assertEquals(1, store.query(101, 200).size());
        assertTrue(store.query(300, 400).isEmpty());
        assertTrue(store.query(200, 100).isEmpty());
    }

    @Test
    public void removeDropsFromSelectionAndIgnoresUnknown() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        store.put(rec(a, 1));
        store.put(rec(b, 2));
        store.select(List.of(a, b));
        var removed = store.remove(List.of(a, UUID.randomUUID()));
        assertEquals(List.of(a), removed);
        assertEquals(List.of(b), store.selected());
        assertTrue(store.query(0, 10).stream().noneMatch(r -> r.uuid().equals(a)));
    }

    @Test
    public void selectReplacesAndIgnoresUnknown() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        store.put(rec(a, 1));
        store.put(rec(b, 2));
        store.select(List.of(a, UUID.randomUUID()));
        assertEquals(List.of(a), store.selected());
        store.select(List.of(b));
        assertEquals(List.of(b), store.selected());
        assertTrue(store.isSelected(b));
        assertFalse(store.isSelected(a));
        store.select(List.of());
        assertTrue(store.selected().isEmpty());
    }

    @Test
    public void clearEmptiesEverything() {
        var a = UUID.randomUUID();
        store.put(rec(a, 1));
        store.select(List.of(a));
        store.clear();
        assertEquals(0, store.size());
        assertTrue(store.selected().isEmpty());
        assertTrue(store.query(0, 10).isEmpty());
    }

    @Test
    public void listenerFiresOncePerBatchAndNotOnNoOps() {
        var count = new AtomicInteger();
        var remove = store.addListener(count::incrementAndGet);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        store.putAll(List.of(rec(a, 1), rec(b, 2)));
        assertEquals(1, count.get());
        store.select(List.of(a));
        assertEquals(2, count.get());
        store.select(List.of(a));            // no change
        store.remove(List.of(UUID.randomUUID()));   // nothing removed
        store.updateAll(List.of(rec(UUID.randomUUID(), 3)));   // unknown
        store.putAll(List.of());
        assertEquals(2, count.get());
        store.clear();
        assertEquals(3, count.get());
        store.clear();                        // already empty
        assertEquals(3, count.get());
        remove.run();
        store.put(rec(a, 1));
        assertEquals(3, count.get());
    }

    @Test
    public void handlesFiftyThousandRecords() {
        var records = new ArrayList<LocalizationRecord>();
        for (int i = 0; i < 50_000; i++) {
            records.add(rec(UUID.randomUUID(), i));
        }
        var start = System.nanoTime();
        store.putAll(records);
        for (int i = 0; i < 1000; i++) {
            assertEquals(201, store.query(i * 10L, i * 10L + 200).size());
        }
        var millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(50_000, store.size());
        assertTrue("took " + millis + " ms", millis < 5_000);
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew test --tests "org.mbari.jsharktopoda.localization.LocalizationStoreTest" -q`
Expected: compilation FAIL (`LocalizationStore` does not exist).

- [ ] **Step 7: Implement `LocalizationStore`**

```java
package org.mbari.jsharktopoda.localization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
```
(Remove the unused `HashSet` import if the compiler warns.)

- [ ] **Step 8: Run both test classes, expect PASS**

Run: `./gradlew test --tests "org.mbari.jsharktopoda.localization.*" -q`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/mbari/jsharktopoda/localization src/test/java/org/mbari/jsharktopoda/localization
git commit -m "feat: LocalizationRecord and time-indexed LocalizationStore

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `LocalizationTarget` and `LocalizationCommandRouter`

**Files:**
- Create: `.../localization/LocalizationTarget.java`
- Create: `.../localization/LocalizationCommandRouter.java`
- Test: `.../localization/LocalizationCommandRouterTest.java`

**Interfaces:**
- Consumes: `LocalizationRecord.fromRemote` (Task 2).
- Produces:
  - `interface LocalizationTarget { void add(List<LocalizationRecord>); void update(List<Localization> partials); void remove(List<UUID>); void clear(); void select(List<UUID>); }`
  - `LocalizationCommandRouter(Observable<LocalizationsCmd<?, ?>> commands, Function<UUID, Optional<LocalizationTarget>> lookup, Executor executor)` implements `AutoCloseable` (`close()` unsubscribes). The lookup **and** the target call both run on `executor` (the FX thread in production), so ordering relative to `open()` is preserved.

- [ ] **Step 1: Write the failing test**

```java
package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.After;
import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.localization.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LocalizationCommandRouterTest {

    private static class FakeTarget implements LocalizationTarget {
        final List<String> calls = new ArrayList<>();
        final List<List<LocalizationRecord>> added = new ArrayList<>();
        final List<List<Localization>> updated = new ArrayList<>();
        boolean explode = false;

        private void maybeExplode() {
            if (explode) {
                throw new IllegalStateException("boom");
            }
        }

        @Override public void add(List<LocalizationRecord> r) { calls.add("add"); added.add(r); maybeExplode(); }
        @Override public void update(List<Localization> p) { calls.add("update"); updated.add(p); }
        @Override public void remove(List<UUID> u) { calls.add("remove:" + u.size()); }
        @Override public void clear() { calls.add("clear"); }
        @Override public void select(List<UUID> u) { calls.add("select:" + u.size()); }
    }

    private final PublishSubject<LocalizationsCmd<?, ?>> subject = PublishSubject.create();
    private final UUID video = UUID.randomUUID();
    private final UUID otherVideo = UUID.randomUUID();
    private final FakeTarget target = new FakeTarget();
    private final FakeTarget otherTarget = new FakeTarget();
    private final LocalizationCommandRouter router = new LocalizationCommandRouter(
            subject,
            id -> id.equals(video) ? Optional.of(target)
                    : id.equals(otherVideo) ? Optional.of(otherTarget) : Optional.empty(),
            Runnable::run);

    @After
    public void tearDown() {
        router.close();
    }

    private static Localization loc(UUID id) {
        return new Localization(id, "c", 10L, 0L, 1, 2, 3, 4, null);
    }

    @Test
    public void routesOnlyValidRecordsOnAdd() {
        var good = UUID.randomUUID();
        var bad = new Localization(null, "c", 10L, 0L, 1, 2, 3, 4, null);
        var zero = new Localization(UUID.randomUUID(), "c", 10L, 0L, 1, 2, 0, 4, null);
        subject.onNext(new AddLocalizationsCmd(video, List.of(loc(good), bad, zero)));
        assertEquals(1, target.added.size());
        assertEquals(1, target.added.get(0).size());
        assertEquals(good, target.added.get(0).get(0).uuid());
    }

    @Test
    public void nullPayloadListsAreIgnored() {
        subject.onNext(new AddLocalizationsCmd(video, null));
        subject.onNext(new UpdateLocalizationsCmd(video, null));
        subject.onNext(new RemoveLocalizationsCmd(video, null));
        subject.onNext(new SelectLocalizationsCmd(video, null));
        assertEquals(List.of("add", "update", "remove:0", "select:0"), target.calls);
        assertTrue(target.added.get(0).isEmpty());
    }

    @Test
    public void routesUpdateRemoveSelectClear() {
        var id = UUID.randomUUID();
        subject.onNext(new UpdateLocalizationsCmd(video, List.of(loc(id))));
        subject.onNext(new RemoveLocalizationsCmd(video, List.of(id, UUID.randomUUID())));
        subject.onNext(new SelectLocalizationsCmd(video, List.of(id)));
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertEquals(List.of("update", "remove:2", "select:1", "clear"), target.calls);
        assertEquals(id, target.updated.get(0).get(0).getUuid());
    }

    @Test
    public void routesByVideoUuid() {
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(otherVideo)));
        assertTrue(target.calls.isEmpty());
        assertEquals(List.of("clear"), otherTarget.calls);
    }

    @Test
    public void unknownVideoIsIgnoredAndStreamContinues() {
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(UUID.randomUUID())));
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertEquals(List.of("clear"), target.calls);
    }

    @Test
    public void targetFailureDoesNotKillTheStream() {
        target.explode = true;
        subject.onNext(new AddLocalizationsCmd(video, List.of(loc(UUID.randomUUID()))));
        target.explode = false;
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertEquals(List.of("add", "clear"), target.calls);
    }

    @Test
    public void closeStopsRouting() {
        router.close();
        subject.onNext(new ClearLocalizationsCmd(new ClearLocalizationsCmd.Request(video)));
        assertTrue(target.calls.isEmpty());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "org.mbari.jsharktopoda.localization.LocalizationCommandRouterTest" -q`
Expected: compilation FAIL.

- [ ] **Step 3: Implement `LocalizationTarget`**

```java
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
```

- [ ] **Step 4: Implement `LocalizationCommandRouter`**

```java
package org.mbari.jsharktopoda.localization;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.mbari.vcr4j.remote.control.commands.localization.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Routes localization commands received by vcr4j to the {@link LocalizationTarget} of the
 * addressed video. vcr4j has already replied to the remote app; this only applies the change.
 */
public class LocalizationCommandRouter implements AutoCloseable {

    private static final System.Logger log = System.getLogger(LocalizationCommandRouter.class.getName());

    private final Function<UUID, Optional<LocalizationTarget>> lookup;
    private final Executor executor;
    private final Disposable subscription;

    public LocalizationCommandRouter(Observable<LocalizationsCmd<?, ?>> commands,
                                     Function<UUID, Optional<LocalizationTarget>> lookup,
                                     Executor executor) {
        this.lookup = lookup;
        this.executor = executor;
        this.subscription = commands.subscribe(this::route,
                e -> log.log(System.Logger.Level.ERROR, "Localization command stream failed", e));
    }

    private void route(LocalizationsCmd<?, ?> cmd) {
        var videoUuid = cmd.getValue().getUuid();
        executor.execute(() -> lookup.apply(videoUuid).ifPresentOrElse(
                target -> apply(target, cmd),
                () -> log.log(System.Logger.Level.DEBUG, () -> "No video " + videoUuid + " for " + cmd.getName())));
    }

    private void apply(LocalizationTarget target, LocalizationsCmd<?, ?> cmd) {
        try {
            switch (cmd) {
                case AddLocalizationsCmd c -> target.add(nn(c.getValue().getLocalizations())
                        .stream()
                        .map(LocalizationRecord::fromRemote)
                        .flatMap(Optional::stream)
                        .toList());
                case UpdateLocalizationsCmd c -> target.update(nn(c.getValue().getLocalizations()));
                case RemoveLocalizationsCmd c -> target.remove(nn(c.getValue().getLocalizations()));
                case SelectLocalizationsCmd c -> target.select(nn(c.getValue().getLocalizations()));
                case ClearLocalizationsCmd c -> target.clear();
                default -> log.log(System.Logger.Level.WARNING, () -> "Unhandled command " + cmd.getName());
            }
        }
        catch (RuntimeException e) {
            log.log(System.Logger.Level.ERROR, "Failed to apply " + cmd.getName(), e);
        }
    }

    private static <T> List<T> nn(List<T> list) {
        return list == null ? List.of() : list;
    }

    @Override
    public void close() {
        subscription.dispose();
    }
}
```

- [ ] **Step 5: Run the test, expect PASS**

Run: `./gradlew test --tests "org.mbari.jsharktopoda.localization.LocalizationCommandRouterTest" -q`
If a `switch` pattern compile error mentions dominance/unchecked, keep `default` last and the case order as written.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/mbari/jsharktopoda/localization src/test/java/org/mbari/jsharktopoda/localization
git commit -m "feat: route vcr4j localization commands to per-video targets

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `RemoteNotifier`

**Files:**
- Create: `.../localization/RemoteNotifier.java`
- Test: `.../localization/RemoteNotifierTest.java`

**Interfaces:**
- Consumes: `LocalizationRecord.toRemote` (Task 2).
- Produces: `RemoteNotifier(Sender sender, Executor executor)`; nested `@FunctionalInterface interface Sender { boolean send(RCommand<?, ?> command); }` (false = no connection); `static RemoteNotifier forConnection(Supplier<Optional<RVideoIO>> connection)` (daemon single-thread executor); `void added(UUID videoUuid, List<LocalizationRecord>)`, `void updated(UUID, List<LocalizationRecord>)`, `void removed(UUID, List<UUID>)`, `void selected(UUID, List<UUID>)`. Never throws; a missing connection or a failing send is logged and the message dropped.

- [ ] **Step 1: Write the failing test**

```java
package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.RCommand;
import org.mbari.vcr4j.remote.control.commands.localization.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RemoteNotifierTest {

    private final List<RCommand<?, ?>> sent = new ArrayList<>();
    private final UUID video = UUID.randomUUID();
    private final LocalizationRecord rec =
            new LocalizationRecord(UUID.randomUUID(), "c", 10, 0, 1, 2, 3, 4, "#FFFFFF");

    private RemoteNotifier notifier(boolean connected) {
        return new RemoteNotifier(cmd -> {
            if (connected) {
                sent.add(cmd);
            }
            return connected;
        }, Runnable::run);
    }

    @Test
    public void sendsAdd() {
        notifier(true).added(video, List.of(rec));
        var cmd = (AddLocalizationsCmd) sent.get(0);
        assertEquals(video, cmd.getValue().getUuid());
        assertEquals(List.of(rec.toRemote()), cmd.getValue().getLocalizations());
    }

    @Test
    public void sendsUpdate() {
        notifier(true).updated(video, List.of(rec));
        var cmd = (UpdateLocalizationsCmd) sent.get(0);
        assertEquals(List.of(rec.toRemote()), cmd.getValue().getLocalizations());
    }

    @Test
    public void sendsRemoveAndSelect() {
        var n = notifier(true);
        n.removed(video, List.of(rec.uuid()));
        n.selected(video, List.of(rec.uuid()));
        assertEquals(List.of(rec.uuid()), ((RemoveLocalizationsCmd) sent.get(0)).getValue().getLocalizations());
        assertEquals(List.of(rec.uuid()), ((SelectLocalizationsCmd) sent.get(1)).getValue().getLocalizations());
    }

    @Test
    public void noConnectionDropsQuietly() {
        notifier(false).added(video, List.of(rec));
        assertTrue(sent.isEmpty());
    }

    @Test
    public void failingSenderDoesNotThrow() {
        new RemoteNotifier(cmd -> { throw new IllegalStateException("boom"); }, Runnable::run)
                .added(video, List.of(rec));
    }

    @Test
    public void emptyPayloadsAreNotSent() {
        var n = notifier(true);
        n.added(video, List.of());
        n.removed(video, List.of());
        assertTrue(sent.isEmpty());
    }

    @Test
    public void emptySelectionIsSent() {
        notifier(true).selected(video, List.of());
        assertEquals(1, sent.size());
    }
}
```

- [ ] **Step 2: Run it to verify it fails** — `./gradlew test --tests "org.mbari.jsharktopoda.localization.RemoteNotifierTest" -q` → compile FAIL.

- [ ] **Step 3: Implement `RemoteNotifier`**

```java
package org.mbari.jsharktopoda.localization;

import org.mbari.vcr4j.remote.control.RVideoIO;
import org.mbari.vcr4j.remote.control.commands.RCommand;
import org.mbari.vcr4j.remote.control.commands.localization.AddLocalizationsCmd;
import org.mbari.vcr4j.remote.control.commands.localization.RemoveLocalizationsCmd;
import org.mbari.vcr4j.remote.control.commands.localization.SelectLocalizationsCmd;
import org.mbari.vcr4j.remote.control.commands.localization.UpdateLocalizationsCmd;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Tells the remote app about localization changes made in this app. Sends happen on a
 * background thread. If no remote app has connected the message is dropped.
 */
public class RemoteNotifier {

    @FunctionalInterface
    public interface Sender {
        /** @return false if there is no connection to send on */
        boolean send(RCommand<?, ?> command);
    }

    private static final System.Logger log = System.getLogger(RemoteNotifier.class.getName());

    private final Sender sender;
    private final Executor executor;

    public RemoteNotifier(Sender sender, Executor executor) {
        this.sender = sender;
        this.executor = executor;
    }

    /** @param connection the connection opened by the remote app's `connect` command, if any */
    public static RemoteNotifier forConnection(Supplier<Optional<RVideoIO>> connection) {
        var executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "remote-notifier");
            t.setDaemon(true);
            return t;
        });
        Sender sender = cmd -> connection.get()
                .map(io -> {
                    io.send(cmd);
                    return true;
                })
                .orElse(false);
        return new RemoteNotifier(sender, executor);
    }

    public void added(UUID videoUuid, List<LocalizationRecord> records) {
        if (!records.isEmpty()) {
            send(new AddLocalizationsCmd(videoUuid, records.stream().map(LocalizationRecord::toRemote).toList()));
        }
    }

    public void updated(UUID videoUuid, List<LocalizationRecord> records) {
        if (!records.isEmpty()) {
            send(new UpdateLocalizationsCmd(videoUuid, records.stream().map(LocalizationRecord::toRemote).toList()));
        }
    }

    public void removed(UUID videoUuid, List<UUID> localizationUuids) {
        if (!localizationUuids.isEmpty()) {
            send(new RemoveLocalizationsCmd(videoUuid, List.copyOf(localizationUuids)));
        }
    }

    /** An empty list is sent: it tells the remote app the selection was cleared. */
    public void selected(UUID videoUuid, List<UUID> localizationUuids) {
        send(new SelectLocalizationsCmd(videoUuid, List.copyOf(localizationUuids)));
    }

    private void send(RCommand<?, ?> cmd) {
        executor.execute(() -> {
            try {
                if (!sender.send(cmd)) {
                    log.log(System.Logger.Level.WARNING,
                            () -> "No remote app connected; dropping " + cmd.getName());
                }
            }
            catch (RuntimeException e) {
                log.log(System.Logger.Level.WARNING, "Failed to send " + cmd.getName(), e);
            }
        });
    }
}
```

- [ ] **Step 4: Run the test, expect PASS** — `./gradlew test --tests "org.mbari.jsharktopoda.localization.RemoteNotifierTest" -q`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/mbari/jsharktopoda/localization src/test/java/org/mbari/jsharktopoda/localization
git commit -m "feat: RemoteNotifier sends localization changes to the remote app

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: `VideoLocalizations` and app wiring (store, router, `hasVideo`)

Overlay attachment is added in Task 8; this task wires everything that does not need the UI.

**Files:**
- Create: `.../localization/VideoLocalizations.java`
- Test: `.../localization/VideoLocalizationsTest.java`
- Modify: `src/main/java/org/mbari/jsharktopoda/etc/vcr4j/SharkVideoController.java`
- Modify: `src/main/java/org/mbari/jsharktopoda/JSharktopoda.java`

**Interfaces:**
- Consumes: `LocalizationStore`, `LocalizationTarget`, `RemoteNotifier`, `LocalizationCommandRouter` (Tasks 2–4).
- Produces:
  - `VideoLocalizations(UUID videoUuid, RemoteNotifier notifier)` implements `LocalizationTarget`; `LocalizationStore getStore()`; `UUID getVideoUuid()`. (`attach(...)` and `dispose()` are added in Task 8.) Target methods only touch the store — they never call the notifier (no echo).
  - `SharkVideoController(RemoteNotifier)`; `Optional<VideoLocalizations> findLocalizations(UUID)`; `@Override boolean hasVideo(UUID)` (true while opening or open).

- [ ] **Step 1: Write the failing test**

```java
package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VideoLocalizationsTest {

    private final UUID video = UUID.randomUUID();
    private final List<String> sends = new ArrayList<>();
    private final RemoteNotifier notifier = new RemoteNotifier(cmd -> sends.add(cmd.getName()), Runnable::run);
    private final VideoLocalizations vl = new VideoLocalizations(video, notifier);

    private static LocalizationRecord rec(UUID id) {
        return new LocalizationRecord(id, "sponge", 100, 0, 10, 20, 30, 40, "#FF0000");
    }

    @Test
    public void commandsBeforeAttachAreKept() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.select(List.of(id));
        assertEquals(1, vl.getStore().size());
        assertEquals(List.of(id), vl.getStore().selected());
    }

    @Test
    public void addOfExistingUuidReplaces() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.add(List.of(new LocalizationRecord(id, "coral", 100, 0, 1, 1, 5, 5, null)));
        assertEquals(1, vl.getStore().size());
        assertEquals("coral", vl.getStore().get(id).orElseThrow().concept());
    }

    @Test
    public void updateKeepsOmittedFields() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.update(List.of(new Localization(id, null, null, null, 99, null, null, null, null)));
        var r = vl.getStore().get(id).orElseThrow();
        assertEquals(99, r.x());
        assertEquals(20, r.y());
        assertEquals("sponge", r.concept());
        assertEquals("#FF0000", r.color());
    }

    @Test
    public void updateIgnoresUnknownAndNullUuid() {
        vl.update(List.of(new Localization(UUID.randomUUID(), "c", 1L, 0L, 1, 1, 1, 1, null),
                new Localization(null, "c", 1L, 0L, 1, 1, 1, 1, null)));
        assertEquals(0, vl.getStore().size());
    }

    @Test
    public void removeClearSelectAffectStore() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        vl.add(List.of(rec(a), rec(b)));
        vl.select(List.of(a, UUID.randomUUID()));
        assertEquals(List.of(a), vl.getStore().selected());
        vl.remove(List.of(a, UUID.randomUUID()));
        assertEquals(1, vl.getStore().size());
        assertTrue(vl.getStore().selected().isEmpty());
        vl.clear();
        assertEquals(0, vl.getStore().size());
    }

    @Test
    public void remoteOriginatedChangesNeverEcho() {
        var id = UUID.randomUUID();
        vl.add(List.of(rec(id)));
        vl.update(List.of(new Localization(id, "x", 1L, 0L, 1, 1, 1, 1, null)));
        vl.select(List.of(id));
        vl.remove(List.of(id));
        vl.clear();
        assertTrue(sends.isEmpty());
    }
}
```

- [ ] **Step 2: Run it to verify it fails** — `./gradlew test --tests "org.mbari.jsharktopoda.localization.VideoLocalizationsTest" -q` → compile FAIL.

- [ ] **Step 3: Implement `VideoLocalizations`**

```java
package org.mbari.jsharktopoda.localization;

import org.mbari.vcr4j.remote.control.commands.localization.Localization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything localization-related for one video. Created when the video is opened (before its
 * window is ready) so commands that arrive while the video loads are kept in the store and shown
 * once the window exists. JavaFX application thread only.
 */
public class VideoLocalizations implements LocalizationTarget {

    private final UUID videoUuid;
    private final RemoteNotifier notifier;
    private final LocalizationStore store = new LocalizationStore();

    public VideoLocalizations(UUID videoUuid, RemoteNotifier notifier) {
        this.videoUuid = videoUuid;
        this.notifier = notifier;
    }

    public UUID getVideoUuid() {
        return videoUuid;
    }

    public LocalizationStore getStore() {
        return store;
    }

    @Override
    public void add(List<LocalizationRecord> records) {
        store.putAll(records);
    }

    @Override
    public void update(List<Localization> partials) {
        var merged = partials.stream()
                .filter(p -> p != null && p.getUuid() != null)
                .map(p -> store.get(p.getUuid()).map(existing -> existing.mergedWith(p)))
                .flatMap(Optional::stream)
                .toList();
        store.updateAll(merged);
    }

    @Override
    public void remove(List<UUID> uuids) {
        store.remove(uuids);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public void select(List<UUID> uuids) {
        store.select(uuids);
    }
}
```
(`notifier` is stored now and used by `attach` in Task 8; keep the field.)

- [ ] **Step 4: Run the test, expect PASS** — `./gradlew test --tests "org.mbari.jsharktopoda.localization.VideoLocalizationsTest" -q`

- [ ] **Step 5: Wire `SharkVideoController`**

Edit `src/main/java/org/mbari/jsharktopoda/etc/vcr4j/SharkVideoController.java`:

Add imports:
```java
import org.mbari.jsharktopoda.localization.RemoteNotifier;
import org.mbari.jsharktopoda.localization.VideoLocalizations;
```
Add fields and constructor (next to `controllers`):
```java
    private final Map<UUID, VideoLocalizations> localizations = new ConcurrentHashMap<>();
    private final RemoteNotifier notifier;

    public SharkVideoController(RemoteNotifier notifier) {
        this.notifier = notifier;
    }

    public Optional<VideoLocalizations> findLocalizations(UUID videoUuid) {
        return Optional.ofNullable(localizations.get(videoUuid));
    }

    /** True while a video is loading or open, so localization commands for it are accepted. */
    @Override
    public boolean hasVideo(UUID videoUuid) {
        return videoUuid != null && controllers.containsKey(videoUuid);
    }
```
In `open(...)`, immediately before `MovieStageController stageController = MovieStageController.newInstance(...)`:
```java
            localizations.putIfAbsent(videoUuid, new VideoLocalizations(videoUuid, notifier));
```
In `close(...)`, inside the `if (controller != null) {` block, after `controller.close();`:
```java
                localizations.remove(videoUuid);
```
(Disposal of the overlay is added in Task 8.)

- [ ] **Step 6: Wire `JSharktopoda`**

Edit `src/main/java/org/mbari/jsharktopoda/JSharktopoda.java`:

Add imports:
```java
import org.mbari.jsharktopoda.localization.LocalizationCommandRouter;
import org.mbari.jsharktopoda.localization.LocalizationTarget;
import org.mbari.jsharktopoda.localization.RemoteNotifier;
```
Add a field next to `videoControl` (line ~34):
```java
    private LocalizationCommandRouter localizationRouter;
```
Change line ~51 `videoController = new SharkVideoController();` to:
```java
        var notifier = RemoteNotifier.forConnection(() -> videoControl == null
                ? Optional.empty()
                : videoControl.getLifeCycle().get());
        videoController = new SharkVideoController(notifier);
```
Make `videoControl` `volatile` (`private volatile VideoControl videoControl;`). In `setPort(int port)`, replace the start and the build so the router follows each new `VideoControl`:
```java
    private void setPort(int port) {
        if (localizationRouter != null) {
            localizationRouter.close();
        }
        if (videoControl != null) {
            videoControl.close();
        }
        videoControl = new VideoControl.Builder()
                .port(port)
                .videoController(videoController)
                .build()
                .get();
        localizationRouter = new LocalizationCommandRouter(
                videoControl.getRequestHandler().getLocalizationsCmdObservable(),
                id -> videoController.findLocalizations(id).map(vl -> (LocalizationTarget) vl),
                Platform::runLater);
```
(keep whatever followed the original builder call, e.g. logging, after these lines; `Optional` is already imported through `java.util.*`).

- [ ] **Step 7: Verify and commit**

Run: `./gradlew clean test compileJava -q` → PASS.
Manual (you): `./gradlew run`, then `python3` or `nc -u` is not needed yet — just confirm the app starts and Open File still plays a video.

```bash
git add src/main/java src/test/java
git commit -m "feat: per-video VideoLocalizations, hasVideo, router and notifier wiring

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Pure helpers — `TimeWindow`, `Picking`, `DeleteShortcut`

**Files:**
- Create: `.../localization/TimeWindow.java`, `Picking.java`, `DeleteShortcut.java`
- Test: `.../localization/TimeWindowTest.java`, `PickingTest.java`

**Interfaces:**
- Produces:
  - `TimeWindow.DEFAULT_MILLIS` (= 200L); `static long from(double currentMillis, long windowMillis)` = `ceil(current - window/2)`; `static long to(double currentMillis, long windowMillis)` = `floor(current + window/2)`. An integer `elapsedTimeMillis` is in the window iff `from <= elapsed <= to`.
  - `Picking.pick(Collection<LocalizationRecord> candidates, double px, double py)` → `Optional<UUID>`: among boxes containing the point (edges inclusive) the one with the smallest distance from the point to its nearest edge; ties → smaller area; ties → first.
  - `DeleteShortcut.matches(KeyCode code, boolean shortcutDown)`.

- [ ] **Step 1: Write the failing tests**

`TimeWindowTest.java`:
```java
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
```
`PickingTest.java`:
```java
package org.mbari.jsharktopoda.localization;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class PickingTest {

    private static LocalizationRecord box(int x, int y, int w, int h) {
        return new LocalizationRecord(UUID.randomUUID(), "c", 0, 0, x, y, w, h, null);
    }

    @Test
    public void missReturnsEmpty() {
        assertTrue(Picking.pick(List.of(box(10, 10, 20, 20)), 5, 5).isEmpty());
        assertTrue(Picking.pick(List.of(), 5, 5).isEmpty());
    }

    @Test
    public void edgesAreInclusive() {
        var b = box(10, 10, 20, 20);
        assertEquals(b.uuid(), Picking.pick(List.of(b), 10, 10).orElseThrow());
        assertEquals(b.uuid(), Picking.pick(List.of(b), 30, 30).orElseThrow());
    }

    @Test
    public void nearestEdgeWinsWhenBoxesOverlap() {
        var outer = box(0, 0, 100, 100);
        var inner = box(40, 40, 20, 20);
        // point (45,50): 5 from inner's left edge, 45 from outer's nearest edge
        assertEquals(inner.uuid(), Picking.pick(List.of(outer, inner), 45, 50).orElseThrow());
        // point (5,50): only inside outer
        assertEquals(outer.uuid(), Picking.pick(List.of(outer, inner), 5, 50).orElseThrow());
        // point (50,50): inner center is 10 from its edge, outer center 50 from its edge
        assertEquals(inner.uuid(), Picking.pick(List.of(outer, inner), 50, 50).orElseThrow());
    }

    @Test
    public void tieGoesToSmallerArea() {
        var big = box(0, 0, 100, 100);
        var small = box(0, 0, 50, 50);
        // (10,10) is 10 from both boxes' nearest edge
        assertEquals(small.uuid(), Picking.pick(List.of(big, small), 10, 10).orElseThrow());
    }
}
```

- [ ] **Step 2: Run to verify FAIL** — `./gradlew test --tests "org.mbari.jsharktopoda.localization.TimeWindowTest" --tests "org.mbari.jsharktopoda.localization.PickingTest" -q` → compile FAIL.

- [ ] **Step 3: Implement**

`TimeWindow.java`:
```java
package org.mbari.jsharktopoda.localization;

/**
 * How long a localization stays on screen around its elapsedTimeMillis.
 */
public final class TimeWindow {

    /**
     * Total width of the display window, centered on a localization's elapsedTimeMillis
     * (so +/- 100 ms). This is a constant for now; it must become a user Preference
     * (see UI.md, "Annotation Display > Time Window"). 50 ms is too short to see comfortably.
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
```
`Picking.java`:
```java
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
```
`DeleteShortcut.java`:
```java
package org.mbari.jsharktopoda.localization;

import javafx.scene.input.KeyCode;

/**
 * Cmd-Delete on macOS, Ctrl-Delete on Windows/Linux. Callers pass
 * {@code KeyEvent.isShortcutDown()}, which is Cmd on macOS and Ctrl elsewhere. The macOS
 * "delete" key reports {@code BACK_SPACE}, so both codes are accepted.
 */
public final class DeleteShortcut {

    private DeleteShortcut() {
    }

    public static boolean matches(KeyCode code, boolean shortcutDown) {
        return shortcutDown && (code == KeyCode.DELETE || code == KeyCode.BACK_SPACE);
    }
}
```

- [ ] **Step 4: Run tests, expect PASS** — same command as Step 2.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/mbari/jsharktopoda/localization src/test/java/org/mbari/jsharktopoda/localization
git commit -m "feat: time-window, nearest-edge picking and delete-shortcut helpers

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Move the video area onto imgfx `MediaPaneController`

No localization logic yet. Puts the video in an autoscaling pane and moves the playback controls out from over the video (the requirements forbid floating controls over the drawing area). Verified manually.

**Files:**
- Modify: `src/main/resources/fxml/MoviePane.fxml`
- Modify: `src/main/java/org/mbari/jsharktopoda/MoviePaneController.java`
- Modify: `src/main/java/org/mbari/jsharktopoda/MovieStageController.java`

**Interfaces:**
- Produces: `MoviePaneController.getMediaPaneController()` and `MovieStageController.getMediaPaneController()` returning the `org.mbari.imgfx.mediaview.MediaPaneController` (non-null once the player is READY — i.e. by the time `MovieStageController.readyProperty()` becomes true). `MoviePaneController.getRoot()` now returns a `BorderPane`.

- [ ] **Step 1: Replace `MoviePane.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.Slider?>
<?import javafx.scene.layout.BorderPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.media.MediaView?>

<BorderPane id="rootPane" fx:id="rootPane" prefHeight="400.0" prefWidth="600.0" xmlns="http://javafx.com/javafx/9" xmlns:fx="http://javafx.com/fxml/1" fx:controller="org.mbari.jsharktopoda.MoviePaneController">
    <center>
        <StackPane fx:id="videoHolder">
            <children>
                <MediaView fx:id="mediaView" />
            </children>
        </StackPane>
    </center>
    <bottom>
        <HBox alignment="CENTER">
            <padding>
                <Insets bottom="5.0" left="10.0" right="10.0" top="5.0" />
            </padding>
            <children>
                <Button id="playButton" fx:id="playButton" mnemonicParsing="false" />
                <Label fx:id="timeLabel" minWidth="40.0" styleClass="timeLabel" />
                <Slider id="scrubber" fx:id="scrubber" maxWidth="1.7976931348623157E308" minWidth="50.0" HBox.hgrow="ALWAYS" />
                <Label fx:id="maxTimeLabel" minWidth="40.0" styleClass="timeLabel" />
            </children>
        </HBox>
    </bottom>
</BorderPane>
```

- [ ] **Step 2: Edit `MoviePaneController.java`**

Imports: remove `javafx.scene.layout.AnchorPane`; add
```java
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.mbari.imgfx.mediaview.MediaPaneController;
```
Replace the `anchorPane` field with:
```java
    @FXML
    private BorderPane rootPane;

    @FXML
    private StackPane videoHolder;

    private MediaPaneController mediaPaneController;
```
In `initialize`, delete these two lines (imgfx sizes the MediaView; binding them too would throw):
```java
        mediaView.fitWidthProperty().bind(anchorPane.widthProperty());
        mediaView.fitHeightProperty().bind(anchorPane.heightProperty());
```
Replace `getRoot()`/`getAnchorPane()` with:
```java
    public BorderPane getRoot() {
        return rootPane;
    }

    public MediaPaneController getMediaPaneController() {
        return mediaPaneController;
    }

    /** imgfx's Autoscale needs the media size, so the video pane is only built once the player is READY. */
    private void installVideoPane() {
        if (mediaPaneController == null) {
            videoHolder.getChildren().clear();
            mediaPaneController = new MediaPaneController(mediaView);
            videoHolder.getChildren().add(mediaPaneController.getPane());
        }
    }
```
In `setMediaLocation`, replace
```java
        mediaPlayer.setOnReady(() -> onReadyRunnable.accept(this));
```
with
```java
        mediaPlayer.setOnReady(() -> {
            installVideoPane();
            onReadyRunnable.accept(this);
        });
```

- [ ] **Step 3: Edit `MovieStageController.java`**

Replace `import javafx.scene.layout.AnchorPane;` with `import javafx.scene.layout.BorderPane;`, add `import org.mbari.imgfx.mediaview.MediaPaneController;`, change in `init()`:
```java
            BorderPane root = controller.getRoot();
```
and add:
```java
    public MediaPaneController getMediaPaneController() {
        return controller.getMediaPaneController();
    }
```
Check nothing else referenced `getAnchorPane()`: `grep -rn "getAnchorPane\|AnchorPane" src/main/java` should print nothing.

- [ ] **Step 4: Compile and verify manually (you)**

Run: `./gradlew compileJava -q` → no errors.
Run: `./gradlew run`, **Open File** with any local `.mp4`/`.mov`. Expected:
- The video shows, plays, and the scrubber/play button work; the time labels update.
- Resize the window: the video scales keeping its aspect ratio, centered, on a black background.
- The controls sit **below** the video and never cover it.
- No exceptions in the console (`bound value cannot be set` means Step 2's binding removal was missed).
- Send a frame capture (optional): PNG still written (capture snapshots the `MediaView` only).

- [ ] **Step 5: Commit**

```bash
git add src/main
git commit -m "refactor: host video in imgfx MediaPaneController, controls below the video

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 8: `LocalizationOverlay` — rendering, selection display, time window

Renders store contents over the video (no mouse gestures yet). Verified manually with UDP commands.

**Files:**
- Create: `.../localization/LocalizationOverlay.java`
- Create: `scripts/udp_send.py`, `scripts/udp_listen.py`
- Modify: `.../localization/VideoLocalizations.java` (add `attach`, `dispose`)
- Modify: `.../etc/vcr4j/SharkVideoController.java` (attach on ready, dispose on close)

**Interfaces:**
- Consumes: `LocalizationStore`, `TimeWindow`, imgfx `MediaPaneController`, `BuilderCoordinator`, `Localization`, `RectangleView`, `RectangleViewEditor`; `MovieStageController.getMediaPaneController()` (Task 7).
- Produces: `LocalizationOverlay(LocalizationStore store, MediaPaneController paneController, MediaPlayer mediaPlayer, Scene scene, Listener listener)`; nested `interface Listener { void userAdded(LocalizationRecord); void userUpdated(LocalizationRecord); void userRemoved(List<UUID>); void userSelected(List<UUID>); }`; `void dispose()`. `VideoLocalizations.attach(MediaPaneController, MediaPlayer, Scene)` and `VideoLocalizations.dispose()`.

- [ ] **Step 1: Create the manual-verification scripts**

`scripts/udp_send.py`:
```python
#!/usr/bin/env python3
"""Send one JSON command to jsharktopoda over UDP and print the reply.

usage: udp_send.py '<json>' [port]      (default port 8800)
"""
import socket
import sys

msg = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 8800
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.settimeout(2)
s.sendto(msg.encode("utf-8"), ("127.0.0.1", port))
try:
    print(s.recvfrom(65535)[0].decode())
except socket.timeout:
    print("(no reply)")
```
`scripts/udp_listen.py`:
```python
#!/usr/bin/env python3
"""Pretend to be the remote app: print every command jsharktopoda sends and reply ok.

usage: udp_listen.py [port]      (default port 9999; send `connect` with this port first)
"""
import json
import socket
import sys

port = int(sys.argv[1]) if len(sys.argv) > 1 else 9999
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("127.0.0.1", port))
print(f"listening on {port}", flush=True)
while True:
    data, addr = s.recvfrom(65535)
    text = data.decode()
    print("<-", text, flush=True)
    try:
        cmd = json.loads(text).get("command", "")
    except Exception:
        cmd = ""
    s.sendto(json.dumps({"response": cmd, "status": "ok"}).encode(), addr)
```

- [ ] **Step 2: Implement `LocalizationOverlay` (render part)**

```java
package org.mbari.jsharktopoda.localization;

import javafx.beans.InvalidationListener;
import javafx.scene.Scene;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import org.mbari.imgfx.BuilderCoordinator;
import org.mbari.imgfx.mediaview.MediaPaneController;
import org.mbari.imgfx.roi.Localization;
import org.mbari.imgfx.roi.RectangleView;
import org.mbari.imgfx.roi.RectangleViewEditor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a video's localizations over the video using imgfx. Only the records inside the current
 * time window exist as scene-graph nodes; the {@link LocalizationStore} is the source of truth.
 * JavaFX application thread only.
 */
public class LocalizationOverlay {

    /** Notified of changes the user makes by interacting with the video (never of remote-originated ones). */
    public interface Listener {
        void userAdded(LocalizationRecord record);

        void userUpdated(LocalizationRecord record);

        void userRemoved(List<UUID> uuids);

        void userSelected(List<UUID> uuids);
    }

    /** One drawn localization. */
    private static final class Shown {
        LocalizationRecord record;
        final Localization<RectangleView, MediaView> localization;
        final RectangleView view;
        final RectangleViewEditor editor;

        Shown(LocalizationRecord record,
              Localization<RectangleView, MediaView> localization,
              RectangleView view,
              RectangleViewEditor editor) {
            this.record = record;
            this.localization = localization;
            this.view = view;
            this.editor = editor;
        }
    }

    private static final System.Logger log = System.getLogger(LocalizationOverlay.class.getName());

    private final LocalizationStore store;
    private final MediaPaneController paneController;
    private final MediaPlayer mediaPlayer;
    private final Scene scene;
    private final Listener listener;
    private final BuilderCoordinator builderCoordinator = new BuilderCoordinator();
    private final Map<UUID, Shown> shown = new LinkedHashMap<>();
    private final Runnable removeStoreListener;
    private final InvalidationListener timeListener = obs -> refresh();
    private final InvalidationListener scaleListener = obs -> rebuildAll();

    public LocalizationOverlay(LocalizationStore store,
                               MediaPaneController paneController,
                               MediaPlayer mediaPlayer,
                               Scene scene,
                               Listener listener) {
        this.store = store;
        this.paneController = paneController;
        this.mediaPlayer = mediaPlayer;
        this.scene = scene;
        this.listener = listener;

        removeStoreListener = store.addListener(this::refresh);
        mediaPlayer.currentTimeProperty().addListener(timeListener);
        var autoscale = paneController.getAutoscale();
        autoscale.scaleXProperty().addListener(scaleListener);
        autoscale.scaleYProperty().addListener(scaleListener);
        refresh();
    }

    // ---------------------------------------------------------------- rendering

    private double currentMillis() {
        var millis = mediaPlayer.getCurrentTime().toMillis();
        return Double.isNaN(millis) ? 0D : millis;
    }

    /** Bring the drawn nodes in line with the store, the playhead and the selection. */
    void refresh() {
        var current = currentMillis();
        var visible = store.query(TimeWindow.from(current, TimeWindow.DEFAULT_MILLIS),
                TimeWindow.to(current, TimeWindow.DEFAULT_MILLIS));

        Set<UUID> visibleIds = new HashSet<>();
        for (var r : visible) {
            visibleIds.add(r.uuid());
        }

        shown.entrySet().removeIf(e -> {
            var stale = !visibleIds.contains(e.getKey());
            if (stale) {
                dispose(e.getValue());
            }
            return stale;
        });

        for (var r : visible) {
            var existing = shown.get(r.uuid());
            if (existing != null && existing.record.equals(r)) {
                continue;
            }
            if (existing != null) {
                dispose(existing);
                shown.remove(r.uuid());
            }
            show(r).ifPresent(s -> shown.put(r.uuid(), s));
        }
        applySelection();
    }

    /** The scale changed (window resized): rebuild from the store so imgfx's view/data sync can't drift. */
    private void rebuildAll() {
        shown.values().forEach(this::dispose);
        shown.clear();
        refresh();
    }

    private Optional<Shown> show(LocalizationRecord r) {
        return RectangleView.fromImageCoords((double) r.x(), (double) r.y(),
                        (double) r.width(), (double) r.height(), paneController.getAutoscale())
                .map(view -> {
                    Localization<RectangleView, MediaView> loc =
                            new Localization<>(view, paneController, r.uuid(), "");
                    var editor = new RectangleViewEditor(view, paneController.getPane());
                    applyStyle(view, r, false);
                    loc.setVisible(true);
                    builderCoordinator.addLocalization(loc);
                    return new Shown(r, loc, view, editor);
                });
    }

    private void dispose(Shown s) {
        s.view.setEditing(false);
        s.localization.setVisible(false);
        builderCoordinator.removeLocalization(s.localization);
    }

    private void applySelection() {
        var selected = store.selected();
        var single = selected.size() == 1 ? selected.get(0) : null;
        for (var s : shown.values()) {
            var uuid = s.record.uuid();
            var isSelected = store.isSelected(uuid);
            var shouldEdit = uuid.equals(single);

            if (s.view.isEditing() && !shouldEdit) {
                s.view.setEditing(false);
            }
            s.localization.setLabel(isSelected ? s.record.concept() : "");
            if (!s.view.isEditing()) {
                // imgfx restores the pre-edit look when editing ends, so style before editing starts
                applyStyle(s.view, s.record, isSelected);
                if (shouldEdit) {
                    s.view.setEditing(true);
                }
            }
        }
    }

    private static void applyStyle(RectangleView view, LocalizationRecord r, boolean selected) {
        var c = parseColor(r.color());
        var rect = view.getView();
        rect.setStroke(c);
        rect.setStrokeWidth(selected ? 4 : 2);
        rect.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), selected ? 0.35 : 0.1));
    }

    private static Color parseColor(String color) {
        try {
            return Color.web(color);
        }
        catch (IllegalArgumentException | NullPointerException e) {
            log.log(System.Logger.Level.WARNING, () -> "Bad localization color '" + color + "', using white");
            return Color.WHITE;
        }
    }

    public void dispose() {
        removeStoreListener.run();
        mediaPlayer.currentTimeProperty().removeListener(timeListener);
        var autoscale = paneController.getAutoscale();
        autoscale.scaleXProperty().removeListener(scaleListener);
        autoscale.scaleYProperty().removeListener(scaleListener);
        shown.values().forEach(this::dispose);
        shown.clear();
    }
}
```
`scene` and `listener` are stored now and used by the gesture code in Task 9.

- [ ] **Step 3: Add `attach`/`dispose` to `VideoLocalizations`**

Add imports `javafx.scene.Scene`, `javafx.scene.media.MediaPlayer`, `org.mbari.imgfx.mediaview.MediaPaneController`. Add field `private LocalizationOverlay overlay;` and methods:

```java
    /** Called on the FX thread once the window and player are ready. Draws whatever is already in the store. */
    public void attach(MediaPaneController paneController, MediaPlayer mediaPlayer, Scene scene) {
        if (overlay != null) {
            return;
        }
        overlay = new LocalizationOverlay(store, paneController, mediaPlayer, scene,
                new LocalizationOverlay.Listener() {
                    @Override
                    public void userAdded(LocalizationRecord record) {
                        notifier.added(videoUuid, List.of(record));
                    }

                    @Override
                    public void userUpdated(LocalizationRecord record) {
                        notifier.updated(videoUuid, List.of(record));
                    }

                    @Override
                    public void userRemoved(List<UUID> uuids) {
                        notifier.removed(videoUuid, uuids);
                    }

                    @Override
                    public void userSelected(List<UUID> uuids) {
                        notifier.selected(videoUuid, uuids);
                    }
                });
    }

    public void dispose() {
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
        }
    }
```

- [ ] **Step 4: Attach on ready and dispose on close in `SharkVideoController`**

In the existing `stageController.readyProperty().addListener((ovs, oldv, newv) -> { ... })` in `open(...)`, add at the end of the lambda body:
```java
                var vl = localizations.get(videoUuid);
                if (vl != null) {
                    vl.attach(stageController.getMediaPaneController(),
                            stageController.getMediaPlayer(),
                            stageController.getStage().getScene());
                }
```
In `close(...)`, replace the `localizations.remove(videoUuid);` added in Task 5 with:
```java
                var vl = localizations.remove(videoUuid);
                if (vl != null) {
                    Platform.runLater(vl::dispose);
                }
```

- [ ] **Step 5: Compile and run the unit tests** — `./gradlew clean test compileJava -q` → PASS.

- [ ] **Step 6: Manual verification (you)**

Terminal A: `python3 scripts/udp_listen.py 9999`. Terminal B, from the repo root (`V=11111111-1111-1111-1111-111111111111`, `URL=file:///absolute/path/to/some.mp4`):
```bash
./gradlew run    # in a third terminal
python3 scripts/udp_send.py '{"command":"connect","port":9999}'
python3 scripts/udp_send.py '{"command":"add localizations","uuid":"'$V'","localizations":[{"uuid":"aaaaaaaa-0000-0000-0000-000000000001","concept":"Sponge","elapsedTimeMillis":0,"x":100,"y":100,"width":200,"height":150,"color":"#00FF00"}]}'
```
Expected (video not open yet): reply `{"response":"add localizations","status":"failed","cause":"No video for uuid"...}`.

Then open the video and add localizations **while it loads** (Review Focus 3):
```bash
python3 scripts/udp_send.py '{"command":"open","uuid":"'$V'","url":"'$URL'"}'
python3 scripts/udp_send.py '{"command":"add localizations","uuid":"'$V'","localizations":[{"uuid":"aaaaaaaa-0000-0000-0000-000000000001","concept":"Sponge","elapsedTimeMillis":0,"x":100,"y":100,"width":200,"height":150,"color":"#00FF00"},{"uuid":"aaaaaaaa-0000-0000-0000-000000000002","concept":"Coral","elapsedTimeMillis":5000,"x":400,"y":200,"width":120,"height":120}]}'
```
Expected:
- Replies `ok`. A green box appears at 0 s; scrub/seek to ~5 s (`{"command":"seek elapsed time","uuid":...,"elapsedTimeMillis":5000}`): the Coral box (default white) appears, the Sponge box disappears. Boxes are visible only within ±100 ms of their time.
- Resize the window: boxes stay glued to the same video pixels.
- `{"command":"select localizations","uuid":...,"localizations":["aaaaaaaa-0000-0000-0000-000000000001"]}` at 0 s: the box gets a thicker border and shows "Sponge"; a single selected box also shows imgfx's edit handles (corner squares).
- `update localizations` with only `{"uuid":"aaaaaaaa-...01","x":300}`: box moves right, size/color unchanged (Review Focus 1). `add` of the same uuid again: box replaced, no duplicate (Review Focus 5). `remove localizations` (note: the protocol key is `"command"`) removes it; `clear localizations` empties the video.
- Nothing is printed by `udp_listen.py` (remote-originated changes never echo). Close the video window; no exceptions.

- [ ] **Step 7: Commit**

```bash
git add src/main/java scripts
git commit -m "feat: render localizations over video with time window, selection style and verification scripts

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 9: `LocalizationOverlay` — gestures (select, edit, create, delete)

Adds mouse/keyboard interaction and reports user changes through `Listener`. Verified manually.

**Files:**
- Modify: `.../localization/LocalizationOverlay.java`

**Interfaces:**
- Consumes: `Picking`, `DeleteShortcut`, imgfx `RectangleBuilder`, `EventBus`, `AddRectangleEvent`; `Listener`, `store`, `shown` from Task 8.

- [ ] **Step 1: Add imports and fields**

Add imports:
```java
import io.reactivex.rxjava3.disposables.Disposable;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.mbari.imgfx.etc.rx.EventBus;
import org.mbari.imgfx.etc.rx.events.AddRectangleEvent;
import org.mbari.imgfx.roi.RectangleBuilder;
```
Add fields (below `scaleListener`):
```java
    private final EventBus eventBus = new EventBus();
    private RectangleBuilder builder;
    private Disposable builtSubscription;
    private final EventHandler<MouseEvent> pressedFilter = this::onPressed;
    private final EventHandler<MouseEvent> releasedFilter = this::onReleased;
    private final EventHandler<KeyEvent> keyFilter = this::onKey;
```

- [ ] **Step 2: Install/uninstall the gestures**

In the constructor, add `installGestures();` on the line **before** the final `refresh();`. In `dispose()`, add `uninstallGestures();` as the first line. Add these methods:

```java
    // ---------------------------------------------------------------- gestures

    private void installGestures() {
        builder = new RectangleBuilder(paneController, eventBus);
        builder.setEditColor(Color.color(1, 1, 1, 0.25));
        builderCoordinator.addBuilder(builder);
        builderCoordinator.setCurrentBuilder(builder);
        builder.setDisabled(false);
        builtSubscription = eventBus.toObserverable()
                .ofType(AddRectangleEvent.class)
                .subscribe(this::onBuilt);

        var pane = paneController.getPane();
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, pressedFilter);
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, releasedFilter);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
    }

    private void uninstallGestures() {
        var pane = paneController.getPane();
        pane.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressedFilter);
        pane.removeEventFilter(MouseEvent.MOUSE_RELEASED, releasedFilter);
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        if (builtSubscription != null) {
            builtSubscription.dispose();
        }
        builder.setDisabled(true);
    }

    private boolean isEditorNode(Object target) {
        return shown.values().stream().anyMatch(s -> s.editor.getNodes().contains(target));
    }

    /** A press on the video: pause, then select the box under the pointer or clear the selection. */
    private void onPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || isEditorNode(e.getTarget())) {
            return;   // the imgfx editor handles drags on the selected box and its handles
        }
        var autoscale = paneController.getAutoscale();
        Point2D p = autoscale.sceneToUnscaled(new Point2D(e.getSceneX(), e.getSceneY()));
        if (p.getX() < 0 || p.getY() < 0
                || p.getX() > autoscale.getUnscaledWidth() || p.getY() > autoscale.getUnscaledHeight()) {
            return;   // letterbox area, not on the video
        }

        mediaPlayer.pause();   // localization actions started here pause playback

        var records = shown.values().stream().map(s -> s.record).toList();
        var hit = Picking.pick(records, p.getX(), p.getY());
        var selected = store.selected();
        if (hit.isPresent()) {
            var uuid = hit.get();
            if (selected.size() == 1 && selected.get(0).equals(uuid)) {
                return;
            }
            store.select(List.of(uuid));
            listener.userSelected(List.of(uuid));
        }
        else if (!selected.isEmpty()) {
            store.select(List.of());
            listener.userSelected(List.of());
        }
    }

    /** End of a drag: if the edited box moved or was resized, write it to the store and tell the remote app. */
    private void onReleased(MouseEvent e) {
        for (var s : List.copyOf(shown.values())) {
            if (!s.view.isEditing()) {
                continue;
            }
            s.view.updateData();   // converts the view back to video pixels and clips to the video
            var d = s.view.getData();
            var updated = new LocalizationRecord(s.record.uuid(),
                    s.record.concept(),
                    s.record.elapsedTimeMillis(),
                    s.record.durationMillis(),
                    (int) Math.round(d.getX()),
                    (int) Math.round(d.getY()),
                    (int) Math.max(1, Math.round(d.getWidth())),
                    (int) Math.max(1, Math.round(d.getHeight())),
                    s.record.color());
            if (!updated.equals(s.record)) {
                s.record = updated;       // set first so refresh() doesn't rebuild the node being edited
                s.view.updateView();      // show the clipped result
                store.update(updated);
                listener.userUpdated(updated);
            }
        }
    }

    /** imgfx finished drawing a new rectangle: make it a localization at the current frame and select it. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onBuilt(AddRectangleEvent event) {
        var view = (RectangleView) event.localization().getDataView();
        var d = view.getData();
        var record = new LocalizationRecord(UUID.randomUUID(),
                LocalizationRecord.DEFAULT_CONCEPT,
                Math.round(currentMillis()),
                0,
                (int) Math.round(d.getX()),
                (int) Math.round(d.getY()),
                (int) Math.max(1, Math.round(d.getWidth())),
                (int) Math.max(1, Math.round(d.getHeight())),
                LocalizationRecord.DEFAULT_COLOR);
        store.put(record);
        store.select(List.of(record.uuid()));
        listener.userAdded(record);
        listener.userSelected(List.of(record.uuid()));
    }

    /** Cmd-Delete (macOS) / Ctrl-Delete (Windows, Linux): remove the selected localizations. */
    private void onKey(KeyEvent e) {
        if (!DeleteShortcut.matches(e.getCode(), e.isShortcutDown())) {
            return;
        }
        var selected = store.selected();
        if (selected.isEmpty()) {
            return;
        }
        var removed = store.remove(selected);
        if (!removed.isEmpty()) {
            listener.userRemoved(removed);
        }
        e.consume();
    }
```
The builder's own throw-away view (from `onBuilt`) is never added to the pane; the node the user sees is created by `refresh()` from the stored record.

- [ ] **Step 3: Compile and unit-test** — `./gradlew clean test compileJava -q` → PASS.

- [ ] **Step 4: Manual verification (you)**

Set up as in Task 8 Step 6 (`udp_listen.py 9999`, `./gradlew run`, `connect`, `open`). Then, on the video window (paused at any time):
1. **Create:** click-drag a rectangle inside the video. Expected: playback pauses; on release a box appears (white, fills lightly) with handles (it is selected/editable); `udp_listen.py` prints an `add localizations` (elapsedTimeMillis = current frame, random uuid, concept `""`, color `#FFFFFF`) then a `select localizations` with that uuid.
2. **Move / resize:** drag the box body and each corner handle. On each release `udp_listen.py` prints one `update localizations` with new x/y/width/height in **video pixels** (not window pixels — resize the window and repeat; values must stay consistent). Dragging partly off the video clips to the video edge.
3. **Select:** with two overlapping boxes at the same time, click inside the overlap near one box's edge: that box gets selected (nearest edge), `select localizations` printed. Click empty video area: selection clears (`select` with `[]`), and the next drag starts a new box. (Known imgfx behavior: the press that ends an edit may only deselect; if the very next press doesn't start a draw, press again — note it if it happens on the first press.)
4. **Delete:** select a box, press **Cmd-Delete** (macOS) / **Ctrl-Delete** (Windows/Linux): box disappears, `udp_listen.py` prints `remove localizations` with its uuid. Plain Delete/Backspace without the modifier does nothing.
5. **Resize while editing (Review Focus 4):** select a box (handles visible), resize the window by dragging its corner; boxes stay aligned to the video content; the printed coordinates of a subsequent drag are still in video pixels and no spurious `update` was printed during the resize.
6. **Remote interplay:** `select localizations` from the remote shows the box selected without printing anything to the listener; `update`/`remove` from the remote likewise never echo.

If anything in 1–5 fails, fix it in `LocalizationOverlay` before committing; do not commit a failing gesture.

- [ ] **Step 5: Commit**

```bash
git add src/main/java
git commit -m "feat: create, select, move/resize and delete localizations on the video

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Docs and final verification

**Files:**
- Modify: `README.md`, `CLAUDE.md`

- [ ] **Step 1: Update `README.md`**

In **Features** add: "Render, create, select, move/resize and delete rectangular localizations over the video (Cmd/Ctrl-Delete removes the selection), synchronized with the remote app over UDP". In **Architecture > Core Classes** add rows:

| Class | Role |
|---|---|
| `LocalizationStore` | Per-video, time-indexed store of localizations and the selection (source of truth). |
| `LocalizationOverlay` | Draws the localizations inside the current time window with imgfx, and handles selecting, editing, creating and deleting. |
| `LocalizationCommandRouter` / `RemoteNotifier` | Apply incoming localization commands to the store / send user changes back to the remote app. |

Add under Key Dependencies: "[imgfx](https://github.com/mbari-org/imgfx) — autoscaling video pane and rectangle drawing/editing". Mention `scripts/udp_send.py` and `scripts/udp_listen.py` under a new "Manual testing" heading (one sentence each).

- [ ] **Step 2: Update `CLAUDE.md`**

In **Architecture**, add a short "Localizations" paragraph: store is the single source of truth and FX-thread only; the overlay materializes only records inside `TimeWindow.DEFAULT_MILLIS`; remote-originated changes go router → `VideoLocalizations` (store only, never the notifier) and user gestures go overlay → store → `RemoteNotifier`; `VideoLocalizations` is created in `SharkVideoController.open()` before the window is ready and `hasVideo` is what makes vcr4j answer `failed` for unknown videos. Update the testing line to: "Unit tests cover the store, record, router, notifier and helpers; the overlay and stage wiring are verified manually with `scripts/udp_send.py` / `scripts/udp_listen.py`." Fix the stale statement about `MoviePaneController` implementing `FrameCaptureService`/`MediaView.snapshot()` only if it is wrong (it is correct: capture snapshots the `MediaView`, which excludes overlays).

- [ ] **Step 3: Full verification**

Run: `./gradlew clean build` → BUILD SUCCESSFUL, all tests pass.
Run: `./gradlew jpackage --info` (optional, slow) → confirm the jlink image still builds with JDK 27 and imgfx; if the `org.beryx.jlink` plugin fails on JDK 27 or on a missing module (e.g. `jdk.unsupported`), report it rather than working around it.
Re-run the Task 9 manual checklist once on the final build, plus: open two videos with different UUIDs and confirm localizations do not leak between windows; close a window and confirm later commands for it reply `failed`.

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: document localizations

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

## Self-Review Notes

- **Spec coverage:** store/time index (T2); router + failed-for-unknown-video via `hasVideo` (T3, T5); notifier incl. no-connection drop and off-FX-thread send (T4); `VideoLocalizations` created at `open()` so loading-time commands are kept (T5); 200 ms window constant with Preferences note (T6, T8); render/diff/selection style/rebuild on resize (T8); select/edit/create/delete incl. platform delete shortcut, pause-on-gesture, no-echo, clip (T9); MediaPaneController/controls restructure (T7); the spec's three open items are resolved here: imgfx/JavaFX compatibility (T1 — forces JDK/JavaFX 27), `RVideoIO` sending (T4 — `RVideoIO.send(VideoCommand)` via `RVideoIOLifeCycle.get()`), `currentTime` granularity (the 200 ms window is wider than MediaPlayer's update interval, so listening to `currentTimeProperty` suffices; confirmed by the Task 8 manual check — if boxes flicker during playback, drive `refresh()` from an `AnimationTimer` while playing).
- **Type consistency:** `LocalizationRecord` accessor names, `LocalizationStore` method names, `Listener` methods and `VideoLocalizations.attach(MediaPaneController, MediaPlayer, Scene)` are identical across tasks.
