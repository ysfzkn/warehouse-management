package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class CmsPageDto {
    private String title;
    private String slug;
    private String content;
    private String metaTitle;
    private String metaDescription;
}
