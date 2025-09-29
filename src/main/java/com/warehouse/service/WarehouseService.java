package com.warehouse.service;

import com.warehouse.entity.Warehouse;
import com.warehouse.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Autowired
    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    public List<Warehouse> getAllActiveWarehouses() {
        return warehouseRepository.findAllActive();
    }

    public Optional<Warehouse> getWarehouseById(Long id) {
        return warehouseRepository.findById(id);
    }

    public Optional<Warehouse> getWarehouseByIdWithStocks(Long id) {
        return warehouseRepository.findByIdWithStocks(id);
    }

    public Optional<Warehouse> getWarehouseByName(String name) {
        return warehouseRepository.findByName(name);
    }

    public Warehouse createWarehouse(Warehouse warehouse) {
        if (warehouseRepository.existsByName(warehouse.getName())) {
            throw new RuntimeException("Warehouse with name '" + warehouse.getName() + "' already exists");
        }
        return warehouseRepository.save(warehouse);
    }

    public Warehouse updateWarehouse(Long id, Warehouse warehouseDetails) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));

        // Check if the new name conflicts with existing warehouses
        if (!warehouse.getName().equals(warehouseDetails.getName()) &&
            warehouseRepository.existsByName(warehouseDetails.getName())) {
            throw new RuntimeException("Warehouse with name '" + warehouseDetails.getName() + "' already exists");
        }

        warehouse.setName(warehouseDetails.getName());
        warehouse.setLocation(warehouseDetails.getLocation());
        warehouse.setPhone(warehouseDetails.getPhone());
        warehouse.setManager(warehouseDetails.getManager());
        warehouse.setCapacitySqm(warehouseDetails.getCapacitySqm());
        warehouse.setActive(warehouseDetails.isActive());

        return warehouseRepository.save(warehouse);
    }

    public void deleteWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));

        if (!warehouse.getStocks().isEmpty()) {
            throw new RuntimeException("Cannot delete warehouse with existing stock");
        }

        warehouseRepository.delete(warehouse);
    }

    public void deactivateWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));

        warehouse.setActive(false);
        warehouseRepository.save(warehouse);
    }

    public void activateWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));

        warehouse.setActive(true);
        warehouseRepository.save(warehouse);
    }

    public boolean existsByName(String name) {
        return warehouseRepository.existsByName(name);
    }
}
