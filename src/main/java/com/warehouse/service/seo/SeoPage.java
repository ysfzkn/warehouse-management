package com.warehouse.service.seo;

import java.util.List;

/**
 * What a crawler should learn about one storefront page before any JavaScript runs.
 *
 * @param title        full document title, site name included
 * @param description  meta description; may be empty
 * @param canonicalUrl absolute URL of the page
 * @param imageUrl     absolute og:image URL, or empty
 * @param ogType       Open Graph type (website, product, article)
 * @param siteName     og:site_name
 * @param heading      visible H1
 * @param intro        plain-text lead paragraph; may be empty
 * @param linksHeading heading above {@code links}
 * @param links        internal links the page leads to (products, categories, brands)
 */
public record SeoPage(
        String title,
        String description,
        String canonicalUrl,
        String imageUrl,
        String ogType,
        String siteName,
        String heading,
        String intro,
        String linksHeading,
        List<Link> links) {

    public SeoPage {
        links = List.copyOf(links);
    }

    public record Link(String label, String href) {}
}
