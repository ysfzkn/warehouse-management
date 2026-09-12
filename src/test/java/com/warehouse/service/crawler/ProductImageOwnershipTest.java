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
    void usesTheSlugWhenThePageHasNoIdInItsAddress() {
        // fakir.com.tr/atomic-rondo-rosie names its files after the slug, and the red
        // sibling from the "similar items" strip is a different slug.
        List<String> found = List.of(
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-9005-30-O.jpg",
                "https://www.fakir.com.tr/img/atomic-rondo-rouge-fakir-9006-30-O.jpg",
                "https://www.fakir.com.tr/img/storchop-dograyici-cream-700w-fakir-9008-41-O.jpg");

        List<String> kept = ProductImageCrawlerService.keepThisProductsImages(
                found, "https://www.fakir.com.tr/atomic-rondo-rosie");

        assertThat(kept).containsExactly(found.get(0));
    }

    @Test
    void matchesAFileNamedAfterTheTailOfTheSlug() {
        // Simfer serves /70-lt-pro-beyaz-turbo-cift-cam-mekanik-saat-lamba but names the
        // file from "turbo-..." onwards; the other oven in the strip shares none of it.
        List<String> found = List.of(
                "https://simfer.com.tr/d/turbo-cift-cam-mekanik-saat-lamba-2158325-10-O.jpg",
                "https://simfer.com.tr/d/inox-gri-70-litre-turbo-midi-firin-2158327-78-K.jpg");

        List<String> kept = ProductImageCrawlerService.keepThisProductsImages(
                found, "https://simfer.com.tr/70-lt-pro-beyaz-turbo-cift-cam-mekanik-saat-lamba");

        assertThat(kept).containsExactly(found.get(0));
    }

    @Test
    void keepsOnlyTheLargestCopyOfTheSamePhotograph() {
        // The same picture is published at three sizes; the smaller two would only be
        // upscaled by the storefront.
        List<String> found = List.of(
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-9005-30-K.jpg",
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-9005-30-O.jpg",
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-9005-30-B.jpg",
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-8705-30-B.jpg");

        List<String> kept = ProductImageCrawlerService.keepThisProductsImages(
                found, "https://www.fakir.com.tr/atomic-rondo-rosie");

        assertThat(kept).containsExactly(
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-9005-30-O.jpg",
                "https://www.fakir.com.tr/img/atomic-rondo-rosie-fakir-8705-30-B.jpg");
    }

    @Test
    void aShortSlugIsNotSpecificEnoughToFilterOn() {
        // "/tv" or "/beyaz" would match most of a catalogue's filenames.
        List<String> found = List.of(
                "https://shop.example.com/img/beyaz-firin-1.jpg",
                "https://shop.example.com/img/gri-ocak-2.jpg");

        assertThat(ProductImageCrawlerService.keepThisProductsImages(
                found, "https://shop.example.com/beyaz")).isEqualTo(found);
    }

    @Test
    void shopFurnitureIsNotAProductPhoto() {
        // A Simfer page listed eleven category-strip icons among its images; carsi24
        // added menu artwork and the app-store badges from its footer.
        for (String junk : new String[] {
                "https://simfer.com.tr/Data/img/category/3/tr_img_1_233.png",
                "https://carsi24.witcdn.net/Data/img/menu_item/6/tr_img_1_6.png",
                "https://witcdn.carsi24.com/Data/EditorFiles/googleplay.webp",
                "https://witcdn.carsi24.com/Data/EditorFiles/applestore.webp" }) {
            assertThat(ProductImageCrawlerService.isProbablyJunkUrl(junk)).as(junk).isTrue();
        }
    }

    @Test
    void aRealProductPhotoIsNotFilteredOut() {
        assertThat(ProductImageCrawlerService.isProbablyJunkUrl(
                "https://simfer.com.tr/Data/Product/70-lt-pro-beyaz-2158325-10-O.jpg")).isFalse();
    }

    @Test
    void doesNothingToASingleImageOrAMissingUrl() {
        List<String> one = List.of("https://cdn.example.com/x-999999.jpg");

        assertThat(ProductImageCrawlerService.keepThisProductsImages(one, PAGE)).isEqualTo(one);
        assertThat(ProductImageCrawlerService.keepThisProductsImages(one, null)).isEqualTo(one);
        assertThat(ProductImageCrawlerService.keepThisProductsImages(List.of(), PAGE)).isEmpty();
    }
}
