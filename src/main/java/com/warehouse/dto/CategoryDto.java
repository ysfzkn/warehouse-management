package com.warehouse.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    private String name;
    private String description;
    private boolean isActive;
    private Long productCount;
    private Long parentId;
    private String parentName;
    private List<CategoryDto> children;
    private List<CategoryDto> subcategories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


