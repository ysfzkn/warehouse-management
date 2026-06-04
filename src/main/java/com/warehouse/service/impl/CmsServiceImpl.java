package com.warehouse.service.impl;

import com.warehouse.dto.store.BannerDto;
import com.warehouse.dto.store.CmsPageDto;
import com.warehouse.entity.CmsPage;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CmsPageRepository;
import com.warehouse.service.CmsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CmsServiceImpl implements CmsService {

    private final CmsPageRepository cmsPageRepository;

    public CmsServiceImpl(CmsPageRepository cmsPageRepository) {
        this.cmsPageRepository = cmsPageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CmsPageDto getPageBySlug(String slug) {
        CmsPage page = cmsPageRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sayfa bulunamadı: " + slug));
        return CmsPageDto.builder()
            .title(page.getTitle())
            .slug(page.getSlug())
            .content(page.getContent())
            .metaTitle(page.getMetaTitle())
            .metaDescription(page.getMetaDescription())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerDto> getActiveBanners(String position) {
        return cmsPageRepository.findActiveBanners(position, LocalDateTime.now()).stream()
            .map(p -> BannerDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .imageUrl(p.getBannerImageUrl())
                .link(p.getBannerLink())
                .position(p.getBannerPosition())
                .sortOrder(p.getSortOrder())
                .build())
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CmsPage> getAllPages(Pageable pageable) {
        return cmsPageRepository.findAll(pageable);
    }

    @Override
    public CmsPage createPage(CmsPage page) { return cmsPageRepository.save(page); }

    @Override
    public CmsPage updatePage(Long id, CmsPage updates) {
        CmsPage page = cmsPageRepository.findById(id)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sayfa bulunamadı."));
        page.setTitle(updates.getTitle());
        page.setSlug(updates.getSlug());
        page.setContent(updates.getContent());
        page.setPageType(updates.getPageType());
        page.setMetaTitle(updates.getMetaTitle());
        page.setMetaDescription(updates.getMetaDescription());
        page.setActive(updates.isActive());
        page.setSortOrder(updates.getSortOrder());
        page.setBannerImageUrl(updates.getBannerImageUrl());
        page.setBannerLink(updates.getBannerLink());
        page.setBannerPosition(updates.getBannerPosition());
        page.setBannerStart(updates.getBannerStart());
        page.setBannerEnd(updates.getBannerEnd());
        return cmsPageRepository.save(page);
    }

    @Override
    public void deletePage(Long id) { cmsPageRepository.deleteById(id); }
}
