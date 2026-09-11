package com.warehouse.service.crawler;

import com.warehouse.entity.Brand;
import com.warehouse.entity.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class SupplierLinkFinderLiveTest {

    private static Product product(String brand, String sku, String name) {
        Product p = new Product();
        Brand b = new Brand();
        b.setName(brand);
        p.setBrand(b);
        p.setSku(sku);
        p.setName(name);
        return p;
    }

    @Test
    @EnabledIfSystemProperty(named = "live.fetch", matches = ".+")
    void findsRealSupplierPages() {
        SupplierLinkFinder finder = new SupplierLinkFinder();
        Object[][] cases = {
            {"Simfer", "SR-2515", "294 Litre Çift Kapılı Statik Buzdolabı"},
            {"Simfer", "40 SFSW4M", "102 EKRAN WEBOS LED FHD TV"},
            {"Ferre", "B2240 CE", "B2240 CE Beyaz Setüstü Cam Ocak"},
            {"Hoover", "HF 3E53E0W-17", "Bulaşık Makinesi"},
            {"Kumtel", "KF-6420", "Fırın"},
            {"Profilo", "42PA300E", "Televizyon"},
            {"Profilo", "FRGA103B", "Ankastre elektrikli fırın"},
            {"Profilo", "FRIAT8AB", "Ankastre Mikrodalga"},
            {"Profilo", "BM4381EG", "Bulasik Makineleri 60 cm solo"},
            // Regression: this proposed the MF-42 oven's page before the digit rule.
            {"Ferre", "Ferre 35 Beyaz", "Beyaz Mini Fırın 35 L"},
        };
        for (Object[] c : cases) {
            Product p = product((String) c[0], (String) c[1], (String) c[2]);
            long t0 = System.currentTimeMillis();
            String host = finder.hostForBrand((String) c[0]);
            String url = finder.find(p);
            System.out.printf("%-8s %-14s host=%-18s %5dms -> %s%n",
                    c[0], c[1], host, System.currentTimeMillis() - t0,
                    url == null ? "(bulunamadi)" : url);
        }
    }
}
