package com.warehouse.service.seo;

import com.warehouse.constants.SettingKeys;
import com.warehouse.entity.Brand;
import com.warehouse.entity.Category;
import com.warehouse.entity.CmsPage;
import com.warehouse.entity.Product;
import com.warehouse.enums.CmsPageType;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.CmsPageRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.service.SiteSettingService;
import com.warehouse.util.Locales;
import com.warehouse.util.ProductImageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Describes storefront pages for the HTML the server sends before the SPA boots.
 *
 * <p>The storefront is a client-rendered SPA: without this, every URL answers with the same
 * static title and an empty body, and a crawler has to render JavaScript to tell a
 * refrigerator from the homepage. Link previews (WhatsApp, Facebook) never render it at all.</p>
 */
@Service
public class StorefrontSeoService {

    public static final String CACHE_NAME = "seoPage";

    /** Enough to link a whole category from its page; the SPA paginates the rest. */
    private static final int LISTED_PRODUCT_LIMIT = 60;
    private static final int META_DESCRIPTION_LENGTH = 160;
    private static final int INTRO_LENGTH = 1500;
    private static final String DEFAULT_SITE_NAME = "Mağaza";
    private static final String OG_WEBSITE = "website";
    private static final String PRODUCTS_HEADING = "Ürünler";

    private final SiteSettingService siteSettingService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CmsPageRepository cmsPageRepository;
    private final String baseUrl;

    public StorefrontSeoService(SiteSettingService siteSettingService,
                                ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                BrandRepository brandRepository,
                                CmsPageRepository cmsPageRepository,
                                @Value("${app.base-url:http://localhost:3000}") String baseUrl) {
        this.siteSettingService = siteSettingService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.cmsPageRepository = cmsPageRepository;
        this.baseUrl = baseUrl;
    }

    /** Empty when the slug names nothing the storefront would show. */
    @Cacheable(cacheNames = CACHE_NAME, key = "#route")
    @Transactional(readOnly = true)
    public Optional<SeoPage> describe(SeoRoute route) {
        Context ctx = new Context(siteSettingService.getPublicSettings(), baseUrl);
        return switch (route.kind()) {
            case HOME -> Optional.of(home(ctx));
            case PRODUCT -> productRepository.findBySlug(route.slug()).map(p -> product(ctx, p));
            case CATEGORY -> categoryRepository.findBySlug(route.slug())
                    .filter(Category::isActive)
                    .map(c -> category(ctx, c));
            case BRAND -> brandRepository.findActiveBySlug(route.slug()).map(b -> brand(ctx, b));
            case CMS -> cmsPageRepository.findBySlugAndActiveTrue(route.slug())
                    .filter(p -> p.getPageType() != CmsPageType.BANNER)
                    .map(p -> cms(ctx, p));
        };
    }

    private SeoPage home(Context ctx) {
        String leadBrand = ctx.primaryBrands().stream().findFirst().orElse("");
        String title = LocalSeoText.firstNonBlank(
                ctx.get(SettingKeys.SEO_META_TITLE_HOME),
                ctx.city().isEmpty() ? "" : ctx.city() + " "
                        + (leadBrand.isEmpty() ? "" : leadBrand + ", ") + "Beyaz Eşya & Küçük Ev Aletleri");
        String heading = ctx.city().isEmpty() || leadBrand.isEmpty()
                ? ctx.siteName()
                : ctx.city() + " " + leadBrand + " Yetkili Satıcısı";

        List<SeoPage.Link> links = new ArrayList<>();
        brandRepository.findActiveWithStorefrontProducts().stream()
                .filter(b -> ctx.isPrimaryBrand(b.getName()))
                .forEach(b -> links.add(new SeoPage.Link(
                        LocalSeoText.withCity(ctx.city(), b.getName()), "/marka/" + b.getSlug())));
        categoryRepository.findAllActive().stream()
                .filter(c -> c.getParent() == null)
                .forEach(c -> links.add(new SeoPage.Link(
                        LocalSeoText.withCity(ctx.city(), c.getName()), "/kategori/" + c.getSlug())));

        return new SeoPage(
                ctx.fullTitle(title),
                ctx.description(null),
                ctx.absolute("/"),
                ctx.absolute(ctx.get(SettingKeys.SEO_DEFAULT_OG_IMAGE, SettingKeys.SITE_LOGO_URL)),
                OG_WEBSITE,
                ctx.siteName(),
                heading,
                ctx.description(null),
                "Markalar ve Kategoriler",
                links);
    }

    private SeoPage product(Context ctx, Product product) {
        String brandName = product.getBrand() != null ? product.getBrand().getName() : "";
        String title = LocalSeoText.firstNonBlank(
                product.getMetaTitle(),
                LocalSeoText.withCity(ctx.city(), LocalSeoText.withBrand(product.getName(), brandName)));
        String description = ctx.description(LocalSeoText.firstNonBlank(
                product.getMetaDescription(),
                product.getShortDescription(),
                LocalSeoText.plainText(product.getDescription(), META_DESCRIPTION_LENGTH)));
        String image = product.getImages() == null ? "" : ProductImageUtil.displayCover(product.getImages())
                .map(img -> "/api/admin/products/images/" + img.getId() + "/view")
                .orElse("");

        List<SeoPage.Link> links = new ArrayList<>();
        if (product.getCategory() != null) {
            links.add(new SeoPage.Link(product.getCategory().getName(), "/kategori/" + product.getCategory().getSlug()));
        }
        if (product.getBrand() != null) {
            links.add(new SeoPage.Link(brandName, "/marka/" + product.getBrand().getSlug()));
        }

        return new SeoPage(
                ctx.fullTitle(title),
                description,
                ctx.absolute("/urun/" + product.getSlug()),
                ctx.absolute(image),
                "product",
                ctx.siteName(),
                product.getName(),
                LocalSeoText.firstNonBlank(
                        LocalSeoText.plainText(product.getDescription(), INTRO_LENGTH),
                        product.getShortDescription()),
                "Kategori ve Marka",
                links);
    }

