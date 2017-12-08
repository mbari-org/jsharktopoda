package org.mbari.m3.jsharktopoda;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author Brian Schlining
 * @since 2017-12-07T14:24:00
 */
public interface FrameCaptureService {

    BufferedImage frameCapture(File target) throws IOException, InterruptedException, TimeoutException, ExecutionException;
}
