# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

jsharktopoda is a JavaFX video player (part of MBARI's VARS ecosystem) that is driven remotely over UDP. External apps such as VARS Annotation send commands (open, play, seek, frame capture, ...) using the protocol implemented by [vcr4j](https://github.com/mbari-org/vcr4j) (`vcr4j-remote`). The protocol spec lives in the separate Sharktopoda repo (`Requirements/UDP_Remote_Protocol.md`), not here.

## Commands

Requires Java 25 (Gradle toolchain) and uses the Gradle wrapper (Kotlin DSL, `build.gradle.kts`).

```bash
./gradlew clean build          # build + tests
./gradlew run                  # run the app (main class org.mbari.jsharktopoda.JSharktopoda)
./gradlew test                 # all tests
./gradlew test --tests "org.mbari.jsharktopoda.SanityCheck"   # single test class
./gradlew clean jpackage --info   # native installer with bundled JDK -> build/jpackage/
./gradlew dependencyUpdates    # check for newer dependency versions
```

There is essentially no test coverage (only a placeholder JUnit 4 `SanityCheck`); no linter is configured.

macOS signing: if `MAC_CODE_SIGNER` is set, the `jpackageImage` task codesigns the runtime binaries and app bundle using `src/jpackage/macos/java.entitlements`. Notarization steps (including the `dot_clean` workaround for AppleDouble `._*` files) are in README.md.

## Architecture

Command flow:

```
UDP command -> vcr4j VideoControl -> SharkVideoController -> MovieStageController -> MoviePaneController / MediaPlayer
```

- `JSharktopoda` — `Application` entry point. Builds the toolbar stage (power, settings, open file, open URL) and owns the UDP listener lifecycle. The UDP port (default 8800) is persisted via `java.util.prefs`.
- `etc/vcr4j/SharkVideoController` — implements vcr4j's `VideoController`; the bridge between remote commands and the UI. Keeps a `Map<UUID, MovieStageController>`, one per open video window, keyed by the UUID the remote client uses to address it.
- `MovieStageController` — wraps a `MoviePaneController` in a `Stage` (window lifecycle, sizing).
- `MoviePaneController` — FXML-backed (`src/main/resources/fxml/MoviePane.fxml`, `css/MoviePane.css`) playback UI; frame capture is done through `MediaView.snapshot()`. Implements `FrameCaptureService`.
- `etc/vcr4j/*Impl`, `FrameCaptureData` — adapter types implementing vcr4j interfaces (frame capture, video info).
- `etc/javafx`, `etc/jdk` — small utility helpers (FXML loading, `PlatformExecutor` for FX-thread dispatch, Material icons, image conversion).
- `VideoPlayer` — standalone `Application` that opens a single movie from `args[0]`; a dev/debug entry point, not the shipped app.

UDP handlers run off the JavaFX thread, so UI and `MediaPlayer` operations must be dispatched to the FX thread (see `PlatformExecutor`/`JFXUtilities`); results flow back to vcr4j via RxJava 3 streams/`CompletableFuture`.

## Build/module notes

- The project is a named, open JPMS module (`src/main/java/module-info.java`). New dependencies must be added both to `build.gradle.kts` and as `requires` in `module-info.java`, and new packages that are used reflectively (FXML) live in an `open` module already.
- JavaFX is wired through the `org.openjfx.javafxplugin`; the installer is built with `org.beryx.jlink` (`addExtraDependencies("javafx")`). Runtime JVM arg is `-Xms1g`.
- The version is set in `build.gradle.kts` (`version = "2.1.0"`); the jpackage `--app-version` derives from it.
- `.github/modernize/` holds tooling artifacts from a Java upgrade, not project code.
