package org.mbari.m3.jsharktopoda.javafx;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public record FrameCaptureData(Path saveLocation, Long elapsedTimeMillis, BufferedImage bufferedImage) {
}
