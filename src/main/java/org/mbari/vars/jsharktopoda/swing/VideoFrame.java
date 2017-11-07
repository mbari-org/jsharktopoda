package org.mbari.vars.jsharktopoda.swing;

import uk.co.caprica.vlcj.component.DirectMediaPlayerComponent;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.direct.BufferFormatCallback;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;
import uk.co.caprica.vlcj.player.direct.RenderCallback;
import uk.co.caprica.vlcj.player.direct.RenderCallbackAdapter;
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * @author Brian Schlining
 * @since 2017-11-07T11:29:00
 */
public class VideoFrame {

    private final String pathToVideo;
    private final JFrame frame;
    private final JPanel videoSurface;
    private volatile BufferedImage image;
    private final DirectMediaPlayerComponent mediaPlayerComponent;
    private int width = 600;
    private int height = 400;

    public VideoFrame(String pathToVideo) {
        this.pathToVideo = pathToVideo;

        frame = new JFrame(pathToVideo);
        frame.setBounds(100, 100, width, height);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                width = e.getComponent().getWidth();
                height = e.getComponent().getHeight();
            }
        });
        videoSurface = new VideoSurfacePanel();
        frame.setContentPane(videoSurface);
        image = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .createCompatibleImage(width, height);

        BufferFormatCallback bufferFormatCallback = (w, h) -> new RV32BufferFormat(width, height);
        mediaPlayerComponent = new DirectMediaPlayerComponent(bufferFormatCallback) {
            @Override
            protected RenderCallback onGetRenderCallback() {
                return new BufferedImageRenderCallbackAdapter();
            }
        };
        frame.setVisible(true);
        mediaPlayerComponent.getMediaPlayer().playMedia(pathToVideo);

    }

    private void updateSize(Component component) {
        width = component.getWidth();
        height = component.getHeight();

    }

    public static void main(String[] args) {
        new NativeDiscovery().discover();
        SwingUtilities.invokeLater(() -> new VideoFrame(args[0]));
    }

    private class VideoSurfacePanel extends JPanel {

        private VideoSurfacePanel() {
            setBackground(Color.black);
            setOpaque(true);
            setPreferredSize(new Dimension(width, height));
            setMinimumSize(new Dimension(width, height));
            setMaximumSize(new Dimension(width, height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D)g;
            g2.drawImage(image, null, 0, 0);
        }
    }

    private class BufferedImageRenderCallbackAdapter extends RenderCallbackAdapter {

        private BufferedImageRenderCallbackAdapter() {
            super(new int[width * height]);
        }

        @Override
        protected void onDisplay(DirectMediaPlayer mediaPlayer, int[] rgbBuffer) {
            // Simply copy buffer to the image and repaint
            image.setRGB(0, 0, width, height, rgbBuffer, 0, width);
            videoSurface.repaint();
        }
    }


}
