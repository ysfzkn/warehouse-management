package com.warehouse.service;

import com.warehouse.entity.Warehouse;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing warehouses.
 */
public interface WarehouseService {

    List<Warehouse> getAllWarehouses();

    List<Warehouse> getAllActiveWarehouses();

    Optional<Warehouse> getWarehouseById(Long id);

    Warehouse getWarehouseByIdOrThrow(Long id);

    Optional<Warehouse> getWarehouseByIdWithStocks(Long id);

    Optional<Warehouse> getWarehouseByName(String name);

    Warehouse createWarehouse(Warehouse warehouse);

    Warehouse updateWarehouse(Long id, Warehouse warehouseDetails);

    void deleteWarehouse(Long id);

    void deactivateWarehouse(Long id);

    void activateWarehouse(Long id);

    boolean existsByName(String name);
}
