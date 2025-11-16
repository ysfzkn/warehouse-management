package com.warehouse.service.impl;

import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.AuditService;
import com.warehouse.service.NotificationService;
import com.warehouse.service.StockTransferService;
import com.warehouse.util.CurrentUser;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.warehouse.constants.NotificationMessages;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.enums.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of StockTransferService for managing stock transfers.
 */
@Service
@Transactional
public class StockTransferServiceImpl implements StockTransferService {

    private static final Logger logger = LoggerFactory.getLogger(StockTransferServiceImpl.class);

    private final StockTransferRepository stockTransferRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public StockTransferServiceImpl(StockTransferRepository stockTransferRepository,
                                    StockRepository stockRepository,
                                    ProductRepository productRepository,
                                    WarehouseRepository warehouseRepository,
                                    AuditService auditService,
                                    NotificationService notificationService) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getAllTransfers() {
        logger.debug("Fetching all transfers");
        return stockTransferRepository.findAllOrderByTransferDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockTransfer> getTransferById(Long id) {
        logger.debug("Fetching transfer by id: {}", id);
        return stockTransferRepository.findByIdWithRelations(id);
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransfer getTransferByIdOrThrow(Long id) {
        logger.debug("Fetching transfer by id or throw: {}", id);
        return stockTransferRepository.findByIdWithRelations(id)
                .orElseThrow(() -> {
                    logger.warn("Transfer not found with id: {}", id);
                    return new WarehouseManagementException(ErrorCode.TRANSFER_NOT_FOUND);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByWarehouse(Long warehouseId) {
        logger.debug("Fetching transfers by warehouse id: {}", warehouseId);
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        return stockTransferRepository.findByWarehouse(warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByProduct(Long productId) {
        logger.debug("Fetching transfers by product id: {}", productId);
        Product product = findProductOrThrow(productId);
        return stockTransferRepository.findByProduct(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByStatus(TransferStatus status) {
        logger.debug("Fetching transfers by status: {}", status);
        return stockTransferRepository.findByStatus(status);
    }

    @Override
    public StockTransfer createTransfer(StockTransfer transfer) {
        logger.info("Creating new transfer");
        TransferType transferType = validateTransferCreation(transfer);

        Warehouse sourceWarehouse = findWarehouseOrThrow(transfer.getSourceWarehouse().getId());
        Warehouse destinationWarehouse = null;
        if (transferType == TransferType.WAREHOUSE) {
            destinationWarehouse = findWarehouseOrThrow(transfer.getDestinationWarehouse().getId());
            EntityValidator.validateWarehousesDifferent(sourceWarehouse, destinationWarehouse);
        } else if (transfer.getDestinationWarehouse() != null && transfer.getDestinationWarehouse().getId() != null) {
            destinationWarehouse = findWarehouseOrThrow(transfer.getDestinationWarehouse().getId());
        }
        Product product = findProductOrThrow(transfer.getProduct().getId());

        ValidationUtil.requirePositive(transfer.getQuantity(), "Quantity");

        validateSufficientStock(product, sourceWarehouse, transfer.getQuantity());

        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setProduct(product);
        transfer.setTransferType(transferType);
        transfer.setStatus(TransferStatus.PENDING);

        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_CREATE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer oluşturuldu: %s | Ürün=%s | Miktar=%d",
                        describeRoute(saved), product.getName(), saved.getQuantity()));
        notificationService.create(NotificationMessages.TRANSFER_CREATED_TITLE,
                String.format("Kullanıcı %s, %s yönünde %s ürünü için %d adet transfer oluşturdu.", username,
                        describeRoute(saved), product.getName(), saved.getQuantity()),
                DomainEntityType.Stock.name(), saved.getId());
        notifyAdminIfNonAdmin(saved, "oluşturdu");
        logger.info("Transfer created successfully with id: {}", saved.getId());
        
        // Fetch with relations again to avoid LazyInitializationException in mapper
        return stockTransferRepository.findByIdWithRelations(saved.getId())
                .orElse(saved);
    }

    @Override
    public StockTransfer startTransfer(Long transferId) {
        logger.info("Starting transfer with id: {}", transferId);
        StockTransfer transfer = getTransferByIdOrThrow(transferId);

        if (transfer.getStatus() != TransferStatus.PENDING) {
            logger.warn("Transfer cannot be started. Current status: {}", transfer.getStatus());
            throw new WarehouseManagementException(ErrorCode.ONLY_PENDING_CAN_BE_STARTED);
        }

        Stock sourceStock = findSourceStockOrThrow(transfer);
        validateSufficientAvailableStock(sourceStock, transfer.getQuantity());

        reserveStockForTransfer(sourceStock, transfer.getQuantity());

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_START, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer yola çıkarıldı: %s | Ürün=%s | Miktar=%d (Stok rezerve edildi)",
                        describeRoute(saved), saved.getProduct().getName(), saved.getQuantity()));
        notificationService.create(NotificationMessages.TRANSFER_STARTED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi yola çıkardı.", username, saved.getId()),
                DomainEntityType.Stock.name(), saved.getId());
        notifyAdminIfNonAdmin(saved, "yola çıkardı");
        logger.info("Transfer started successfully with id: {}", saved.getId());
        
        // Fetch with relations again to avoid LazyInitializationException in mapper
        return stockTransferRepository.findByIdWithRelations(saved.getId())
                .orElse(saved);
    }

    @Override
    public StockTransfer completeTransfer(Long transferId, String completionNote) {
        logger.info("Completing transfer with id: {}", transferId);
        StockTransfer transfer = getTransferByIdOrThrow(transferId);

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            logger.warn("Transfer already completed. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.TRANSFER_ALREADY_COMPLETED);
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            logger.warn("Cannot complete cancelled transfer. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.CANNOT_CANCEL_COMPLETED);
        }

        Stock sourceStock = findSourceStockOrThrow(transfer);

        if (transfer.getStatus() == TransferStatus.PENDING) {
            deductStockDirectly(sourceStock, transfer.getQuantity());
        } else if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            deductReservedStock(sourceStock, transfer.getQuantity());
        }

        if (isWarehouseTransfer(transfer)) {
            addStockToDestination(transfer);
        }

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedDate(LocalDateTime.now());
        if (completionNote != null && !completionNote.trim().isEmpty()) {
            transfer.setCompletionNote(completionNote.trim());
        } else if (transfer.getTransferType() == TransferType.CUSTOMER_DELIVERY) {
            transfer.setCompletionNote(null);
        }
        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_COMPLETE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer tamamlandı: %s | Ürün=%s | Miktar=%d",
                        describeRoute(saved), saved.getProduct().getName(), saved.getQuantity()));
        notificationService.create(NotificationMessages.TRANSFER_COMPLETED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi tamamladı.", username, saved.getId()),
                DomainEntityType.Stock.name(), saved.getId());
        notifyAdminIfNonAdmin(saved, "tamamladı");
        logger.info("Transfer completed successfully with id: {}", saved.getId());
        
        // Fetch with relations again to avoid LazyInitializationException in mapper
        return stockTransferRepository.findByIdWithRelations(saved.getId())
                .orElse(saved);
    }

    @Override
    public StockTransfer cancelTransfer(Long transferId, String cancellationReason) {
        logger.info("Cancelling transfer with id: {}", transferId);
        StockTransfer transfer = getTransferByIdOrThrow(transferId);

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            logger.warn("Cannot cancel completed transfer. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.CANNOT_CANCEL_COMPLETED);
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            logger.warn("Transfer already cancelled. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.TRANSFER_ALREADY_CANCELLED);
        }

        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            Stock sourceStock = findSourceStockOrThrow(transfer);
            releaseReservedStock(sourceStock, transfer.getQuantity());
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledDate(LocalDateTime.now());
        transfer.setCancellationReason(cancellationReason);
        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_CANCEL, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer iptal edildi: Sebep=%s", cancellationReason));
        notificationService.create(NotificationMessages.TRANSFER_CANCELLED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi iptal etti. Sebep: %s", username, saved.getId(), cancellationReason),
                DomainEntityType.Stock.name(), saved.getId());
        logger.info("Transfer cancelled successfully with id: {}", saved.getId());
        
        // Fetch with relations again to avoid LazyInitializationException in mapper
        return stockTransferRepository.findByIdWithRelations(saved.getId())
                .orElse(saved);
    }

    @Override
    public StockTransfer updateTransfer(Long transferId, StockTransfer updatedTransfer) {
        logger.info("Updating transfer with id: {}", transferId);
        StockTransfer transfer = getTransferByIdOrThrow(transferId);

        if (transfer.getStatus() != TransferStatus.PENDING) {
            logger.warn("Only pending transfers can be updated. Current status: {}", transfer.getStatus());
            throw new WarehouseManagementException(ErrorCode.ONLY_PENDING_CAN_BE_UPDATED);
        }

        updateTransferFields(transfer, updatedTransfer);

        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_UPDATE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                "Transfer güncellendi");
        notificationService.create(NotificationMessages.TRANSFER_UPDATED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi güncelledi.", username, saved.getId()),
                DomainEntityType.Stock.name(), saved.getId());
        logger.info("Transfer updated successfully with id: {}", saved.getId());
        
        // Fetch with relations again to avoid LazyInitializationException in mapper
        return stockTransferRepository.findByIdWithRelations(saved.getId())
                .orElse(saved);
    }

    @Override
    public void deleteTransfer(Long transferId) {
        logger.info("Deleting transfer with id: {}", transferId);
        StockTransfer transfer = getTransferByIdOrThrow(transferId);

        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            logger.warn("Cannot delete transfer in transit. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.CANNOT_DELETE_IN_TRANSIT);
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            logger.warn("Cannot delete completed transfer. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.CANNOT_DELETE_COMPLETED);
        }

        stockTransferRepository.delete(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_DELETE, DomainEntityType.StockTransfer.name(), transferId, username,
                "Transfer silindi");
        notificationService.create(NotificationMessages.TRANSFER_DELETED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi sildi.", username, transferId),
                DomainEntityType.Stock.name(), transferId);
        logger.info("Transfer deleted successfully with id: {}", transferId);
    }

    private TransferType validateTransferCreation(StockTransfer transfer) {
        ValidationUtil.requireNonNull(transfer.getSourceWarehouse(), "Source warehouse");
        ValidationUtil.requireNonNull(transfer.getSourceWarehouse().getId(), "Source warehouse ID");
        ValidationUtil.requireNonNull(transfer.getProduct(), "Product");
        ValidationUtil.requireNonNull(transfer.getProduct().getId(), "Product ID");

        TransferType transferType = transfer.getTransferType() != null
                ? transfer.getTransferType()
                : TransferType.WAREHOUSE;
        transfer.setTransferType(transferType);

        if (transferType == TransferType.WAREHOUSE) {
            ValidationUtil.requireNonNull(transfer.getDestinationWarehouse(), "Destination warehouse");
            ValidationUtil.requireNonNull(transfer.getDestinationWarehouse().getId(), "Destination warehouse ID");
        } else {
            ValidationUtil.requireNotBlank(transfer.getCustomerFullName(), "Customer full name");
            ValidationUtil.requireNotBlank(transfer.getCustomerPhone(), "Customer phone");
            ValidationUtil.requireNotBlank(transfer.getCustomerAddress(), "Customer address");
        }
        return transferType;
    }

    private boolean isWarehouseTransfer(StockTransfer transfer) {
        return transfer.getTransferType() == null || transfer.getTransferType() == TransferType.WAREHOUSE;
    }

    private String describeRoute(StockTransfer transfer) {
        String sourceName = transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : "Bilinmiyor";
        if (isWarehouseTransfer(transfer) && transfer.getDestinationWarehouse() != null) {
            return String.format("%s -> %s", sourceName, transfer.getDestinationWarehouse().getName());
        }
        String customer = transfer.getCustomerFullName();
        if (customer == null || customer.trim().isEmpty()) {
            customer = "Müşteri";
        }
        return String.format("%s -> Müşteri (%s)", sourceName, customer);
    }

    private void notifyAdminIfNonAdmin(StockTransfer transfer, String actionVerb) {
        if (isCurrentUserAdmin()) {
            return;
        }
        String username = CurrentUser.usernameOrSystem();
        String verb = actionVerb != null ? actionVerb : "işledi";
        notificationService.create(
                NotificationMessages.TRANSFER_ADMIN_ALERT_TITLE,
                String.format("Kullanıcı %s, #%d numaralı (%s) %s transferini %s.",
                        username,
                        transfer.getId(),
                        describeRoute(transfer),
                        transfer.getTransferType() == TransferType.CUSTOMER_DELIVERY ? "müşteri sevkiyat" : "depo",
                        verb),
                DomainEntityType.StockTransfer.name(),
                transfer.getId());
    }

    private boolean isCurrentUserAdmin() {
        return RoleName.ADMIN.name().equalsIgnoreCase(CurrentUser.getRole());
    }

    private void validateSufficientStock(Product product, Warehouse warehouse, Integer quantity) {
        Optional<Stock> stockOpt = stockRepository.findByProductAndWarehouse(product, warehouse);
        if (stockOpt.isEmpty()) {
            logger.warn("Product not found in warehouse. Product id: {}, Warehouse id: {}", product.getId(), warehouse.getId());
            throw new WarehouseManagementException(ErrorCode.PRODUCT_NOT_IN_WAREHOUSE);
        }

        Stock stock = stockOpt.get();
        if (stock.getAvailableQuantity() < quantity) {
            logger.warn("Insufficient stock. Available: {}, Requested: {}", stock.getAvailableQuantity(), quantity);
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private void validateSufficientAvailableStock(Stock stock, Integer quantity) {
        if (stock.getAvailableQuantity() < quantity) {
            logger.warn("Insufficient available stock. Available: {}, Requested: {}", stock.getAvailableQuantity(), quantity);
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private Stock findSourceStockOrThrow(StockTransfer transfer) {
        return stockRepository.findByProductAndWarehouse(transfer.getProduct(), transfer.getSourceWarehouse())
                .orElseThrow(() -> {
                    logger.warn("Source stock not found for transfer id: {}", transfer.getId());
                    return new WarehouseManagementException(ErrorCode.PRODUCT_NOT_IN_WAREHOUSE);
                });
    }

    private void reserveStockForTransfer(Stock stock, Integer quantity) {
        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        stockRepository.save(stock);
    }

    private void releaseReservedStock(Stock stock, Integer quantity) {
        int currentReserved = stock.getReservedQuantity() != null ? stock.getReservedQuantity() : 0;
        int newReserved = Math.max(0, currentReserved - quantity); // Ensure non-negative
        stock.setReservedQuantity(newReserved);
        stockRepository.save(stock);
    }

    private void deductStockDirectly(Stock stock, Integer quantity) {
        stock.setQuantity(stock.getQuantity() - quantity);
        stockRepository.save(stock);
    }

    private void deductReservedStock(Stock stock, Integer quantity) {
        stock.setQuantity(stock.getQuantity() - quantity);
        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        stockRepository.save(stock);
    }

    private void addStockToDestination(StockTransfer transfer) {
        if (transfer.getDestinationWarehouse() == null) {
            logger.warn("Destination warehouse missing while adding stock for transfer {}", transfer.getId());
            return;
        }
        Optional<Stock> destinationStockOpt = stockRepository.findByProductAndWarehouse(
                transfer.getProduct(), transfer.getDestinationWarehouse());

        Stock destinationStock;
        if (destinationStockOpt.isPresent()) {
            destinationStock = destinationStockOpt.get();
            destinationStock.setQuantity(destinationStock.getQuantity() + transfer.getQuantity());
        } else {
            destinationStock = createNewStock(transfer);
        }

        stockRepository.save(destinationStock);
    }

    private Stock createNewStock(StockTransfer transfer) {
        Stock stock = new Stock();
        stock.setProduct(transfer.getProduct());
        stock.setWarehouse(transfer.getDestinationWarehouse());
        stock.setQuantity(transfer.getQuantity());
        stock.setMinStockLevel(0);
        stock.setReservedQuantity(0);
        stock.setConsignedQuantity(0);
        return stock;
    }

    private void updateTransferFields(StockTransfer transfer, StockTransfer updatedTransfer) {
        if (updatedTransfer.getDriverName() != null) {
            transfer.setDriverName(updatedTransfer.getDriverName());
        }
        if (updatedTransfer.getDriverTcId() != null) {
            transfer.setDriverTcId(updatedTransfer.getDriverTcId());
        }
        if (updatedTransfer.getDriverPhone() != null) {
            transfer.setDriverPhone(updatedTransfer.getDriverPhone());
        }
        if (updatedTransfer.getVehiclePlate() != null) {
            transfer.setVehiclePlate(updatedTransfer.getVehiclePlate());
        }
        if (updatedTransfer.getNotes() != null) {
            transfer.setNotes(updatedTransfer.getNotes());
        }
        if (updatedTransfer.getCustomerFullName() != null) {
            transfer.setCustomerFullName(updatedTransfer.getCustomerFullName());
        }
        if (updatedTransfer.getCustomerPhone() != null) {
            transfer.setCustomerPhone(updatedTransfer.getCustomerPhone());
        }
        if (updatedTransfer.getCustomerAddress() != null) {
            transfer.setCustomerAddress(updatedTransfer.getCustomerAddress());
        }
        if (updatedTransfer.getTransferDate() != null) {
            transfer.setTransferDate(updatedTransfer.getTransferDate());
        }
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> {
                    logger.warn("Product not found with id: {}", productId);
                    return new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND);
                });
    }

    private Warehouse findWarehouseOrThrow(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> {
                    logger.warn("Warehouse not found with id: {}", warehouseId);
                    return new WarehouseManagementException(ErrorCode.WAREHOUSE_NOT_FOUND);
                });
    }
}

