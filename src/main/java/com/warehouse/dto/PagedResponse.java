package com.warehouse.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Generic response wrapper for paginated endpoints.
 */
public class PagedResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;
    private final Map<String, Object> metadata;

    public PagedResponse(List<T> content,
                         int page,
                         int size,
                         long totalElements,
                         int totalPages,
                         boolean first,
                         boolean last) {
        this(content, page, size, totalElements, totalPages, first, last, Collections.emptyMap());
    }

    public PagedResponse(List<T> content,
                         int page,
                         int size,
                         long totalElements,
                         int totalPages,
                         boolean first,
                         boolean last,
                         Map<String, Object> metadata) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public boolean isLast() {
        return last;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

