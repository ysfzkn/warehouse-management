package com.warehouse.controller;

import com.warehouse.entity.Category;
import com.warehouse.entity.CmsPage;
import com.warehouse.entity.Product;
import com.warehouse.enums.CmsPageType;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.CmsPageRepository;
import com.warehouse.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class SitemapController {

    private static final DateTimeFormatter W3C_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CmsPageRepository cmsPageRepository;

    public SitemapController(ProductRepository productRepository,
                             CategoryRepository categoryRepository,
                             CmsPageRepository cmsPageRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.cmsPageRepository = cmsPageRepository;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Active products
        List<Product> products = productRepository.findAllActive();
        for (Product product : products) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(baseUrl)).append("/store/product/")
               .append(escapeXml(product.getSlug())).append("</loc>\n");
            if (product.getUpdatedAt() != null) {
                xml.append("    <lastmod>").append(product.getUpdatedAt().format(W3C_DATE)).append("</lastmod>\n");
            }
            xml.append("    <changefreq>weekly</changefreq>\n");
            xml.append("    <priority>0.8</priority>\n");
            xml.append("  </url>\n");
        }

        // Active categories
        List<Category> categories = categoryRepository.findAllActive();
        for (Category category : categories) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(baseUrl)).append("/store/category/")
               .append(escapeXml(category.getSlug())).append("</loc>\n");
            if (category.getUpdatedAt() != null) {
                xml.append("    <lastmod>").append(category.getUpdatedAt().format(W3C_DATE)).append("</lastmod>\n");
            }
            xml.append("    <changefreq>weekly</changefreq>\n");
            xml.append("    <priority>0.6</priority>\n");
            xml.append("  </url>\n");
        }

        // Published CMS pages (CONTENT, LEGAL, FAQ — exclude banners)
        List<CmsPage> cmsPages = cmsPageRepository.findAll().stream()
                .filter(CmsPage::isActive)
                .filter(page -> page.getPageType() != CmsPageType.BANNER)
                .toList();
        for (CmsPage page : cmsPages) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(baseUrl)).append("/store/page/")
               .append(escapeXml(page.getSlug())).append("</loc>\n");
            if (page.getUpdatedAt() != null) {
                xml.append("    <lastmod>").append(page.getUpdatedAt().format(W3C_DATE)).append("</lastmod>\n");
            }
            xml.append("    <changefreq>monthly</changefreq>\n");
            xml.append("    <priority>0.5</priority>\n");
            xml.append("  </url>\n");
        }

        xml.append("</urlset>");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        String content = "User-agent: *\n" +
                "Allow: /store/\n" +
                "Disallow: /api/\n" +
                "Disallow: /admin/\n" +
                "Sitemap: " + baseUrl + "/sitemap.xml\n";

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
