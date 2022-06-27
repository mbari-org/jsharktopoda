package org.mbari.m3.jsharktopoda.javafx;

import org.mbari.vcr4j.remote.control.commands.FrameCapture;

import java.util.UUID;

public record FrameCaptureImpl(UUID videoUuid,
                               UUID imageReferenceUuid,
                               String imageLocation,
                               Long elapsedTimeMillis) implements FrameCapture {

    @Override
    public UUID getUuid() {
        return videoUuid;
    }

    @Override
    public String getImageLocation() {
        return imageLocation;
    }

    @Override
    public UUID getImageReferenceUuid() {
        return imageReferenceUuid;
    }

    @Override
    public Long getElapsedTimeMillis() {
        return elapsedTimeMillis;
    }
}
