package com.warehouse.service;

import com.warehouse.entity.Color;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.ColorRepository;
import com.warehouse.util.EntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ColorService {

    private final ColorRepository colorRepository;

    public ColorService(ColorRepository colorRepository) {
        this.colorRepository = colorRepository;
    }

    @Transactional(readOnly = true)
    public List<Color> getAllColors() {
        return colorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Color> getAllActiveColors() {
        return colorRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<Color> searchActiveColors(String name) {
        return colorRepository.searchActiveByName(name);
    }

    @Transactional(readOnly = true)
    public Optional<Color> getColorById(Long id) {
        return colorRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Color getColorByIdOrThrow(Long id) {
        return colorRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.COLOR_NOT_FOUND, "ID: " + id));
    }

    public Color createColor(Color color) {
        checkNameDuplication(color.getName());
        return colorRepository.save(color);
    }

    public Color updateColor(Long id, Color details) {
        Color color = getColorByIdOrThrow(id);
        checkNameDuplicationOnUpdate(color, details);
        color.setName(details.getName());
        color.setHexCode(details.getHexCode());
        color.setActive(details.isActive());
        return colorRepository.save(color);
    }

    public void deleteColor(Long id) {
        Color color = getColorByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            color.getProducts() != null && !color.getProducts().isEmpty(), 
            "Color", "products"
        );
        colorRepository.delete(color);
    }

    private void checkNameDuplication(String name) {
        if (colorRepository.existsByName(name)) {
            throw new WarehouseManagementException(ErrorCode.COLOR_NAME_ALREADY_EXISTS, "Name: " + name);
        }
    }

    private void checkNameDuplicationOnUpdate(Color color, Color details) {
        if (!color.getName().equals(details.getName()) && 
            colorRepository.existsByName(details.getName())) {
            throw new WarehouseManagementException(ErrorCode.COLOR_NAME_ALREADY_EXISTS, "Name: " + details.getName());
        }
    }
}


