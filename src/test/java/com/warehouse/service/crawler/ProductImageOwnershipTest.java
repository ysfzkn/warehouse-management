package com.warehouse.service.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A retailer product page is mostly other products. These cases come from a live listing
 * that offered twenty images for one thermos: one was the thermos, ten were the
 * neighbouring models in the "similar items" strip, and nine were menu icons and
 * app-store badges. Importing that would have put nineteen wrong pictures on the product.
 */
class ProductImageOwnershipTest {

    private static final String PAGE =
            "https://www.carsi24.com/lifetime-guaranteed-active-stainless-steel-thermos-vanilla-035-lt-12-oz-866mnz1911942";

    @Test
    void keepsOnlyTheImagesCarryingThisPagesProductId() {
        List<String> found = List.of(
                "https://carsi24.witcdn.net/vanilla-035-lt-12-oz-866mnz1911942-tanq-1413725-31-O.jpg",
                "https://carsi24.witcdn.net/titan-black-060-lt-20-oz-866mnz1911947-tanq-1413730-31-O.jpg",
                "https://carsi24.witcdn.net/vanilla-060-lt-20-oz-866mnz1911946-tanq-1413729-31-O.jpg");

        List<String> kept = ProductImageCrawlerService.keepThisProductsImages(found, PAGE);

        assertThat(kept).containsExactly(found.get(0));
    }

    @Test
    void leavesThePageAloneWhenNoImageCarriesTheId() {
        // Profilo addresses the product as /product/FRGA103B but serves photographs named
        // by an internal code. Nothing matches, so nothing may be thrown away.
        List<String> found = List.of(
                "https://media3.bsh-group.com/Product_Shots/16991718_Template_A.png",
                "https://media3.bsh-group.com/Product_Shots/16991718_Template_B.png");

        List<String> kept = ProductImageCrawlerService.keepThisProductsImages(
                found, "https://www.profilo.com/tr/tr/product/FRGA103B");

        assertThat(kept).isEqualTo(found);
    }

    @Test
    void ignoresWordsThatOnlyLookLikeIdentifiers() {
        // "thermos", "product" and "lifetime" are long but carry no digit, so they must
        // never be used to decide which photographs belong to the page.
        List<String> found = List.of(
                "https://cdn.example.com/a-thermos-shot.jpg",
                "https://cdn.example.com/another-product-shot.jpg");

        List<String> kept = ProductImageCrawlerService.keepThisProductsImages(
                found, "https://shop.example.com/lifetime-thermos-product-page");

        assertThat(kept).isEqualTo(found);
    }

    @Test
    void doesNothingToASingleImageOrAMissingUrl() {
        List<String> one = List.of("https://cdn.example.com/x-999999.jpg");

        assertThat(ProductImageCrawlerService.keepThisProductsImages(one, PAGE)).isEqualTo(one);
        assertThat(ProductImageCrawlerService.keepThisProductsImages(one, null)).isEqualTo(one);
        assertThat(ProductImageCrawlerService.keepThisProductsImages(List.of(), PAGE)).isEmpty();
    }
}
