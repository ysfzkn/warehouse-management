package com.warehouse.service;

import com.warehouse.entity.Color;
import com.warehouse.repository.ColorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ColorService {

    private final ColorRepository colorRepository;

    @Autowired
    public ColorService(ColorRepository colorRepository) {
        this.colorRepository = colorRepository;
    }

    public List<Color> getAllColors() {
        return colorRepository.findAll();
    }

    public List<Color> getAllActiveColors() {
        return colorRepository.findAllActive();
    }

    public List<Color> searchActiveColors(String name) {
        return colorRepository.searchActiveByName(name);
    }

    public Optional<Color> getColorById(Long id) {
        return colorRepository.findById(id);
    }

    public Color createColor(Color color) {
        if (colorRepository.existsByName(color.getName())) {
            throw new RuntimeException("Color with name '" + color.getName() + "' already exists");
        }
        return colorRepository.save(color);
    }

    public Color updateColor(Long id, Color details) {
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Color not found with id: " + id));

        if (!color.getName().equals(details.getName()) && colorRepository.existsByName(details.getName())) {
            throw new RuntimeException("Color with name '" + details.getName() + "' already exists");
        }

        color.setName(details.getName());
        color.setHexCode(details.getHexCode());
        color.setActive(details.isActive());
        return colorRepository.save(color);
    }

    public void deleteColor(Long id) {
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Color not found with id: " + id));

        if (color.getProducts() != null && !color.getProducts().isEmpty()) {
            throw new RuntimeException("Cannot delete color with existing products");
        }
        colorRepository.delete(color);
    }
}


