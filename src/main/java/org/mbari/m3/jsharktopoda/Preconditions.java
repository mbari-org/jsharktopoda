package org.mbari.m3.jsharktopoda;

/**
 * @author Brian Schlining
 * @since 2016-01-28T12:41:00
 */
public class Preconditions {
    public static void checkArgument(boolean arg, String msg) {
        if (!arg) {
            throw new IllegalArgumentException(msg);
        }
    }
}
