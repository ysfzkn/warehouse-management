package com.warehouse.dto.store;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StoreCategoryDto {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String imageUrl;
    private String metaTitle;
    private String metaDescription;
    private int sortOrder;
    private Long parentId;
    private String parentSlug;
    private String parentName;
    private List<StoreCategoryDto> children;
    private long productCount;
}
