package org.mbari.jsharktopoda;

import org.mbari.jsharktopoda.etc.vcr4j.FrameCaptureData;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * @author Brian Schlining
 * @since 2017-12-07T14:24:00
 */
public interface FrameCaptureService {

    CompletableFuture<FrameCaptureData> frameCapture(Path target);
}
