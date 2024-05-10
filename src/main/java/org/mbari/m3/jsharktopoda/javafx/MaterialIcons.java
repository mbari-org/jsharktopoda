package org.mbari.m3.jsharktopoda.javafx;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material.Material;

import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * MaterialIcons
 */
public class MaterialIcons {

  private MaterialIcons() {
  }

  private static final Color color = Color.BLACK;


  public static final Text POWER_SETTINGS_NEW = FontIcon.of(Material.POWER_SETTINGS_NEW, 100, color);
  public static final Text SETTINGS = FontIcon.of(Material.SETTINGS, 100, color);
  public static final Text COMPUTER = FontIcon.of(Material.COMPUTER, 100, color);
  public static final Text CLOUD = FontIcon.of(Material.CLOUD, 100, color);
  public static final Text VOLUME_UP = FontIcon.of(Material.VOLUME_UP, 20, color);
  public static final Text VOLUME_DOWN= FontIcon.of(Material.VOLUME_DOWN, 20, color);
  public static final Text FAST_FORWARD = FontIcon.of(Material.FAST_FORWARD, 30, color);
  public static final Text FAST_REWIND = FontIcon.of(Material.FAST_REWIND, 30, color);
  public static final Text PLAY_ARROW_50PX = FontIcon.of(Material.PLAY_ARROW, 50, color);
  public static final Text PLAY_ARROW = FontIcon.of(Material.PLAY_ARROW, 30, color);
  public static final Text PAUSE = FontIcon.of(Material.PAUSE, 30, color);
}