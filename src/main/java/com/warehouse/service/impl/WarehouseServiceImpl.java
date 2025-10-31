package com.warehouse.service.impl;

import com.warehouse.entity.Warehouse;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.WarehouseService;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.NameUniquenessValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of WarehouseService for managing warehouses.
 */
@Service
@Transactional
public class WarehouseServiceImpl implements WarehouseService {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseServiceImpl.class);

    private final WarehouseRepository warehouseRepository;

    public WarehouseServiceImpl(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Warehouse> getAllWarehouses() {
        logger.debug("Fetching all warehouses");
        return warehouseRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Warehouse> getAllActiveWarehouses() {
        logger.debug("Fetching all active warehouses");
        return warehouseRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Warehouse> getWarehouseById(Long id) {
        logger.debug("Fetching warehouse by id: {}", id);
        return warehouseRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse getWarehouseByIdOrThrow(Long id) {
        logger.debug("Fetching warehouse by id or throw: {}", id);
        return warehouseRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Warehouse not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.WAREHOUSE_NOT_FOUND, "ID: " + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Warehouse> getWarehouseByIdWithStocks(Long id) {
        logger.debug("Fetching warehouse with stocks by id: {}", id);
        return warehouseRepository.findByIdWithStocks(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Warehouse> getWarehouseByName(String name) {
        logger.debug("Fetching warehouse by name: {}", name);
        return warehouseRepository.findByName(name);
    }

    @Override
    public Warehouse createWarehouse(Warehouse warehouse) {
        logger.info("Creating new warehouse: {}", warehouse.getName());
        validateNameUniqueness(warehouse.getName());
        Warehouse saved = warehouseRepository.save(warehouse);
        logger.info("Warehouse created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public Warehouse updateWarehouse(Long id, Warehouse warehouseDetails) {
        logger.info("Updating warehouse with id: {}", id);
        Warehouse warehouse = getWarehouseByIdOrThrow(id);
        validateNameUniquenessOnUpdate(warehouse, warehouseDetails);
        updateWarehouseFields(warehouse, warehouseDetails);
        Warehouse saved = warehouseRepository.save(warehouse);
        logger.info("Warehouse updated successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void deleteWarehouse(Long id) {
        logger.info("Deleting warehouse with id: {}", id);
        Warehouse warehouse = getWarehouseByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            !warehouse.getStocks().isEmpty(), "Warehouse", "stocks"
        );
        warehouseRepository.delete(warehouse);
        logger.info("Warehouse deleted successfully with id: {}", id);
    }

    @Override
    public void deactivateWarehouse(Long id) {
        logger.info("Deactivating warehouse with id: {}", id);
        updateWarehouseStatus(id, false);
    }

    @Override
    public void activateWarehouse(Long id) {
        logger.info("Activating warehouse with id: {}", id);
        updateWarehouseStatus(id, true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return warehouseRepository.existsByName(name);
    }

    private void validateNameUniqueness(String name) {
        NameUniquenessValidator.validateNameUniqueness(
            name,
            warehouseRepository::existsByName,
            ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS,
            "Warehouse"
        );
    }

    private void validateNameUniquenessOnUpdate(Warehouse warehouse, Warehouse warehouseDetails) {
        NameUniquenessValidator.validateNameUniquenessOnUpdate(
            warehouse.getName(),
            warehouseDetails.getName(),
            warehouseRepository::existsByName,
            ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS,
            "Warehouse"
        );
    }

    private void updateWarehouseFields(Warehouse warehouse, Warehouse warehouseDetails) {
        warehouse.setName(warehouseDetails.getName());
        warehouse.setLocation(warehouseDetails.getLocation());
        warehouse.setPhone(warehouseDetails.getPhone());
        warehouse.setManager(warehouseDetails.getManager());
        warehouse.setCapacitySqm(warehouseDetails.getCapacitySqm());
        warehouse.setActive(warehouseDetails.isActive());
    }

    private void updateWarehouseStatus(Long id, boolean isActive) {
        Warehouse warehouse = getWarehouseByIdOrThrow(id);
        warehouse.setActive(isActive);
        warehouseRepository.save(warehouse);
        logger.debug("Warehouse status updated. Id: {}, Active: {}", id, isActive);
    }
}

