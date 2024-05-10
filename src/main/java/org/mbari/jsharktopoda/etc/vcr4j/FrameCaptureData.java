package org.mbari.jsharktopoda.etc.vcr4j;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public record FrameCaptureData(Path saveLocation, Long elapsedTimeMillis, BufferedImage bufferedImage) {
}
