package org.mbari.m3.jsharktopoda.javafx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author Brian Schlining
 * @since 2017-12-07T14:24:00
 */
public interface FrameCaptureService {

    CompletableFuture<FrameCaptureData> frameCapture(Path target);
}
