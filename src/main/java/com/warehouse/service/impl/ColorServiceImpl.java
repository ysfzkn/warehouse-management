package com.warehouse.service.impl;

import com.warehouse.entity.Color;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ColorRepository;
import com.warehouse.service.ColorService;
import com.warehouse.util.EntityValidator;
import com.warehouse.constants.BusinessMessages;
import com.warehouse.constants.EntityNames;
import com.warehouse.util.NameUniquenessValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of ColorService for managing colors.
 */
@Service
@Transactional
public class ColorServiceImpl implements ColorService {

    private static final Logger logger = LoggerFactory.getLogger(ColorServiceImpl.class);

    private final ColorRepository colorRepository;

    public ColorServiceImpl(ColorRepository colorRepository) {
        this.colorRepository = colorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> getAllColors() {
        logger.debug("Fetching all colors");
        return colorRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> getAllActiveColors() {
        logger.debug("Fetching all active colors");
        return colorRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> searchActiveColors(String name) {
        logger.debug("Searching active colors by name: {}", name);
        return colorRepository.searchActiveByName(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Color> getColorById(Long id) {
        logger.debug("Fetching color by id: {}", id);
        return colorRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Color getColorByIdOrThrow(Long id) {
        logger.debug("Fetching color by id or throw: {}", id);
        return colorRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Color not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, BusinessMessages.ID_PREFIX + id);
                });
    }

    @Override
    public Color createColor(Color color) {
        logger.info("Creating new color: {}", color.getName());
        validateNameUniqueness(color.getName());
        Color saved = colorRepository.save(color);
        logger.info("Color created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Color updateColor(Long id, Color details) {
        logger.info("Updating color with id: {}", id);
        Color color = getColorByIdOrThrow(id);
        validateNameUniquenessOnUpdate(color, details);
        color.setName(details.getName());
        color.setHexCode(details.getHexCode());
        color.setActive(details.isActive());
        Color saved = colorRepository.save(color);
        logger.info("Color updated successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void deleteColor(Long id) {
        logger.info("Deleting color with id: {}", id);
        Color color = getColorByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            color.getProducts() != null && !color.getProducts().isEmpty(),
            EntityNames.COLOR, EntityNames.RELATION_PRODUCTS
        );
        colorRepository.delete(color);
        logger.info("Color deleted successfully with id: {}", id);
    }

    private void validateNameUniqueness(String name) {
        NameUniquenessValidator.validateNameUniqueness(
            name,
            colorRepository::existsByName,
            ErrorCode.COLOR_NAME_ALREADY_EXISTS,
            "Color"
        );
    }

    private void validateNameUniquenessOnUpdate(Color color, Color details) {
        NameUniquenessValidator.validateNameUniquenessOnUpdate(
            color.getName(),
            details.getName(),
            colorRepository::existsByName,
            ErrorCode.COLOR_NAME_ALREADY_EXISTS,
            "Color"
        );
    }
}

