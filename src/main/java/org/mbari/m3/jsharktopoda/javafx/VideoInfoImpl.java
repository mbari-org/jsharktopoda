package org.mbari.m3.jsharktopoda.javafx;

import org.mbari.vcr4j.remote.control.commands.VideoInfo;

import java.net.URL;
import java.util.UUID;

public record VideoInfoImpl(UUID uuid, URL url, Long durationMillis, Double frameRate) implements VideoInfo {
    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public Long getDurationMillis() {
        return durationMillis;
    }

    @Override
    public Double getFrameRate() {
        return frameRate;
    }

    @Override
    public Boolean isKey() {
        return false;
    }
}
