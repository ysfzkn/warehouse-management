package com.warehouse.service;

import com.warehouse.entity.Warehouse;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.util.EntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Warehouse> getAllActiveWarehouses() {
        return warehouseRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Optional<Warehouse> getWarehouseById(Long id) {
        return warehouseRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouseByIdOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.WAREHOUSE_NOT_FOUND, "ID: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Warehouse> getWarehouseByIdWithStocks(Long id) {
        return warehouseRepository.findByIdWithStocks(id);
    }

    @Transactional(readOnly = true)
    public Optional<Warehouse> getWarehouseByName(String name) {
        return warehouseRepository.findByName(name);
    }

    public Warehouse createWarehouse(Warehouse warehouse) {
        checkNameDuplication(warehouse.getName());
        return warehouseRepository.save(warehouse);
    }

    public Warehouse updateWarehouse(Long id, Warehouse warehouseDetails) {
        Warehouse warehouse = getWarehouseByIdOrThrow(id);
        checkNameDuplicationOnUpdate(warehouse, warehouseDetails);
        updateWarehouseFields(warehouse, warehouseDetails);
        return warehouseRepository.save(warehouse);
    }

    public void deleteWarehouse(Long id) {
        Warehouse warehouse = getWarehouseByIdOrThrow(id);
        EntityValidator.validateEntityHasNoRelations(
            !warehouse.getStocks().isEmpty(), "Warehouse", "stocks"
        );
        warehouseRepository.delete(warehouse);
    }

    public void deactivateWarehouse(Long id) {
        updateWarehouseStatus(id, false);
    }

    public void activateWarehouse(Long id) {
        updateWarehouseStatus(id, true);
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return warehouseRepository.existsByName(name);
    }

    private void checkNameDuplication(String name) {
        if (warehouseRepository.existsByName(name)) {
            throw new WarehouseManagementException(ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS, "Name: " + name);
        }
    }

    private void checkNameDuplicationOnUpdate(Warehouse warehouse, Warehouse warehouseDetails) {
        if (!warehouse.getName().equals(warehouseDetails.getName()) &&
            warehouseRepository.existsByName(warehouseDetails.getName())) {
            throw new WarehouseManagementException(ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS, "Name: " + warehouseDetails.getName());
        }
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
    }
}
