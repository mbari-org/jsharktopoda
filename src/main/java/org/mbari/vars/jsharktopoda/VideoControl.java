package org.mbari.vars.jsharktopoda;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import de.jensd.fx.glyphs.GlyphsFactory;
import de.jensd.fx.glyphs.materialicons.MaterialIcon;
import de.jensd.fx.glyphs.materialicons.utils.MaterialIconFactory;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;

import java.time.Duration;

/**
 * @author Brian Schlining
 * @since 2016-08-16T15:28:00
 */
public class VideoControl extends Pane {


    JFXSlider volumeSlider;
    JFXSlider scrubber;
    Button rewindButton;
    Button fastForwardButton;
    Button playButton;
    Label elapsedTimeLabel = new Label("00:00");
    Label durationLabel = new Label("00:00");
    private GlyphsFactory glyphsFactory = MaterialIconFactory.get();
    private Color color = Color.LIGHTGRAY;
    Text volumeUpIcon = glyphsFactory.createIcon(MaterialIcon.VOLUME_UP, "20px");
    Text volumeDownIcon = glyphsFactory.createIcon(MaterialIcon.VOLUME_DOWN, "20px");

    public VideoControl() {
        // orange: "0xBF360C"

        setStyle("-fx-background-color: #263238;");
        setPrefSize(440, 80);
        volumeDownIcon.setFill(color);
        volumeUpIcon.setFill(color);
        elapsedTimeLabel.setTextFill(color);
        durationLabel.setTextFill(color);

        doLayout();

        getChildren().addAll(volumeDownIcon,
                volumeUpIcon,
                getRewindButton(),
                getPlayButton(),
                getFastForwardButton(),
                elapsedTimeLabel,
                durationLabel,
                getVolumeSlider(),
                getScrubber());
    }

    private void doLayout() {


        volumeDownIcon.relocate(5, 25);
        getVolumeSlider().relocate(25, 19);
        volumeUpIcon.relocate(90, 25);
        getRewindButton().relocate(145, 8);
        getPlayButton().relocate(195, 0);
        getFastForwardButton().relocate(260, 8);
        elapsedTimeLabel.relocate(9, 47);
        getScrubber().relocate(55, 47);
        durationLabel.relocate(395, 47);

    }

    protected JFXSlider getVolumeSlider() {
        if (volumeSlider == null) {
            volumeSlider = new JFXSlider(0, 100, 0);
            volumeSlider.setPrefWidth(60);
            volumeSlider.setIndicatorPosition(JFXSlider.IndicatorPosition.RIGHT);
        }
        return volumeSlider;
    }

    protected Button getFastForwardButton() {
        if (fastForwardButton == null) {
            Text icon = glyphsFactory.createIcon(MaterialIcon.FAST_FORWARD, "30px");
            icon.setFill(color);
            fastForwardButton = new JFXButton();
            fastForwardButton.setGraphic(icon);
            fastForwardButton.setPrefSize(30, 30);
            //GlyphIcon
        }
        return fastForwardButton;
    }

    protected Button getRewindButton() {
        if (rewindButton == null) {
            Text icon = glyphsFactory.createIcon(MaterialIcon.FAST_REWIND, "30px");
            icon.setFill(color);
            rewindButton = new JFXButton();
            rewindButton.setGraphic(icon);
            rewindButton.setPrefSize(30, 30);
            //GlyphIcon
        }
        return rewindButton;
    }

    protected Button getPlayButton() {
        if (playButton == null) {
            Text icon = glyphsFactory.createIcon(MaterialIcon.PLAY_ARROW, "50px");
            icon.setFill(color);
            playButton = new JFXButton();
            playButton.setGraphic(icon);
            playButton.setPrefSize(30, 30);
        }
        return playButton;
    }

    public JFXSlider getScrubber() {
        if (scrubber == null) {
            scrubber = new JFXSlider(0, 1000, 0);
            scrubber.setPrefWidth(325);

        }
        return scrubber;

    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        Media media = mediaPlayer.getMedia();

        // Show the total media time
        long seconds = (long) mediaPlayer.getMedia().getDuration().toSeconds();
        String s = formatSeconds(seconds);
        durationLabel.setText(s);

        // Configure the scrubber for the media time and bind it
        JFXSlider slider = getScrubber();
        slider.setMax(media.getDuration().toMillis());

        slider.valueProperty().addListener(observable -> {
            if (slider.isValueChanging()) {
                mediaPlayer.seek(javafx.util.Duration.millis(slider.getValue()));
            }
            else {
                slider.setValue(mediaPlayer.getCurrentTime().toMillis());
            }
        });

        // Bind media to elapsed time label
        mediaPlayer.currentTimeProperty()
                .addListener((observable, oldValue, newValue) -> durationLabel.setText(formatSeconds((long) newValue.toSeconds())));
    }

    public void setDirectMediaPlayer(uk.co.caprica.vlcj.player.MediaPlayer mediaPlayer) {
        long millis = mediaPlayer.getMediaMeta().getLength();
        long seconds = millis / 1000;
        String s = formatSeconds(seconds);
        durationLabel.setText(s);

        JFXSlider slider = getScrubber();
        slider.setMax(millis);

        slider.valueProperty().addListener(observable -> {
            if (slider.isValueChanging() || mediaPlayer.isSeekable()) {
                float position = (float) (slider.getValue() / (double) millis);
                mediaPlayer.setPosition(position);
            }
            else {
                long m = (long) (millis * mediaPlayer.getPosition());
                slider.setValue(m);
            }
        });





    }

    private String formatSeconds(long seconds) {
        return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
    }
}
