open module jsharktopoda {
  requires com.jfoenix;
  // requires de.jensd.fx.fontawesomefx.commons;
  // requires de.jensd.fx.fontawesomefx.materialicons;
  requires java.prefs;
  requires java.sql;
  requires javafx.controls;
  requires javafx.fxml;
  requires javafx.media;
  requires javafx.swing;
  requires org.kordamp.iconli.core;
  requires org.kordamp.ikonli.javafx;
  // add icon pack modules
  requires org.kordamp.ikonli.material;
  requires vlcj;

  exports org.mbari.m3.jsharktopoda.javafx;
}