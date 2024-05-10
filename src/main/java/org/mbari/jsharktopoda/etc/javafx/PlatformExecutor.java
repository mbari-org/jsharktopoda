package org.mbari.jsharktopoda.etc.javafx;

import javafx.application.Platform;

import java.util.concurrent.Executor;

/**
 * @author Brian Schlining
 * @since 2017-12-06T16:38:00
 */
public class PlatformExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
        Platform.runLater(command);
    }
}
