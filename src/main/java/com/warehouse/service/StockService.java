package com.warehouse.service;

import com.warehouse.entity.Stock;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.repository.BrandRepository;
import com.warehouse.repository.ColorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final BrandRepository brandRepository;
    private final ColorRepository colorRepository;

    @Autowired
    public StockService(StockRepository stockRepository,
                       ProductRepository productRepository,
                       WarehouseRepository warehouseRepository,
                       BrandRepository brandRepository,
                       ColorRepository colorRepository) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.brandRepository = brandRepository;
        this.colorRepository = colorRepository;
    }

    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    public List<Stock> getAllStocksFiltered(Long brandId, Long colorId) {
        var all = stockRepository.findAll();
        return all.stream().filter(s -> {
            boolean ok = true;
            if (brandId != null) {
                ok = ok && s.getProduct() != null && s.getProduct().getBrand() != null && brandId.equals(s.getProduct().getBrand().getId());
            }
            if (colorId != null) {
                ok = ok && s.getProduct() != null && s.getProduct().getColor() != null && colorId.equals(s.getProduct().getColor().getId());
            }
            return ok;
        }).toList();
    }

    public Optional<Stock> getStockById(Long id) {
        return stockRepository.findById(id);
    }

    public List<Stock> getStocksByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        return stockRepository.findByProduct(product);
    }

    public List<Stock> getStocksByWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + warehouseId));

        return stockRepository.findByWarehouse(warehouse);
    }

    public Optional<Stock> getStockByProductAndWarehouse(Long productId, Long warehouseId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + warehouseId));

        return stockRepository.findByProductAndWarehouse(product, warehouse);
    }

    public List<Stock> getLowStockItems() {
        return stockRepository.findLowStockItems();
    }

    public List<Stock> getOutOfStockItems() {
        return stockRepository.findOutOfStockItems();
    }

    public List<Stock> getLowStockItemsByWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + warehouseId));

        return stockRepository.findLowStockItemsByWarehouse(warehouse);
    }

    public Long getTotalQuantityByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        Long total = stockRepository.getTotalQuantityByProduct(product);
        return total != null ? total : 0L;
    }

    public Long getTotalQuantityByWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + warehouseId));

        Long total = stockRepository.getTotalQuantityByWarehouse(warehouse);
        return total != null ? total : 0L;
    }

    public Stock createStock(Stock stock) {
        // Validate product and warehouse exist
        if (stock.getProduct() == null || stock.getProduct().getId() == null) {
            throw new RuntimeException("Product is required");
        }

        if (stock.getWarehouse() == null || stock.getWarehouse().getId() == null) {
            throw new RuntimeException("Warehouse is required");
        }

        Product product = productRepository.findById(stock.getProduct().getId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + stock.getProduct().getId()));

        Warehouse warehouse = warehouseRepository.findById(stock.getWarehouse().getId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + stock.getWarehouse().getId()));

        // Check if stock already exists for this product-warehouse combination
        Optional<Stock> existingStock = stockRepository.findByProductAndWarehouse(product, warehouse);
        if (existingStock.isPresent()) {
            throw new RuntimeException("Stock already exists for this product in the selected warehouse");
        }

        stock.setProduct(product);
        stock.setWarehouse(warehouse);

        return stockRepository.save(stock);
    }

    public Stock updateStock(Long id, Stock stockDetails) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + id));

        // Validate and set new quantity
        if (stockDetails.getQuantity() != null) {
            if (stockDetails.getQuantity() < 0) {
                throw new RuntimeException("Quantity cannot be negative");
            }
            stock.setQuantity(stockDetails.getQuantity());
        }

        if (stockDetails.getMinStockLevel() != null) {
            if (stockDetails.getMinStockLevel() < 0) {
                throw new RuntimeException("Minimum stock level cannot be negative");
            }
            stock.setMinStockLevel(stockDetails.getMinStockLevel());
        }

        if (stockDetails.getReservedQuantity() != null) {
            if (stockDetails.getReservedQuantity() < 0) {
                throw new RuntimeException("Reserved quantity cannot be negative");
            }
            stock.setReservedQuantity(stockDetails.getReservedQuantity());
        }

        if (stockDetails.getConsignedQuantity() != null) {
            if (stockDetails.getConsignedQuantity() < 0) {
                throw new RuntimeException("Consigned quantity cannot be negative");
            }
            stock.setConsignedQuantity(stockDetails.getConsignedQuantity());
        }

        return stockRepository.save(stock);
    }

    public Stock addToStock(Long stockId, Integer quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity to add must be positive");
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + stockId));

        stock.setQuantity(stock.getQuantity() + quantity);
        return stockRepository.save(stock);
    }

    public Stock removeFromStock(Long stockId, Integer quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity to remove must be positive");
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + stockId));

        stock.setQuantity(stock.getQuantity() - quantity);
        return stockRepository.save(stock);
    }

    public void deleteStock(Long id) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + id));

        stockRepository.delete(stock);
    }

    public Stock reserveStock(Long stockId, Integer quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity to reserve must be positive");
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + stockId));

        Integer availableQuantity = stock.getAvailableQuantity();
        if (availableQuantity < quantity) {
            throw new RuntimeException("Insufficient available stock. Available: " + availableQuantity + ", Requested: " + quantity);
        }

        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        return stockRepository.save(stock);
    }

    public Stock releaseStock(Long stockId, Integer quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity to release must be positive");
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found with id: " + stockId));

        if (stock.getReservedQuantity() < quantity) {
            throw new RuntimeException("Cannot release more than reserved quantity. Reserved: " + stock.getReservedQuantity() + ", Requested: " + quantity);
        }

        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        return stockRepository.save(stock);
    }
}
