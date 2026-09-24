package com.warehouse.service.seo;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Category;
import com.warehouse.entity.CmsPage;
import com.warehouse.enums.CmsPageType;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.CmsPageRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.service.SiteSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * What the server tells a crawler about a storefront URL.
 *
 * <p>The titles must match the SPA's (seo.js) — Google reads the server's first and the
 * rendered one later. And a URL the storefront would not show (inactive category, banner
 * CMS entry) must come back empty, so nginx serves the defaults instead of advertising a
 * page that 404s once the SPA loads.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // settings are stubbed once for every page kind
class StorefrontSeoServiceTest {

    @Mock private SiteSettingService siteSettingService;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private CmsPageRepository cmsPageRepository;

    private StorefrontSeoService service;

    @BeforeEach
    void setUp() {
        when(siteSettingService.getPublicSettings()).thenReturn(Map.of(
                "site_name", "ATS DTM",
                "seo_local_city", "Niğde",
                "seo_local_primary_brands", "Profilo, Simfer",
                "seo_default_meta_description", "",
                "seo_local_description", "1980'den beri Niğde'de beyaz eşya.",
                "seo_canonical_domain", "https://atsdtm.com.tr/"));
        when(productRepository.findActiveByFilters(isNull(), any(), any(), isNull(), isNull(), any()))
                .thenReturn(Page.empty());
        service = new StorefrontSeoService(siteSettingService, productRepository, categoryRepository,
                brandRepository, cmsPageRepository, "http://localhost:3000");
    }

    private static Brand brand(String name, String slug) {
        Brand b = new Brand(name);
        b.setId(1L);
        b.setSlug(slug);
        return b;
    }

    @Test
    @DisplayName("Yetkili marka sayfası 'Niğde Profilo Yetkili Satıcısı' başlığını taşır")
    void anAuthorisedBrandPageTargetsCityPlusBrand() {
        when(brandRepository.findActiveBySlug("profilo-114")).thenReturn(Optional.of(brand("Profilo", "profilo-114")));

        SeoPage page = service.describe(new SeoRoute(SeoRoute.Kind.BRAND, "profilo-114")).orElseThrow();

        assertThat(page.heading()).isEqualTo("Niğde Profilo Yetkili Satıcısı");
        assertThat(page.title()).isEqualTo("Niğde Profilo Yetkili Satıcısı: Modeller ve Fiyatlar | ATS DTM");
        assertThat(page.description()).isEqualTo(
                "Niğde'de Profilo modelleri ve güncel fiyatları. ATS DTM, Niğde Profilo yetkili satıcısıdır.");
        assertThat(page.canonicalUrl())
                .as("canonical domain'deki sondaki / çift eğik çizgi üretmemeli")
                .isEqualTo("https://atsdtm.com.tr/marka/profilo-114");
    }

    @Test
    @DisplayName("Yetkili olmayan marka için 'yetkili satıcı' iddiası yazılmaz")
    void aBrandThatIsNotAuthorisedMakesNoDealerClaim() {
        when(brandRepository.findActiveBySlug("tefal-145")).thenReturn(Optional.of(brand("Tefal", "tefal-145")));

        SeoPage page = service.describe(new SeoRoute(SeoRoute.Kind.BRAND, "tefal-145")).orElseThrow();

        assertThat(page.heading()).isEqualTo("Niğde Tefal Ürünleri");
        assertThat(page.title() + page.description()).doesNotContainIgnoringCase("yetkili");
    }

    @Test
    @DisplayName("Açıklaması olmayan kategori şehirli bir açıklama alır, başlık SPA ile aynıdır")
    void aCategoryWithoutCopyGetsACityDescription() {
        Category category = new Category();
        category.setId(75L);
        category.setName("Buzdolabı");
        category.setSlug("buzdolabi-75");
        category.setActive(true);
        when(categoryRepository.findBySlug("buzdolabi-75")).thenReturn(Optional.of(category));

        SeoPage page = service.describe(new SeoRoute(SeoRoute.Kind.CATEGORY, "buzdolabi-75")).orElseThrow();

        assertThat(page.title()).isEqualTo("Niğde Buzdolabı Modelleri ve Fiyatları | ATS DTM");
        assertThat(page.description()).startsWith("Niğde'de buzdolabı modelleri ve güncel fiyatları.");
    }

    @Test
    @DisplayName("Pasif kategori için sayfa tanımlanmaz")
    void anInactiveCategoryIsNotDescribed() {
        Category category = new Category();
        category.setName("Eski");
        category.setSlug("eski");
        category.setActive(false);
        when(categoryRepository.findBySlug("eski")).thenReturn(Optional.of(category));

        assertThat(service.describe(new SeoRoute(SeoRoute.Kind.CATEGORY, "eski"))).isEmpty();
    }

    @Test
    @DisplayName("Banner kaydı /sayfa altında sayfa gibi sunulmaz")
    void aBannerIsNotAPage() {
        CmsPage banner = new CmsPage();
        banner.setTitle("Kampanya");
        banner.setSlug("kampanya");
        banner.setPageType(CmsPageType.BANNER);
        when(cmsPageRepository.findBySlugAndActiveTrue("kampanya")).thenReturn(Optional.of(banner));

        assertThat(service.describe(new SeoRoute(SeoRoute.Kind.CMS, "kampanya"))).isEmpty();
    }

    @Test
    @DisplayName("Ana sayfa varsayılan açıklama boşsa yerel tanıtım metnine düşer")
    void theHomepageFallsBackToTheLocalDescription() {
        when(brandRepository.findActiveWithStorefrontProducts()).thenReturn(List.of(brand("Profilo", "profilo-114")));
        when(categoryRepository.findAllActive()).thenReturn(List.of());

        SeoPage page = service.describe(SeoRoute.home()).orElseThrow();

        assertThat(page.title()).isEqualTo("Niğde Profilo, Beyaz Eşya & Küçük Ev Aletleri | ATS DTM");
        assertThat(page.heading()).isEqualTo("Niğde Profilo Yetkili Satıcısı");
        assertThat(page.description()).isEqualTo("1980'den beri Niğde'de beyaz eşya.");
        assertThat(page.links()).extracting(SeoPage.Link::href).containsExactly("/marka/profilo-114");
    }
}
