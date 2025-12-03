package com.warehouse.service;

import com.warehouse.entity.Color;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing colors.
 */
public interface ColorService {

    List<Color> getAllColors();

    List<Color> getAllActiveColors();

    List<Color> searchActiveColors(String name);

    Optional<Color> getColorById(Long id);

    Color getColorByIdOrThrow(Long id);

    Color createColor(Color color);

    Color updateColor(Long id, Color details);

    void deleteColor(Long id);

    /**
     * Deletes multiple colors by their IDs.
     *
     * @param ids list of color IDs to delete
     */
    void deleteColorsBulk(List<Long> ids);
}
