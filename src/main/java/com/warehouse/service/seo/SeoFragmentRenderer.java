package com.warehouse.service.seo;

import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Turns a {@link SeoPage} into the two HTML fragments nginx splices into index.html.
 *
 * <p>Every value is escaped: product names and descriptions are typed by staff, and this
 * output lands in the document outside React, where nothing else would escape it.</p>
 */
public final class SeoFragmentRenderer {

    /**
     * react-helmet-async removes head tags carrying this attribute when it takes over, so
     * the SPA replaces these instead of adding a second description and canonical.
     */
    private static final String HELMET_ATTR = " data-rh=\"true\"";

    private SeoFragmentRenderer() {}

    public static String head(SeoPage page) {
        StringBuilder html = new StringBuilder(1024);
        html.append("<title>").append(escape(page.title())).append("</title>\n");
        meta(html, "name", "description", page.description());
        if (!page.canonicalUrl().isEmpty()) {
            html.append("<link rel=\"canonical\" href=\"").append(escape(page.canonicalUrl())).append('"')
                    .append(HELMET_ATTR).append(">\n");
        }
        meta(html, "property", "og:title", page.title());
        meta(html, "property", "og:description", page.description());
        meta(html, "property", "og:url", page.canonicalUrl());
        meta(html, "property", "og:type", page.ogType());
        meta(html, "property", "og:site_name", page.siteName());
        meta(html, "property", "og:locale", "tr_TR");
        meta(html, "property", "og:image", page.imageUrl());
        meta(html, "name", "twitter:card", page.imageUrl().isEmpty() ? "summary" : "summary_large_image");
        meta(html, "name", "twitter:title", page.title());
        meta(html, "name", "twitter:description", page.description());
        meta(html, "name", "twitter:image", page.imageUrl());
        return html.toString();
    }

    /**
     * Readable page content for clients that do not run JavaScript. The SPA replaces the
     * contents of #root on mount, and index.html hides this block once JavaScript is known
     * to be running, so visitors never see it flash.
     */
    public static String body(SeoPage page) {
        StringBuilder html = new StringBuilder(4096);
        html.append("<div class=\"seo-prerender container py-4\">\n");
        html.append("<nav aria-label=\"breadcrumb\"><a href=\"/\">Ana Sayfa</a></nav>\n");
        html.append("<h1>").append(escape(page.heading())).append("</h1>\n");
        if (!page.intro().isEmpty()) {
            html.append("<p>").append(escape(page.intro())).append("</p>\n");
        }
        if (!page.links().isEmpty()) {
            html.append("<h2>").append(escape(page.linksHeading())).append("</h2>\n<ul>\n");
            for (SeoPage.Link link : page.links()) {
                html.append("<li><a href=\"").append(escape(link.href())).append("\">")
                        .append(escape(link.label())).append("</a></li>\n");
            }
            html.append("</ul>\n");
        }
        html.append("</div>\n");
        return html.toString();
    }

    private static void meta(StringBuilder html, String attr, String key, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        html.append("<meta ").append(attr).append("=\"").append(key).append("\" content=\"")
                .append(escape(content)).append('"').append(HELMET_ATTR).append(">\n");
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }
}
