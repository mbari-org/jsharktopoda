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
