open module jsharktopoda {

  requires com.google.gson;
  requires io.reactivex.rxjava3;
  requires java.naming;
  requires java.prefs;
  requires java.sql;
  requires javafx.base;
  requires javafx.controls;
  requires javafx.fxml;
  requires javafx.graphics;
  requires javafx.media;
  requires javafx.swing;
//  requires mbarix4j;
  requires org.kordamp.ikonli.javafx;
  requires org.kordamp.ikonli.material;
  requires transitive vcr4j.core;
  requires transitive vcr4j.remote;
  requires org.mbari.jcommons;

  exports org.mbari.jsharktopoda;
  exports org.mbari.jsharktopoda.etc.vcr4j;
  exports org.mbari.jsharktopoda.etc.javafx;
  exports org.mbari.jsharktopoda.etc.jdk;

}