    private SeoPage category(Context ctx, Category category) {
        String heading = LocalSeoText.withCity(ctx.city(), category.getName() + " Modelleri ve Fiyatları");
        String generated = ctx.listingDescription(category.getName().toLowerCase(Locales.TR));
        String description = LocalSeoText.firstNonBlank(
                category.getMetaDescription(),
                LocalSeoText.plainText(category.getDescription(), META_DESCRIPTION_LENGTH),
                generated);

        return new SeoPage(
                ctx.fullTitle(LocalSeoText.firstNonBlank(category.getMetaTitle(), heading)),
                description,
                ctx.absolute("/kategori/" + category.getSlug()),
                ctx.absolute(category.getImageUrl()),
                OG_WEBSITE,
                ctx.siteName(),
                heading,
                LocalSeoText.firstNonBlank(LocalSeoText.plainText(category.getDescription(), INTRO_LENGTH), generated),
                PRODUCTS_HEADING,
                productLinks(category.getId(), null));
    }

    private SeoPage brand(Context ctx, Brand brand) {
        boolean authorised = ctx.isPrimaryBrand(brand.getName());
        String heading = LocalSeoText.withCity(ctx.city(),
                brand.getName() + (authorised ? " Yetkili Satıcısı" : " Ürünleri"));
        String title = authorised ? heading + ": Modeller ve Fiyatlar" : heading + " ve Fiyatları";
        String generated = ctx.brandDescription(brand.getName(), authorised);

        return new SeoPage(
                ctx.fullTitle(title),
                LocalSeoText.firstNonBlank(brand.getDescription(), generated),
                ctx.absolute("/marka/" + brand.getSlug()),
                ctx.absolute(brand.getLogoUrl()),
                OG_WEBSITE,
                ctx.siteName(),
                heading,
                LocalSeoText.firstNonBlank(brand.getDescription(), generated),
                PRODUCTS_HEADING,
                productLinks(null, brand.getId()));
    }

    private SeoPage cms(Context ctx, CmsPage page) {
        return new SeoPage(
                ctx.fullTitle(LocalSeoText.firstNonBlank(page.getMetaTitle(), page.getTitle())),
                ctx.description(page.getMetaDescription()),
                ctx.absolute("/sayfa/" + page.getSlug()),
                ctx.absolute(page.getBannerImageUrl()),
                "article",
                ctx.siteName(),
                page.getTitle(),
                LocalSeoText.plainText(page.getContent(), INTRO_LENGTH),
                "",
                List.of());
    }

    /** Same visibility and order as the storefront listing, so the links match what a visitor sees. */
    private List<SeoPage.Link> productLinks(Long categoryId, Long brandId) {
        return productRepository.findActiveByFilters(null, categoryId, brandId, null, null,
                        PageRequest.of(0, LISTED_PRODUCT_LIMIT))
                .map(p -> new SeoPage.Link(p.getName(), "/urun/" + p.getSlug()))
                .getContent();
    }

    /** Settings read once per page, with the fallbacks the SPA applies. */
    private record Context(Map<String, String> settings, String baseUrl) {

        String get(String... keys) {
            return LocalSeoText.firstNonBlank(Arrays.stream(keys).map(settings::get).toArray(String[]::new));
        }

        String city() {
            return get(SettingKeys.SEO_LOCAL_CITY);
        }

        String siteName() {
            return LocalSeoText.firstNonBlank(
                    get(SettingKeys.SITE_NAME, SettingKeys.SEO_ORGANIZATION_NAME), DEFAULT_SITE_NAME);
        }

        String fullTitle(String title) {
            return title.isBlank() ? siteName() : title + " | " + siteName();
        }

        /** Page's own description, else the site default, else the local business blurb. */
        String description(String own) {
            return LocalSeoText.firstNonBlank(own,
                    get(SettingKeys.SEO_DEFAULT_META_DESCRIPTION, SettingKeys.SEO_LOCAL_DESCRIPTION));
        }

        /** "Niğde'de buzdolabı modelleri ve güncel fiyatları. ATS DTM güvencesiyle …" */
        String listingDescription(String subject) {
            return listingLead(subject) + " " + siteName() + " güvencesiyle inceleyin, kolayca sipariş verin.";
        }

        String brandDescription(String brand, boolean authorised) {
            if (!authorised || city().isEmpty()) {
                return listingDescription(brand);
            }
            return listingLead(brand) + " " + siteName() + ", " + city() + " " + brand + " yetkili satıcısıdır.";
        }

        private String listingLead(String subject) {
            String lead = city().isEmpty() ? subject : LocalSeoText.locative(city()) + " " + subject;
            return lead + " modelleri ve güncel fiyatları.";
        }

        List<String> primaryBrands() {
            return Arrays.stream(get(SettingKeys.SEO_LOCAL_PRIMARY_BRANDS).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        boolean isPrimaryBrand(String name) {
            String wanted = name == null ? "" : name.trim().toLowerCase(Locales.TR);
            return primaryBrands().stream().anyMatch(b -> b.toLowerCase(Locales.TR).equals(wanted));
        }

        String absolute(String url) {
            if (url == null || url.isBlank()) {
                return "";
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
            String origin = LocalSeoText.firstNonBlank(get(SettingKeys.SEO_CANONICAL_DOMAIN), baseUrl)
                    .replaceAll("/+$", "");
            return origin + (url.startsWith("/") ? url : "/" + url);
        }
    }
}
