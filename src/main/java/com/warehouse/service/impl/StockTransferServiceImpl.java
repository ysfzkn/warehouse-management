package com.warehouse.service.impl;

import com.warehouse.dto.AuditMetadata;
import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.NotificationRequest;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.dto.StockTransferDeletionResult;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.enums.TransferApprovalStatus;
import com.warehouse.enums.WarehouseType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.service.AuditService;
import com.warehouse.service.NotificationService;
import com.warehouse.service.StockTransferService;
import com.warehouse.service.StockService;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.util.CurrentUser;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.warehouse.constants.NotificationMessages;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


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
    private final StockService stockService;
    private final WarehouseRepository warehouseRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AdminSecurityService adminSecurityService;

    public StockTransferServiceImpl(StockTransferRepository stockTransferRepository,
                                    StockRepository stockRepository,
                                    ProductRepository productRepository,
                                    WarehouseRepository warehouseRepository,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    AdminSecurityService adminSecurityService,
                                    StockService stockService) {
        this.stockTransferRepository = stockTransferRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.adminSecurityService = adminSecurityService;
        this.stockService = stockService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getAllTransfers() {
        logger.debug("Fetching all transfers");
        return stockTransferRepository.findAllOrderByTransferDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockTransfer> getTransfersPaged(StockTransferFilter filter, Pageable pageable) {
        TransferFilterParams params = TransferFilterParams.from(filter);
        logger.debug("Fetching paged transfers - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        // Safe bounds when null so PostgreSQL can infer parameter type (avoids "could not determine data type of parameter")
        LocalDateTime transferDateFrom = filter != null && filter.getTransferDateFrom() != null ? filter.getTransferDateFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime transferDateTo = filter != null && filter.getTransferDateTo() != null ? filter.getTransferDateTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        LocalDateTime createdAtFrom = filter != null && filter.getCreatedAtFrom() != null ? filter.getCreatedAtFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime createdAtTo = filter != null && filter.getCreatedAtTo() != null ? filter.getCreatedAtTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        return stockTransferRepository.findByFilters(
                null,
                params.status,
                params.transferType,
                params.sourceWarehouseId,
                params.destinationWarehouseId,
                params.driverNameProvided,
                params.driverPattern,
                params.productNameProvided,
                params.productNamePattern,
                params.skuProvided,
                params.skuPattern,
                params.notesProvided,
                params.notesPattern,
                transferDateFrom,
                transferDateTo,
                createdAtFrom,
                createdAtTo,
                pageable);
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
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersForCurrentUser() {
        String username = CurrentUser.usernameOrSystem();
        logger.debug("Fetching transfers for user: {}", username);
        return stockTransferRepository.findAllByCreatedByOrderByTransferDateDesc(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockTransfer> getTransfersForCurrentUserPaged(StockTransferFilter filter, Pageable pageable) {
        String username = CurrentUser.usernameOrSystem();
        TransferFilterParams params = TransferFilterParams.from(filter);
        logger.debug("Fetching paged transfers for user: {} - page: {}", username, pageable.getPageNumber());
        LocalDateTime transferDateFrom = filter != null && filter.getTransferDateFrom() != null ? filter.getTransferDateFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime transferDateTo = filter != null && filter.getTransferDateTo() != null ? filter.getTransferDateTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        LocalDateTime createdAtFrom = filter != null && filter.getCreatedAtFrom() != null ? filter.getCreatedAtFrom() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime createdAtTo = filter != null && filter.getCreatedAtTo() != null ? filter.getCreatedAtTo() : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        return stockTransferRepository.findByFilters(
                username,
                params.status,
                params.transferType,
                params.sourceWarehouseId,
                params.destinationWarehouseId,
                params.driverNameProvided,
                params.driverPattern,
                params.productNameProvided,
                params.productNamePattern,
                params.skuProvided,
                params.skuPattern,
                params.notesProvided,
                params.notesPattern,
                transferDateFrom,
                transferDateTo,
                createdAtFrom,
                createdAtTo,
                pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferSummary getTransferSummary(StockTransferFilter filter, boolean currentUserOnly) {
        TransferFilterParams params = TransferFilterParams.from(filter);
        String createdBy = currentUserOnly ? CurrentUser.usernameOrSystem() : null;

        Map<String, Long> statusCounts = stockTransferRepository.countByFiltersGroupedStatus(
                        createdBy,
                        params.status,
                        params.transferType,
                        params.sourceWarehouseId,
                        params.destinationWarehouseId,
                        params.driverNameProvided,
                        params.driverPattern,
                        params.productNameProvided,
                        params.productNamePattern,
                        params.skuProvided,
                        params.skuPattern)
                .stream()
                .collect(Collectors.toMap(
                        sc -> sc.getStatus().name(),
                        StockTransferRepository.StatusCountProjection::getCount));

        Map<String, Long> typeCounts = stockTransferRepository.countByFiltersGroupedTransferType(
                        createdBy,
                        params.status,
                        params.transferType,
                        params.sourceWarehouseId,
                        params.destinationWarehouseId,
                        params.driverNameProvided,
                        params.driverPattern,
                        params.productNameProvided,
                        params.productNamePattern,
                        params.skuProvided,
                        params.skuPattern)
                .stream()
                .collect(Collectors.toMap(
                        tc -> tc.getTransferType() != null ? tc.getTransferType().name() : TransferType.WAREHOUSE.name(),
                        StockTransferRepository.TransferTypeCountProjection::getCount));

        return new StockTransferSummary(statusCounts, typeCounts);
    }

    @Override
    public StockTransfer createTransfer(StockTransfer transfer) {
        logger.info("Creating new transfer");
        Warehouse sourceWarehouse = findWarehouseOrThrow(transfer.getSourceWarehouse().getId());
        List<StockTransferItem> normalizedItems = resolveTransferItems(transfer);
        TransferType transferType = validateTransferCreation(transfer, normalizedItems);

        Warehouse destinationWarehouse = null;
        if (transferType == TransferType.WAREHOUSE) {
            destinationWarehouse = findWarehouseOrThrow(transfer.getDestinationWarehouse().getId());
            EntityValidator.validateWarehousesDifferent(sourceWarehouse, destinationWarehouse);
        } else if (transfer.getDestinationWarehouse() != null && transfer.getDestinationWarehouse().getId() != null) {
            destinationWarehouse = findWarehouseOrThrow(transfer.getDestinationWarehouse().getId());
        }

        validateSufficientStockForItems(sourceWarehouse, normalizedItems);

        transfer.setSourceWarehouse(sourceWarehouse);
        transfer.setDestinationWarehouse(destinationWarehouse);
        transfer.setItems(normalizedItems);
        transfer.setQuantity(calculateTotalQuantity(normalizedItems));
        transfer.setProduct(normalizedItems.size() == 1 ? normalizedItems.get(0).getProduct() : null);
        transfer.setTransferType(transferType);
        String username = CurrentUser.usernameOrSystem();
        transfer.setCreatedBy(username);
        transfer.setStatus(TransferStatus.PENDING);
        boolean isAdminUser = isCurrentUserAdmin();

        // For non-admin users, create an approval request
        if (!isAdminUser) {
            transfer.setApprovalStatus(TransferApprovalStatus.PENDING);
            transfer.setApprovalRequestedBy(username);
            transfer.setApprovalRequestedAt(LocalDateTime.now());
            transfer.setApprovalNote("START_REQUEST");
        } else {
            transfer.setApprovalStatus(TransferApprovalStatus.NONE);
        }

        StockTransfer saved = stockTransferRepository.save(transfer);
        AuditMetadata metadata = buildTransferMetadata(saved);
        auditService.log(AuditAction.TRANSFER_CREATE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer oluşturuldu: %s | Ürünler=%s",
                        describeRoute(saved), describeItems(saved)),
                metadata);
        
        if (isAdminUser) {
            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_CREATED_TITLE,
                    String.format("Kullanıcı %s, %s yönünde %s transferi oluşturdu. Ürünler: %s", username,
                            describeRoute(saved), transferType == TransferType.CUSTOMER_DELIVERY ? "müşteri sevkiyatı" : "depo", describeItems(saved)),
                    saved));
        } else {
            // Notify admins about the transfer approval request
            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_APPROVAL_REQUESTED_TITLE,
                    String.format("Kullanıcı %s, %s yönünde %s transferi için onay talep etti. Ürünler: %s",
                            username, describeRoute(saved),
                            transferType == TransferType.CUSTOMER_DELIVERY ? "müşteri sevkiyatı" : "depo",
                            describeItems(saved)),
                    saved));
        }
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

        boolean isAdminUser = isCurrentUserAdmin();
        
        // If transfer was created by non-admin and needs approval, check if it's already approved
        if (transfer.getApprovalStatus() == TransferApprovalStatus.PENDING) {
            if (!isAdminUser) {
                // Non-admin trying to start a pending approval transfer - should not happen, but handle gracefully
                StockTransfer saved = submitStartApprovalRequest(transfer);
                logger.info("Transfer {} awaiting approval before start", saved.getId());
                return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
            }
            // Admin is approving and starting the transfer
            transfer.setApprovalStatus(TransferApprovalStatus.APPROVED);
            transfer.setApprovalDecisionBy(CurrentUser.usernameOrSystem());
            transfer.setApprovalDecisionAt(LocalDateTime.now());
        } else if (!isAdminUser && transfer.getApprovalStatus() == TransferApprovalStatus.NONE) {
            // Non-admin trying to start a transfer that was created without approval (shouldn't happen for new flow)
            StockTransfer saved = submitStartApprovalRequest(transfer);
            logger.info("Transfer {} awaiting approval before start", saved.getId());
            return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
        }

        List<StockTransferItem> items = getTransferItemsOrFallback(transfer);
        Map<String, Stock> sourceStocks = loadSourceStocks(transfer.getSourceWarehouse(), items);
        for (StockTransferItem item : items) {
            Stock sourceStock = sourceStocks.get(stockKey(item));
            validateSufficientAvailableStock(sourceStock, item.getQuantity());
            reserveStockForTransfer(sourceStock, item.getQuantity(), transfer);
        }

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        AuditMetadata metadata = buildTransferMetadata(saved);
        auditService.log(AuditAction.TRANSFER_START, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer yola çıkarıldı: %s | Ürünler=%s (Stok rezerve edildi)",
                        describeRoute(saved), describeItems(saved)),
                metadata);
        if (isAdminUser) {
            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_STARTED_TITLE,
                    String.format("Kullanıcı %s, #%d numaralı transferi yola çıkardı. Ürünler: %s", username, saved.getId(), describeItems(saved)),
                    saved));
        }
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
            logger.info("Transfer already completed, returning existing record. Transfer id: {}", transferId);
            return stockTransferRepository.findByIdWithRelations(transferId).orElse(transfer);
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            logger.warn("Cannot complete cancelled transfer. Transfer id: {}", transferId);
            throw new WarehouseManagementException(ErrorCode.CANNOT_CANCEL_COMPLETED);
        }

        List<StockTransferItem> items = getTransferItemsOrFallback(transfer);
        Map<String, Stock> sourceStocks = loadSourceStocks(transfer.getSourceWarehouse(), items);

        if (transfer.getStatus() == TransferStatus.PENDING) {
            for (StockTransferItem item : items) {
                Stock sourceStock = sourceStocks.get(stockKey(item));
                deductStockDirectly(sourceStock, item.getQuantity(), transfer);
            }
        } else if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            for (StockTransferItem item : items) {
                Stock sourceStock = sourceStocks.get(stockKey(item));
                deductReservedStock(sourceStock, item.getQuantity(), transfer);
            }
        }

        if (isWarehouseTransfer(transfer)) {
            addStockToDestination(transfer, items);
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
        boolean isAdminUser = isCurrentUserAdmin();
        AuditMetadata metadata = buildTransferMetadata(saved);
        auditService.log(AuditAction.TRANSFER_COMPLETE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer tamamlandı: %s | Ürünler=%s",
                        describeRoute(saved), describeItems(saved)),
                metadata);
        if (isAdminUser) {
            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_COMPLETED_TITLE,
                    String.format("Kullanıcı %s, #%d numaralı transferi tamamladı. Ürünler: %s", username, saved.getId(), describeItems(saved)),
                    saved));
        }
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
            List<StockTransferItem> items = getTransferItemsOrFallback(transfer);
            Map<String, Stock> sourceStocks = loadSourceStocks(transfer.getSourceWarehouse(), items);
            for (StockTransferItem item : items) {
                Stock sourceStock = sourceStocks.get(stockKey(item));
                releaseReservedStock(sourceStock, item.getQuantity());
            }
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledDate(LocalDateTime.now());
        transfer.setCancellationReason(cancellationReason);
        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        boolean isAdminUser = isCurrentUserAdmin();
        AuditMetadata metadata = buildTransferMetadata(saved);
        auditService.log(AuditAction.TRANSFER_CANCEL, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer iptal edildi: Sebep=%s", cancellationReason),
                metadata);
        if (isAdminUser) {
            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_CANCELLED_TITLE,
                    String.format("Kullanıcı %s, #%d numaralı transferi iptal etti. Sebep: %s", username, saved.getId(), cancellationReason),
                    saved));
        }
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
        AuditMetadata metadata = buildTransferMetadata(saved);
        auditService.log(AuditAction.TRANSFER_UPDATE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                "Transfer güncellendi", metadata);
        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_UPDATED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi güncelledi.", username, saved.getId()),
                saved));
        logger.info("Transfer updated successfully with id: {}", saved.getId());
        
        // Fetch with relations again to avoid LazyInitializationException in mapper
        return stockTransferRepository.findByIdWithRelations(saved.getId())
                .orElse(saved);
    }

    @Override
    public StockTransferDeletionResult deleteTransfer(Long transferId, String adminSecurityCode) {
        logger.info("Deleting transfer with id: {}", transferId);
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        boolean isAdmin = isCurrentUserAdmin();

        if (!isAdmin) {
            StockTransferDeletionResult result = submitDeletionApprovalRequest(transfer);
            logger.info("Deletion approval requested for transfer id {}", transferId);
            return result;
        }

        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);

        // Tüm transfer durumlarının (yolda, tamamlanan, beklemede) silinmesine izin veriyoruz
        AuditMetadata metadata = buildTransferMetadata(transfer);
        stockTransferRepository.delete(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_DELETE, DomainEntityType.StockTransfer.name(), transferId, username,
                String.format("Transfer silindi: %s | Ürünler=%s", describeRoute(transfer), describeItems(transfer)), metadata);
        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_DELETED_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi sildi. Rota: %s | Ürünler: %s", username, transferId, describeRoute(transfer), describeItems(transfer)),
                transfer));
        logger.info("Transfer deleted successfully with id: {}", transferId);
        return StockTransferDeletionResult.deleted();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransferRequestsForCurrentUser() {
        String username = CurrentUser.usernameOrSystem();
        logger.debug("Fetching transfer requests for user: {}", username);
        List<StockTransfer> allUserTransfers = stockTransferRepository.findAllByCreatedByOrderByTransferDateDesc(username);
        // Filter transfers that have approval status (PENDING, APPROVED, REJECTED)
        return allUserTransfers.stream()
                .filter(t -> t.getApprovalStatus() != null && t.getApprovalStatus() != TransferApprovalStatus.NONE)
                .collect(Collectors.toList());
    }

    @Override
    public BulkDeleteResponse deleteTransfers(List<Long> transferIds) {
        if (transferIds == null || transferIds.isEmpty()) {
            logger.warn("Attempted to delete transfers with empty list");
            return new BulkDeleteResponse(0, 0, List.of());
        }
        
        logger.info("Deleting {} transfers", transferIds.size());
        String username = CurrentUser.usernameOrSystem();
        List<BulkDeleteResponse.DeleteError> errors = new java.util.ArrayList<>();
        int successCount = 0;
        
        for (Long transferId : transferIds) {
            try {
                StockTransfer transfer = getTransferByIdOrThrow(transferId);
                
                // Tüm transfer durumlarının (yolda, tamamlanan, beklemede) silinmesine izin veriyoruz
                AuditMetadata metadata = buildTransferMetadata(transfer);
                stockTransferRepository.delete(transfer);
                auditService.log(AuditAction.TRANSFER_DELETE, DomainEntityType.StockTransfer.name(), transferId, username,
                        String.format("Transfer silindi: %s | Ürünler=%s", describeRoute(transfer), describeItems(transfer)), metadata);
                successCount++;
                logger.debug("Transfer deleted successfully with id: {}", transferId);
            } catch (WarehouseManagementException e) {
                // Domain exception'ları yakala
                StockTransfer transfer = null;
                try {
                    transfer = getTransferByIdOrThrow(transferId);
                } catch (Exception ex) {
                    // Transfer bulunamadı
                }
                String transferInfo = transfer != null 
                    ? String.format("Transfer #%d (%s → %s)", transferId,
                        transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : "Bilinmeyen",
                        transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getName() 
                            : (transfer.getCustomerFullName() != null ? transfer.getCustomerFullName() : "Bilinmeyen"))
                    : String.format("Transfer #%d", transferId);
                
                errors.add(new BulkDeleteResponse.DeleteError(
                    transferId,
                    transferInfo,
                    null, // Transfer için SKU yok
                    e.getErrorCode().getCode(),
                    e.getMessage()
                ));
                logger.warn("Cannot delete transfer with id {}: {}", transferId, e.getMessage());
            } catch (Exception e) {
                // Diğer hatalar
                StockTransfer transfer = null;
                try {
                    transfer = getTransferByIdOrThrow(transferId);
                } catch (Exception ex) {
                    // Transfer bulunamadı
                }
                String transferInfo = transfer != null 
                    ? String.format("Transfer #%d (%s → %s)", transferId,
                        transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : "Bilinmeyen",
                        transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getName() 
                            : (transfer.getCustomerFullName() != null ? transfer.getCustomerFullName() : "Bilinmeyen"))
                    : String.format("Transfer #%d", transferId);
                
                errors.add(new BulkDeleteResponse.DeleteError(
                    transferId,
                    transferInfo,
                    null,
                    ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                    "Transfer silinirken beklenmeyen bir hata oluştu: " + e.getMessage()
                ));
                logger.error("Error deleting transfer {}: {}", transferId, e.getMessage(), e);
            }
        }
        
        if (successCount > 0) {
            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_DELETED_TITLE,
                    String.format("Kullanıcı %s, %d adet transferi sildi.", username, successCount),
                    null));
        }
        
        logger.info("Batch delete completed: {} successful, {} errors", successCount, errors.size());
        return new BulkDeleteResponse(successCount, errors.size(), errors);
    }

    @Override
    public List<StockTransfer> getTransferApprovals(TransferApprovalStatus status) {
        TransferApprovalStatus effectiveStatus = status != null ? status : TransferApprovalStatus.PENDING;
        if (effectiveStatus == TransferApprovalStatus.NONE) {
            return List.of();
        }
        return stockTransferRepository.findByApprovalStatusOrderByTransferDateDesc(effectiveStatus);
    }

    @Override
    public long countTransferApprovals(TransferApprovalStatus status) {
        TransferApprovalStatus effectiveStatus = status != null ? status : TransferApprovalStatus.PENDING;
        if (effectiveStatus == TransferApprovalStatus.NONE) {
            return 0;
        }
        return stockTransferRepository.countByApprovalStatus(effectiveStatus);
    }

    @Override
    public StockTransfer approveTransferStart(Long transferId, String approvalNote, String adminSecurityCode) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        if (transfer.getApprovalStatus() != TransferApprovalStatus.PENDING) {
            throw new WarehouseManagementException(ErrorCode.INVALID_TRANSFER_STATUS, "Onay bekleyen transfer bulunamadı");
        }
        String username = CurrentUser.usernameOrSystem();
        String trimmedNote = approvalNote != null && !approvalNote.trim().isEmpty() ? approvalNote.trim() : null;

        if (transfer.isDeleteRequest()) {
            adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
            transfer.setApprovalStatus(TransferApprovalStatus.APPROVED);
            transfer.setApprovalDecisionBy(username);
            transfer.setApprovalDecisionAt(LocalDateTime.now());
            transfer.setApprovalNote(trimmedNote);
            transfer.setStatus(TransferStatus.CANCELLED);
            transfer.setCancellationReason(trimmedNote != null ? trimmedNote : "Silme talebi onaylandı");
            StockTransfer saved = stockTransferRepository.save(transfer);

            AuditMetadata metadata = buildTransferMetadata(saved);
            auditService.log(AuditAction.TRANSFER_DELETE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                    String.format("Transfer silme talebi onaylandı: %s | Ürünler=%s",
                            describeRoute(saved), describeItems(saved)), metadata);

            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_DELETED_TITLE,
                    String.format("Yönetici %s, #%d numaralı transferin silme talebini onayladı. Rota: %s",
                            username, saved.getId(), describeRoute(saved)),
                    saved));
            return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
        }

        // Normal onaylarda güvenlik şifresi isteme
        transfer.setApprovalStatus(TransferApprovalStatus.APPROVED);
        transfer.setApprovalDecisionBy(username);
        transfer.setApprovalDecisionAt(LocalDateTime.now());
        transfer.setApprovalNote(trimmedNote);
        stockTransferRepository.save(transfer);

        // Start the transfer (admin approval bypasses the approval check in startTransfer)
        StockTransfer started = startTransfer(transferId);
        
        // Notify admins
        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_APPROVAL_APPROVED_TITLE,
                String.format("Yönetici %s, #%d numaralı transferi onayladı ve başlattı. Rota: %s",
                        username, started.getId(), describeRoute(started)),
                started));
        
        // Notify the user who created the transfer (notification will be visible to all admins and the user can see it in their requests)
        AuditMetadata metadata = buildTransferMetadata(started);
        auditService.log(AuditAction.TRANSFER_APPROVE, DomainEntityType.StockTransfer.name(), started.getId(), username,
                String.format("Transfer onaylandı ve başlatıldı: %s | Ürünler=%s",
                        describeRoute(started), describeItems(started)),
                metadata);
        
        return started;
    }

    @Override
    public StockTransfer rejectTransferStart(Long transferId, String rejectionReason) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        if (transfer.getApprovalStatus() != TransferApprovalStatus.PENDING) {
            throw new WarehouseManagementException(ErrorCode.INVALID_TRANSFER_STATUS, "Onay bekleyen transfer bulunamadı");
        }
        String username = CurrentUser.usernameOrSystem();
        String trimmedReason = rejectionReason != null && !rejectionReason.trim().isEmpty() ? rejectionReason.trim() : null;
        if ("DELETE_REQUEST".equalsIgnoreCase(transfer.getApprovalNote())) {
            transfer.setApprovalStatus(TransferApprovalStatus.REJECTED);
            transfer.setApprovalDecisionBy(username);
            transfer.setApprovalDecisionAt(LocalDateTime.now());
            transfer.setApprovalNote(trimmedReason);
            StockTransfer saved = stockTransferRepository.save(transfer);

            notificationService.create(buildTransferNotification(
                    NotificationMessages.TRANSFER_APPROVAL_REJECTED_TITLE,
                    String.format("Yönetici %s, #%d numaralı transferin silme talebini reddetti.%s",
                            username,
                            saved.getId(),
                            trimmedReason != null ? " Not: " + trimmedReason : ""),
                    saved));

            AuditMetadata metadata = buildTransferMetadata(saved);
            auditService.log(AuditAction.TRANSFER_REJECT, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                    String.format("Transfer silme talebi reddedildi: %s%s",
                            describeRoute(saved),
                            trimmedReason != null ? " | Not: " + trimmedReason : ""),
                    metadata);
            return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
        }
        transfer.setApprovalStatus(TransferApprovalStatus.REJECTED);
        transfer.setApprovalDecisionBy(username);
        transfer.setApprovalDecisionAt(LocalDateTime.now());
        transfer.setApprovalNote(trimmedReason);
        StockTransfer saved = stockTransferRepository.save(transfer);
        
        // Notify admins
        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_APPROVAL_REJECTED_TITLE,
                String.format("Yönetici %s, #%d numaralı transferi reddetti.%s",
                        username,
                        saved.getId(),
                        trimmedReason != null ? " Not: " + trimmedReason : ""),
                saved));
        
        // Log rejection for audit trail
        AuditMetadata metadata = buildTransferMetadata(saved);
        auditService.log(AuditAction.TRANSFER_REJECT, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                String.format("Transfer reddedildi: %s | Ürünler=%s%s",
                        describeRoute(saved), describeItems(saved),
                        trimmedReason != null ? " | Not: " + trimmedReason : ""),
                metadata);
        
        return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
    }

    private StockTransferDeletionResult submitDeletionApprovalRequest(StockTransfer transfer) {
        // Avoid duplicating pending delete requests
        if (transfer.isDeleteRequest()
                && transfer.getApprovalStatus() == TransferApprovalStatus.PENDING) {
            return StockTransferDeletionResult.approval("Silme talebi zaten iletilmiş durumda.");
        }

        String username = CurrentUser.usernameOrSystem();
        transfer.setApprovalStatus(TransferApprovalStatus.PENDING);
        transfer.setApprovalRequestedBy(username);
        transfer.setApprovalRequestedAt(LocalDateTime.now());
        transfer.setApprovalDecisionBy(null);
        transfer.setApprovalDecisionAt(null);
        transfer.setApprovalNote(null);
        transfer.setDeleteRequest(true);
        StockTransfer saved = stockTransferRepository.save(transfer);

        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_DELETE_REQUEST_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferin silinmesi için onay istedi. Rota: %s", username, saved.getId(), describeRoute(saved)),
                saved));

        return StockTransferDeletionResult.approval("Silme talebi yöneticilere iletildi. Onaylandığında kayıt silinecek.");
    }

    private TransferType validateTransferCreation(StockTransfer transfer, List<StockTransferItem> items) {
        ValidationUtil.requireNonNull(transfer.getSourceWarehouse(), "Source warehouse");
        ValidationUtil.requireNonNull(transfer.getSourceWarehouse().getId(), "Source warehouse ID");
        ValidationUtil.requireNonEmpty(items, "Transfer items");

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
        return String.format("%s -> %s", sourceName, customer);
    }

    private void notifyAdminIfNonAdmin(StockTransfer transfer, String actionVerb) {
        if (isCurrentUserAdmin()) {
            return;
        }
        String username = CurrentUser.usernameOrSystem();
        String verb = actionVerb != null ? actionVerb : "işledi";
        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_ADMIN_ALERT_TITLE,
                String.format("Kullanıcı %s, #%d numaralı (%s) %s transferini %s.",
                        username,
                        transfer.getId(),
                        describeRoute(transfer),
                        transfer.getTransferType() == TransferType.CUSTOMER_DELIVERY ? "müşteri sevkiyat" : "depo",
                        verb),
                transfer));
    }

    private boolean isCurrentUserAdmin() {
        return RoleName.ADMIN.name().equalsIgnoreCase(CurrentUser.getRole());
    }

    private void validateSufficientAvailableStock(Stock stock, Integer quantity) {
        if (stock.getAvailableQuantity() < quantity) {
            logger.warn("Insufficient available stock. Available: {}, Requested: {}", stock.getAvailableQuantity(), quantity);
            throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private void reserveStockForTransfer(Stock stock, Integer quantity, StockTransfer transfer) {
        stock.setReservedQuantity(stock.getReservedQuantity() + quantity);
        Stock saved = stockRepository.save(stock);
        
        // Log stock reservation for transfer
        String username = CurrentUser.usernameOrSystem();
        Warehouse warehouse = saved.getWarehouse();
        Product product = saved.getProduct();
        boolean isEmanetDepo = warehouse != null && warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO;
        String customerName = isEmanetDepo ? saved.getCustomerName() : null;
        String customerPhone = isEmanetDepo ? saved.getCustomerPhone() : null;
        
        String transferInfo = String.format("Transfer #%d", transfer.getId());
        String routeInfo = describeRoute(transfer);
        if (routeInfo != null && !routeInfo.isEmpty()) {
            transferInfo += " - " + routeInfo;
        }
        
        AuditMetadata metadata = AuditMetadata.builder()
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(quantity)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .transferId(transfer.getId())
                .build();
        
        String detailsMessage = String.format("Stok transfer için rezerve edildi: +%s adet (Rezerve=%s) | %s | Depo=%s, Ürün=%s",
                String.valueOf(quantity), String.valueOf(saved.getReservedQuantity()),
                transferInfo, warehouse != null ? warehouse.getName() : "N/A",
                product != null ? product.getName() : "N/A");
        if (isEmanetDepo && customerName != null && !customerName.trim().isEmpty()) {
            detailsMessage += String.format(" | Müşteri: %s", customerName);
        }
        
        auditService.log(AuditAction.STOCK_RESERVE, DomainEntityType.Stock.name(), saved.getId(), username,
                detailsMessage, metadata);
    }

    private void releaseReservedStock(Stock stock, Integer quantity) {
        int currentReserved = stock.getReservedQuantity() != null ? stock.getReservedQuantity() : 0;
        int newReserved = Math.max(0, currentReserved - quantity); // Ensure non-negative
        stock.setReservedQuantity(newReserved);
        stockRepository.save(stock);
    }

    private void deductStockDirectly(Stock stock, Integer quantity, StockTransfer transfer) {
        stock.setQuantity(stock.getQuantity() - quantity);
        Stock saved = stockRepository.save(stock);
        
        // Log stock removal for transfer
        logStockRemovalForTransfer(saved, quantity, transfer, false);
    }

    private void deductReservedStock(Stock stock, Integer quantity, StockTransfer transfer) {
        stock.setQuantity(stock.getQuantity() - quantity);
        stock.setReservedQuantity(stock.getReservedQuantity() - quantity);
        Stock saved = stockRepository.save(stock);
        
        // Log stock removal for transfer
        logStockRemovalForTransfer(saved, quantity, transfer, true);
    }
    
    private void logStockRemovalForTransfer(Stock stock, Integer quantity, StockTransfer transfer, boolean wasReserved) {
        String username = CurrentUser.usernameOrSystem();
        Warehouse warehouse = stock.getWarehouse();
        Product product = stock.getProduct();
        boolean isEmanetDepo = warehouse != null && warehouse.getWarehouseType() == WarehouseType.EMANET_DEPO;
        String customerName = isEmanetDepo ? stock.getCustomerName() : null;
        String customerPhone = isEmanetDepo ? stock.getCustomerPhone() : null;
        
        // Get customer info from transfer if it's a customer delivery
        String transferCustomerName = null;
        String transferCustomerPhone = null;
        if (transfer.getTransferType() == TransferType.CUSTOMER_DELIVERY) {
            transferCustomerName = transfer.getCustomerFullName();
            transferCustomerPhone = transfer.getCustomerPhone();
        }
        
        // Use transfer customer info if available, otherwise use stock customer info
        String finalCustomerName = transferCustomerName != null ? transferCustomerName : customerName;
        String finalCustomerPhone = transferCustomerPhone != null ? transferCustomerPhone : customerPhone;
        
        String transferInfo = String.format("Transfer #%d", transfer.getId());
        String routeInfo = describeRoute(transfer);
        if (routeInfo != null && !routeInfo.isEmpty()) {
            transferInfo += " - " + routeInfo;
        }
        
        // Build customer delivery message
        String customerDeliveryInfo = "";
        if (transfer.getTransferType() == TransferType.CUSTOMER_DELIVERY && finalCustomerName != null && !finalCustomerName.trim().isEmpty()) {
            customerDeliveryInfo = String.format(" | %s müşterisine teslim edildi", finalCustomerName);
        }
        
        AuditMetadata metadata = AuditMetadata.builder()
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(-quantity)
                .customerName(finalCustomerName)
                .customerPhone(finalCustomerPhone)
                .transferId(transfer.getId())
                .build();
        
        String reservedInfo = wasReserved ? " (Rezerve'den çıkarıldı)" : "";
        String detailsMessage = String.format("Stok transfer yoluyla azaltıldı: -%s adet → Yeni=%s%s | %s | Depo=%s, Ürün=%s%s",
                String.valueOf(quantity), String.valueOf(stock.getQuantity()), reservedInfo,
                transferInfo, warehouse != null ? warehouse.getName() : "N/A",
                product != null ? product.getName() : "N/A", customerDeliveryInfo);
        if (isEmanetDepo && customerName != null && !customerName.trim().isEmpty() && transferCustomerName == null) {
            detailsMessage += String.format(" | Emanet Müşteri: %s", customerName);
        }
        
        auditService.log(AuditAction.STOCK_REMOVE, DomainEntityType.Stock.name(), stock.getId(), username,
                detailsMessage, metadata);
    }

    private void addStockToDestination(StockTransfer transfer, List<StockTransferItem> items) {
        if (transfer.getDestinationWarehouse() == null) {
            logger.warn("Destination warehouse missing while adding stock for transfer {}", transfer.getId());
            return;
        }

        for (StockTransferItem item : items) {
            Product product = item.getProduct();
            Optional<Stock> destinationStockOpt = stockRepository.findByProductAndWarehouse(
                    product, transfer.getDestinationWarehouse());

            Stock destinationStock;
            if (destinationStockOpt.isPresent()) {
                destinationStock = destinationStockOpt.get();
                destinationStock.setQuantity(destinationStock.getQuantity() + item.getQuantity());
            } else {
                destinationStock = createNewStock(product, transfer.getDestinationWarehouse(), item.getQuantity());
            }

            stockRepository.save(destinationStock);
        }
    }

    private Stock createNewStock(Product product, Warehouse warehouse, Integer quantity) {
        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(quantity);
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

    private List<StockTransferItem> resolveTransferItems(StockTransfer transfer) {
        List<StockTransferItem> resolved = new ArrayList<>();
        if (transfer.getItems() != null && !transfer.getItems().isEmpty()) {
            for (StockTransferItem item : transfer.getItems()) {
                ValidationUtil.requireNonNull(item.getProduct(), "Product");
                ValidationUtil.requireNonNull(item.getProduct().getId(), "Product ID");
                ValidationUtil.requirePositive(item.getQuantity(), "Quantity");

                Product product = findProductOrThrow(item.getProduct().getId());
                StockTransferItem normalized = new StockTransferItem();
                normalized.setTransfer(transfer);
                normalized.setProduct(product);
                normalized.setQuantity(item.getQuantity());
                normalized.setStockId(item.getStockId());
                resolved.add(normalized);
            }
        } else {
            ValidationUtil.requireNonNull(transfer.getProduct(), "Product");
            ValidationUtil.requireNonNull(transfer.getProduct().getId(), "Product ID");
            ValidationUtil.requirePositive(transfer.getQuantity(), "Quantity");
            Product product = findProductOrThrow(transfer.getProduct().getId());
            StockTransferItem fallback = new StockTransferItem();
            fallback.setTransfer(transfer);
            fallback.setProduct(product);
            fallback.setQuantity(transfer.getQuantity());
            resolved.add(fallback);
        }

        return resolved;
    }

    private List<StockTransferItem> getTransferItemsOrFallback(StockTransfer transfer) {
        if (transfer.getItems() != null && !transfer.getItems().isEmpty()) {
            return transfer.getItems();
        }
        if (transfer.getProduct() == null) {
            return new ArrayList<>();
        }
        StockTransferItem item = new StockTransferItem();
        item.setTransfer(transfer);
        item.setProduct(transfer.getProduct());
        item.setQuantity(transfer.getQuantity());
        List<StockTransferItem> fallback = new ArrayList<>();
        fallback.add(item);
        return fallback;
    }

    private int calculateTotalQuantity(List<StockTransferItem> items) {
        return items.stream()
                .map(StockTransferItem::getQuantity)
                .filter(q -> q != null && q > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String describeItems(StockTransfer transfer) {
        return describeItems(getTransferItemsOrFallback(transfer));
    }

    private String describeItems(List<StockTransferItem> items) {
        if (items == null || items.isEmpty()) {
            return "Ürün bulunmuyor";
        }
        return items.stream()
                .map(item -> {
                    String productName = item.getProduct() != null ? item.getProduct().getName() : "Ürün";
                    return String.format("%d x %s", item.getQuantity(), productName);
                })
                .collect(Collectors.joining(", "));
    }

    private void validateSufficientStockForItems(Warehouse warehouse, List<StockTransferItem> items) {
        Map<String, Stock> stocks = loadSourceStocks(warehouse, items);
        for (StockTransferItem item : items) {
            Stock stock = stocks.get(stockKey(item));
            validateSufficientAvailableStock(stock, item.getQuantity());
        }
    }

    private Map<String, Stock> loadSourceStocks(Warehouse warehouse, List<StockTransferItem> items) {
        Map<String, Stock> result = new LinkedHashMap<>();

        // First resolve items that carry explicit stockId
        for (StockTransferItem item : items) {
            if (item.getStockId() != null) {
                Stock stock = stockService.getStockByIdOrThrow(item.getStockId());
                if (!Objects.equals(stock.getWarehouse().getId(), warehouse.getId())) {
                    logger.warn("Stock {} does not belong to warehouse {} for transfer", stock.getId(), warehouse.getId());
                    throw new WarehouseManagementException(ErrorCode.PRODUCT_NOT_IN_WAREHOUSE);
                }
                result.put(stockKey(item), stock);
            }
        }

        // Fetch remaining by product for items without stockId
        List<Long> productIds = items.stream()
                .filter(it -> it.getStockId() == null)
                .map(it -> it.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());

        if (!productIds.isEmpty()) {
            List<Stock> stockList = stockRepository.findByWarehouseAndProductIds(warehouse, productIds);
            for (Stock stock : stockList) {
                String key = "P" + stock.getProduct().getId();
                result.putIfAbsent(key, stock);
            }
        }

        for (StockTransferItem item : items) {
            String key = stockKey(item);
            if (!result.containsKey(key)) {
                logger.warn("Stock not found in warehouse. productId={}, stockId={}, warehouseId={}", item.getProduct().getId(), item.getStockId(), warehouse.getId());
                throw new WarehouseManagementException(ErrorCode.PRODUCT_NOT_IN_WAREHOUSE);
            }
        }

        return result;
    }

    private String stockKey(StockTransferItem item) {
        return item.getStockId() != null
                ? "S" + item.getStockId()
                : "P" + item.getProduct().getId();
    }

    private StockTransfer submitStartApprovalRequest(StockTransfer transfer) {
        if (transfer.getApprovalStatus() == TransferApprovalStatus.PENDING) {
            throw new WarehouseManagementException(ErrorCode.INVALID_TRANSFER_STATUS, "Transfer already awaiting approval");
        }
        String username = CurrentUser.usernameOrSystem();
        transfer.setApprovalStatus(TransferApprovalStatus.PENDING);
        transfer.setApprovalRequestedBy(username);
        transfer.setApprovalRequestedAt(LocalDateTime.now());
        transfer.setApprovalDecisionBy(null);
        transfer.setApprovalDecisionAt(null);
        transfer.setApprovalNote(null);
        StockTransfer saved = stockTransferRepository.save(transfer);
        notificationService.create(buildTransferNotification(
                NotificationMessages.TRANSFER_START_APPROVAL_REQUEST_TITLE,
                String.format("Kullanıcı %s, #%d numaralı transferi başlatmak için onay istedi.", username, saved.getId()),
                saved));
        notifyAdminIfNonAdmin(saved, "için transfer başlatma onayı oluşturdu");
        return saved;
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

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AuditMetadata buildTransferMetadata(StockTransfer transfer) {
        if (transfer == null) {
            return null;
        }
        Product primaryProduct = resolvePrimaryProduct(transfer);
        Warehouse source = transfer.getSourceWarehouse();
        Warehouse destination = transfer.getDestinationWarehouse();
        Long primaryWarehouseId = source != null ? source.getId() : null;
        String primaryWarehouseName = source != null ? source.getName() : null;

        return AuditMetadata.builder()
                .warehouseId(primaryWarehouseId)
                .warehouseName(primaryWarehouseName)
                .sourceWarehouseId(source != null ? source.getId() : null)
                .sourceWarehouseName(source != null ? source.getName() : null)
                .destinationWarehouseId(destination != null ? destination.getId() : null)
                .destinationWarehouseName(destination != null ? destination.getName() : null)
                .productId(primaryProduct != null ? primaryProduct.getId() : null)
                .productName(primaryProduct != null ? primaryProduct.getName() : null)
                .productSku(primaryProduct != null ? primaryProduct.getSku() : null)
                .quantity(transfer.getQuantity())
                .build();
    }

    private NotificationRequest buildTransferNotification(String title, String message, StockTransfer transfer) {
        Product primaryProduct = resolvePrimaryProduct(transfer);
        Warehouse source = transfer != null ? transfer.getSourceWarehouse() : null;
        Warehouse destination = transfer != null ? transfer.getDestinationWarehouse() : null;
        return NotificationRequest.builder()
                .title(title)
                .message(message)
                .entityType(DomainEntityType.StockTransfer.name())
                .entityId(transfer != null ? transfer.getId() : null)
                .actor(CurrentUser.usernameOrSystem())
                .warehouseId(source != null ? source.getId() : null)
                .warehouseName(source != null ? source.getName() : null)
                .sourceWarehouseId(source != null ? source.getId() : null)
                .sourceWarehouseName(source != null ? source.getName() : null)
                .destinationWarehouseId(destination != null ? destination.getId() : null)
                .destinationWarehouseName(destination != null ? destination.getName() : null)
                .productId(primaryProduct != null ? primaryProduct.getId() : null)
                .productName(primaryProduct != null ? primaryProduct.getName() : null)
                .productSku(primaryProduct != null ? primaryProduct.getSku() : null)
                .quantity(transfer != null ? transfer.getQuantity() : null)
                .build();
    }

    private Product resolvePrimaryProduct(StockTransfer transfer) {
        if (transfer == null) {
            return null;
        }
        if (transfer.getProduct() != null) {
            return transfer.getProduct();
        }
        if (transfer.getItems() != null && !transfer.getItems().isEmpty()) {
            StockTransferItem first = transfer.getItems().get(0);
            return first != null ? first.getProduct() : null;
        }
        return null;
    }

    private static class TransferFilterParams {
        private final TransferStatus status;
        private final TransferType transferType;
        private final Long sourceWarehouseId;
        private final Long destinationWarehouseId;
        private final boolean productNameProvided;
        private final boolean skuProvided;
        private final boolean driverNameProvided;
        private final boolean notesProvided;
        private final String productNamePattern;
        private final String skuPattern;
        private final String driverPattern;
        private final String notesPattern;

        private TransferFilterParams(StockTransferFilter filter) {
            if (filter == null) {
                this.status = null;
                this.transferType = null;
                this.sourceWarehouseId = null;
                this.destinationWarehouseId = null;
                this.productNameProvided = false;
                this.skuProvided = false;
                this.driverNameProvided = false;
                this.notesProvided = false;
                this.productNamePattern = "%";
                this.skuPattern = "%";
                this.driverPattern = "%";
                this.notesPattern = "%";
            } else {
                this.status = filter.getStatus();
                this.transferType = filter.getTransferType();
                this.sourceWarehouseId = filter.getSourceWarehouseId();
                this.destinationWarehouseId = filter.getDestinationWarehouseId();
                String productName = normalize(filter.getProductName());
                String sku = normalize(filter.getSku());
                String driverName = normalize(filter.getDriverName());
                String notes = normalize(filter.getNotes());
                this.productNameProvided = productName != null;
                this.skuProvided = sku != null;
                this.driverNameProvided = driverName != null;
                this.notesProvided = notes != null;
                this.productNamePattern = productNameProvided ? likePattern(productName) : "%";
                this.skuPattern = skuProvided ? likePattern(sku) : "%";
                this.driverPattern = driverNameProvided ? likePattern(driverName) : "%";
                this.notesPattern = notesProvided ? likePattern(notes) : "%";
            }
        }

        private static TransferFilterParams from(StockTransferFilter filter) {
            return new TransferFilterParams(filter);
        }

        private static String likePattern(String value) {
            // Use Turkish locale for proper case-insensitive search with Turkish characters
            return "%" + value.toLowerCase(Locale.forLanguageTag("tr-TR")) + "%";
        }
    }
}

