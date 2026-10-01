# Localizations (render, create, select, edit, delete) — Design

Date: 2026-09-30

## Goal

Make jsharktopoda the cross-platform JavaFX counterpart of Sharktopoda 2 (Swift) for rectangular
localizations. Rectangles are drawn over the video at the right moment, and the user can create,
select, move/resize and delete them. Each user change is reported to the remote app (e.g. VARS
Annotation) over the existing UDP protocol implemented by vcr4j-remote.

Source requirements: `/Users/brian/workspace/github/mbari-org/Sharktopoda/Requirements/`
(`REQUIREMENTS.md`, `UI.md`, `UDP_Remote_Protocol.md`).

### Scope

In: render; create by drag; select (click and via `select` command); move/resize; delete;
incoming `add/update/remove/clear/select`; outgoing `add/update/remove/select`.

Out (future specs): Preferences UI (colors, time window, use-duration, concept), multi-select
(Cmd-click, Cmd-drag), `durationMillis` display semantics, full-screen, per-state styling.

### Constraints

- Coordinates are unscaled video pixels, origin upper-left, +Y down.
- Must handle tens of thousands of localizations per video.
- Incoming messages are at most 4096 bytes; remote sends at most ~10 localizations per message.
- Rendering uses imgfx (`org.mbari.imgfx:imgfx`, currently 0.0.20 in mavenLocal): `MediaPaneController`,
  `MediaViewAutoscale`, `RectangleBuilder`, `RectangleView`, `RectangleViewEditor`, `Localization`.

## Already done: vcr4j 5.3.8

vcr4j:
`VideoController.hasVideo(UUID)` (default `true`); the five localization handlers reply `failed` with
cause `"No video for uuid"` when it is false, and do not emit to the observable; localization
`Response` classes accept a cause. jsharktopoda depends on 5.3.8 which is in maven central.

## Architecture

New package `org.mbari.jsharktopoda.localization`:

| Unit | Scope | Responsibility |
|---|---|---|
| `LocalizationRecord` | value | Immutable: uuid, concept, elapsedTimeMillis, durationMillis, x, y, width, height, color. |
| `LocalizationStore` | per video | Map by UUID plus time-sorted index. `add`, `update`, `remove`, `clear`, `query(t0, t1)`. No JavaFX dependency. FX-thread confined, no locking. |
| `LocalizationOverlay` | per video window | Wraps the video in imgfx `MediaPaneController`. Owns `BuilderCoordinator` and `RectangleBuilder`. Materializes imgfx `Localization` nodes for visible records, handles selection, editing, create, delete. |
| `LocalizationCommandRouter` | app | Subscribes to `RxPlayerRequestHandler.getLocalizationsCmdObservable()`; routes each command by video UUID to the store/overlay on the FX thread. |
| `RemoteNotifier` | app | Sends `add/update/remove/select` via the connection opened by `connect` (`RVideoIOLifeCycle`). |

Flow: remote → UDP → vcr4j → router → store → overlay. User gesture → overlay → store → notifier → UDP.
All changes go to the store first. Changes that originate from the remote are never echoed back.

Changes to existing code:
- `SharkVideoController.open()` creates the store before the stage is ready (so commands sent while loading are
  queued/applied in order) and implements `hasVideo` as "open or loading".
- `MoviePane.fxml` / `MoviePaneController`: the video area moves onto imgfx `MediaPaneController` so overlays
  autoscale. Playback controls must not float over the drawing area (per `UI.md`).

## Rendering

- The overlay tracks the player's current time and re-syncs on seek, pause and frame step (so paused scrubbing works).
- A record is visible when `|currentTime - elapsedTimeMillis| <= window / 2`.
- **Time window: 200 ms default, a constant for now.** It is centered on `elapsedTimeMillis` (±100 ms). It
  must become a Preference (`UI.md` "Annotation Display > Time Window"); 50 ms is too short to see comfortably.
  The constant carries a code comment saying so.
- `durationMillis` is stored but ignored for display.
- The overlay diffs visible records against existing nodes (add new, remove stale); it never rebuilds all nodes per tick.

## Selection, editing, create, delete

- Click inside a box selects it; with overlapping boxes, the one whose edge is nearest the click wins. Exactly one
  selected box is editable (imgfx `RectangleViewEditor`: drag to move, edges/corners to resize).
- Releasing an edit clips to the video bounds, updates the store, sends `update`.
- Remote `select` replaces the selection; exactly one selected box becomes editable. Unknown UUIDs are ignored.
- A drag starting outside any box draws a new localization (imgfx `RectangleBuilder`) and pauses the video. On
  release: new record with random UUID, send `add`, select it, send `select`. Concept is a placeholder constant
  and color `#FFFFFF` until Preferences exist.
- Delete: Cmd-Delete on macOS, Ctrl-Delete on Windows/Linux. Removes the selected boxes from store and overlay,
  sends `remove`.
- Commands from the remote never pause the video.

## Outgoing messages

`RemoteNotifier` uses the `connect` connection; with none, it logs a warning and drops the message. Sends are
off the FX thread. The timeout is a constant until Preferences exist.

## Errors

- Unknown video: `failed` / `"No video for uuid"` (vcr4j, via `hasVideo`).
- `update` for an unknown localization UUID: ignored. `add` for an existing UUID: treated as `update`.
- Box extending past the video: clipped.

## Testing

- Unit tests: `LocalizationStore` (add/update/remove/clear, time queries, 50k records) and the router (routing,
  FX-thread dispatch, no-echo) with fakes.
- Overlay: manual verification by running the app against VARS Annotation (the repo has no UI test
  infrastructure; not adding any here).
- Existing vcr4j tests plus `LocalizationVideoExistsTest` already cover the protocol-level failure path.

## Open items for the plan

- Add imgfx as a Gradle dependency (and `requires org.mbari.imgfx` in `module-info.java`); imgfx builds against
  JavaFX 27 while jsharktopoda uses 25.0.1 — verify compatibility.
- Confirm how `RVideoIO` exposes sending, for `RemoteNotifier`.
- Confirm that `MediaPlayer.currentTime` updates are fine-grained enough during playback (otherwise drive the
  overlay from a timer during play).
