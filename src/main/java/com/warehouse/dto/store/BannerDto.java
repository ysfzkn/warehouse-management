package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class BannerDto {
    private Long id;
    private String title;
    private String imageUrl;
    private String link;
    private String position;
    private int sortOrder;
}
