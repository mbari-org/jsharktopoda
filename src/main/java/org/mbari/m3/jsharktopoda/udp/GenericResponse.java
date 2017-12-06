package org.mbari.m3.jsharktopoda.udp;

import com.google.gson.annotations.SerializedName;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

/**
 * Contains all possible fields in the response.
 *
 * @author Brian Schlining
 * @since 2017-12-05T13:17:00
 */
public class GenericResponse {
    private String response;
    private String status;
    private UUID uuid;
    private Video[] videos;
    @SerializedName("elapsed_time_millis") private Duration elapsedTime;
    private UUID imageReferenceUuid;
    private String imageLocation;
    private URL url;

    public GenericResponse() {
    }

    public GenericResponse(String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Video[] getVideos() {
        return videos;
    }

    public void setVideos(Video[] videos) {
        this.videos = videos;
    }

    public Duration getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(Duration elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public UUID getImageReferenceUuid() {
        return imageReferenceUuid;
    }

    public void setImageReferenceUuid(UUID imageReferenceUuid) {
        this.imageReferenceUuid = imageReferenceUuid;
    }

    public String getImageLocation() {
        return imageLocation;
    }

    public void setImageLocation(String imageLocation) {
        this.imageLocation = imageLocation;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    /**
     *
     * @return true if a response is expected. False if not UDP response should be sent.
     */
    public boolean isResponseExpected() {
        return response != null && !response.isEmpty();
    }
}
