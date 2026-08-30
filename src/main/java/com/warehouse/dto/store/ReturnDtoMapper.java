package com.warehouse.dto.store;

import com.warehouse.entity.ReturnRequest;
import com.warehouse.entity.ReturnRequestItem;
import com.warehouse.entity.ReturnRequestPhoto;
import com.warehouse.util.ProductImageUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes a {@link ReturnRequest} to a plain map for JSON responses (avoids
 * lazy-loading surprises and keeps the wire shape explicit). Must be called within
 * an open transaction since it touches lazy associations (items, order, customer).
 * Shared by the admin and storefront return endpoints.
 */
public final class ReturnDtoMapper {

    private ReturnDtoMapper() {}

    public static Map<String, Object> toMap(ReturnRequest rr) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", rr.getId());
        dto.put("returnNumber", rr.getReturnNumber());
        dto.put("status", rr.getStatus() != null ? rr.getStatus().name() : null);
        dto.put("reason", rr.getReason() != null ? rr.getReason().name() : null);
        dto.put("customerNote", rr.getCustomerNote());
        dto.put("adminNote", rr.getAdminNote());
        dto.put("refundAmount", rr.getRefundAmount());
        dto.put("cargoTrackingNo", rr.getCargoTrackingNo());
        dto.put("createdAt", rr.getCreatedAt());
        dto.put("updatedAt", rr.getUpdatedAt());

        try {
            if (rr.getOrder() != null) {
                dto.put("orderNumber", rr.getOrder().getOrderNumber());
                dto.put("orderId", rr.getOrder().getId());
            }
        } catch (Exception ignored) { /* lazy */ }

        try {
            if (rr.getCustomer() != null) {
                String name = (safe(rr.getCustomer().getFirstName()) + " " + safe(rr.getCustomer().getLastName())).trim();
                dto.put("customerId", rr.getCustomer().getId());
                dto.put("customerName", name);
                dto.put("customerEmail", rr.getCustomer().getEmail());
            }
        } catch (Exception ignored) { /* lazy */ }

        List<Map<String, Object>> items = new ArrayList<>();
        try {
            if (rr.getItems() != null) {
                for (ReturnRequestItem it : rr.getItems()) {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("id", it.getId());
                    im.put("quantity", it.getQuantity());
                    im.put("reason", it.getReason());
                    try {
                        if (it.getProduct() != null) {
                            im.put("productId", it.getProduct().getId());
                            im.put("productName", it.getProduct().getName());
                            im.put("productSlug", it.getProduct().getSlug());
                            ProductImageUtil.displayCover(it.getProduct().getImages())
                                    .map(img -> "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true")
                                    .ifPresent(url -> im.put("productImageUrl", url));
                        }
                    } catch (Exception ignored) { /* lazy */ }
                    items.add(im);
                }
            }
        } catch (Exception ignored) { /* lazy */ }
        dto.put("items", items);

        // Customer-uploaded evidence photos.
        List<Map<String, Object>> photos = new ArrayList<>();
        try {
            if (rr.getPhotos() != null) {
                for (ReturnRequestPhoto ph : rr.getPhotos()) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("id", ph.getId());
                    pm.put("url", "/api/admin/returns/photos/" + ph.getId() + "/view"
                            + com.warehouse.security.SignedUrlService.query("return-photo", ph.getId()));
                    photos.add(pm);
                }
            }
        } catch (Exception ignored) { /* lazy */ }
        dto.put("photos", photos);
        return dto;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
