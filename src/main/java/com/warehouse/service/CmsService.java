package com.warehouse.service;

import com.warehouse.dto.store.BannerDto;
import com.warehouse.dto.store.CmsPageDto;
import com.warehouse.entity.CmsPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface CmsService {
    CmsPageDto getPageBySlug(String slug);
    List<BannerDto> getActiveBanners(String position);
    Page<CmsPage> getAllPages(Pageable pageable);
    CmsPage createPage(CmsPage page);
    CmsPage updatePage(Long id, CmsPage page);
    void deletePage(Long id);
}
