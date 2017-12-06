package org.mbari.m3.jsharktopoda.udp;

import java.net.URL;
import java.util.UUID;

/**
 * @author Brian Schlining
 * @since 2017-12-05T13:18:00
 */
public class Video {
    private UUID uuid;
    private URL url;

    public Video(UUID uuid, URL url) {
        this.uuid = uuid;
        this.url = url;
    }

    public Video() {
    }

    public UUID getUuid() {
        return uuid;
    }

    public URL getUrl() {
        return url;
    }
}



