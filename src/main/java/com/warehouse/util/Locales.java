package com.warehouse.util;

import java.util.Locale;

/**
 * The locales this application formats and compares text with.
 *
 * <p>Turkish casing is not a cosmetic choice here. {@code "I".toLowerCase()} yields {@code "i"}
 * under the default locale and {@code "ı"} under Turkish, so a comparison that picks the wrong
 * one silently fails to match names, provinces and carrier slugs. Every call site therefore has
 * to pass a locale explicitly — and they were each building their own {@code new Locale("tr",
 * "TR")}, which both duplicated the value seven times and used a constructor deprecated since
 * Java 19.
 *
 * <p>One constant, built the supported way, is the whole point of this class.
 */
public final class Locales {

    /** Turkish. Use for every {@code toLowerCase}, {@code toUpperCase} and text format. */
    public static final Locale TR = Locale.of("tr", "TR");

    private Locales() {}
}
