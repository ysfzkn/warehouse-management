package com.warehouse.service.impl;

import com.warehouse.dto.AuditMetadata;
import com.warehouse.dto.BulkDeleteResponse;
import com.warehouse.dto.CarrierAssignmentRequest;
import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.NotificationRequest;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.dto.StockTransferSummary;
import com.warehouse.dto.StockTransferDeletionResult;
import com.warehouse.dto.TransferReturnDto;
import com.warehouse.dto.TransferReturnRequest;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.TransferReturn;
import com.warehouse.entity.TransferReturnItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.AuditAction;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.enums.TransferApprovalStatus;
import com.warehouse.enums.WarehouseType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.TransferReturnRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.WarehouseRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderStatusHistoryRepository;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.service.AuditService;
import com.warehouse.service.NotificationService;
import com.warehouse.service.StockTransferService;
import com.warehouse.service.StockService;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.util.CurrentUser;
import com.warehouse.util.EntityValidator;
import com.warehouse.util.TurkishText;
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
    private final TransferReturnRepository transferReturnRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final WarehouseRepository warehouseRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AdminSecurityService adminSecurityService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CustomerRepository customerRepository;
    private final com.warehouse.service.DriverService driverService;
    private final com.warehouse.service.VehicleService vehicleService;

    public StockTransferServiceImpl(StockTransferRepository stockTransferRepository,
                                    StockRepository stockRepository,
                                    ProductRepository productRepository,
                                    WarehouseRepository warehouseRepository,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    AdminSecurityService adminSecurityService,
                                    StockService stockService,
                                    OrderRepository orderRepository,
                                    OrderItemRepository orderItemRepository,
                                    OrderStatusHistoryRepository orderStatusHistoryRepository,
                                    CustomerRepository customerRepository,
                                    com.warehouse.service.DriverService driverService,
                                    com.warehouse.service.VehicleService vehicleService,
                                    TransferReturnRepository transferReturnRepository) {
        this.stockTransferRepository = stockTransferRepository;
        this.transferReturnRepository = transferReturnRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.adminSecurityService = adminSecurityService;
        this.stockService = stockService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.customerRepository = customerRepository;
        this.driverService = driverService;
        this.vehicleService = vehicleService;
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
                params.startDate,
                params.endDate,
                params.driverNameProvided,
                params.driverPattern,
                params.productNameProvided,
                params.productNamePattern,
                params.skuProvided,
                params.skuPattern,
                params.notesProvided,
                params.notesPattern,
                params.customerProvided,
                params.customerNamePattern,
                params.customerPhonePattern,
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
                params.startDate,
                params.endDate,
                params.driverNameProvided,
                params.driverPattern,
                params.productNameProvided,
                params.productNamePattern,
                params.skuProvided,
                params.skuPattern,
                params.notesProvided,
                params.notesPattern,
                params.customerProvided,
                params.customerNamePattern,
                params.customerPhonePattern,
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
                        params.startDate,
                        params.endDate,
                        params.driverNameProvided,
                        params.driverPattern,
                        params.productNameProvided,
                        params.productNamePattern,
                        params.skuProvided,
                        params.skuPattern,
                        params.customerProvided,
                        params.customerNamePattern,
                        params.customerPhonePattern)
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
                        params.startDate,
                        params.endDate,
                        params.driverNameProvided,
                        params.driverPattern,
                        params.productNameProvided,
                        params.productNamePattern,
                        params.skuProvided,
                        params.skuPattern,
                        params.customerProvided,
                        params.customerNamePattern,
                        params.customerPhonePattern)
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

        validateCustomerLink(transfer.getCustomerId());
        resolveLinkedOrder(transfer, transferType);
        validateSufficientStockForItems(sourceWarehouse, normalizedItems,
                reservedForOrder(transfer.getOrderId()));


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

        StockTransfer saved = recordCarrierInDirectory(stockTransferRepository.save(transfer));
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

    /**
     * Files the carrier into the driver and vehicle directories so the next transfer can
     * offer them back instead of making the operator retype name, TC, phone and plate. The
     * directory links let duplicates be merged later without touching this transfer's own
     * record of who drove.
     *
     * <p>Both {@code recordUsage} calls ignore blanks, so a depot exit with no carrier yet
     * passes through untouched and gets filed the moment {@link #assignCarrier} runs.</p>
     */
    private StockTransfer recordCarrierInDirectory(StockTransfer saved) {
        com.warehouse.entity.Driver directoryEntry = driverService.recordUsage(
                saved.getDriverName(), saved.getDriverTcId(),
                saved.getDriverPhone(), saved.getVehiclePlate());
        com.warehouse.entity.Vehicle vehicleEntry = vehicleService.recordUsage(saved.getVehiclePlate());
        boolean linksChanged = false;
        if (directoryEntry != null && saved.getDriverId() == null) {
            saved.setDriverId(directoryEntry.getId());
            linksChanged = true;
        }
        if (vehicleEntry != null && saved.getVehicleId() == null) {
            saved.setVehicleId(vehicleEntry.getId());
            linksChanged = true;
        }
        if (linksChanged) {
            saved = stockTransferRepository.save(saved);
        }
        // Driving a vehicle is what assigns it: the pairing the operator actually used becomes
        // the pairing the transfer form offers next time.
        if (directoryEntry != null && vehicleEntry != null) {
            vehicleService.linkQuietly(directoryEntry.getId(), vehicleEntry.getId());
        }
        return saved;
    }

    @Override
    public StockTransfer createServiceHandover(ServiceHandoverRequest request) {
        // Admin-only at the controller as well; repeated here because this is the one path
        // that may write a shipment with no carrier, and it should not become reachable by
        // accident from anywhere else in the service layer.
        if (!isCurrentUserAdmin()) {
            throw new WarehouseManagementException(ErrorCode.UNAUTHORIZED_ACTION,
                    "Depo çıkış makbuzu yalnızca yönetici tarafından düzenlenebilir.");
        }

        LocalDateTime handedOverAt = request.getHandedOverAt() != null
                ? request.getHandedOverAt()
                : LocalDateTime.now();
        if (handedOverAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Teslim tarihi ileri bir tarih olamaz.");
        }

        StockTransfer transfer = new StockTransfer();
        Warehouse source = new Warehouse();
        source.setId(request.getSourceWarehouseId());
        transfer.setSourceWarehouse(source);
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCarrierPending(true);

        transfer.setHandoverToName(TurkishText.toTitleCase(request.getHandoverToName()));
        transfer.setHandoverToPhone(trimToNull(request.getHandoverToPhone()));
        transfer.setHandedOverBy(TurkishText.toTitleCase(request.getHandedOverBy()));

        transfer.setCustomerFullName(TurkishText.toTitleCase(request.getCustomerFullName()));
        transfer.setCustomerPhone(request.getCustomerPhone().trim());
        transfer.setCustomerAddress(request.getCustomerAddress().trim());
        transfer.setOrderId(request.getOrderId());
        transfer.setCustomerId(request.getCustomerId());
        transfer.setNotes(trimToNull(request.getNotes()));
        transfer.setTransferDate(handedOverAt);

        List<StockTransferItem> items = new ArrayList<>();
        for (ServiceHandoverRequest.Item line : request.getItems()) {
            StockTransferItem item = new StockTransferItem();
            item.setStockId(line.getStockId());
            Product product = new Product();
            product.setId(line.getProductId());
            item.setProduct(product);
            item.setQuantity(line.getQuantity());
            item.setTransfer(transfer);
            items.add(item);
        }
        transfer.setItems(items);

        // Created and completed in one go. The goods are physically out of the building the
        // moment the paper is signed, so leaving the shipment PENDING or IN_TRANSIT would put
        // the warehouse count above what is on the shelves. completeTransfer runs the same
        // deduction and audit path as any other shipment — there is no second way for stock to
        // leave, and therefore no way for these goods to be deducted twice when the carrier is
        // filled in later.
        StockTransfer created = createTransfer(transfer);
        return completeTransfer(created.getId(),
                "Servise teslim edildi — taşıyıcı sonradan belirlenecek");
    }

    @Override
    public StockTransfer assignCarrier(Long transferId, CarrierAssignmentRequest request) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        if (!transfer.isCarrierPending()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu sevkiyatın taşıyıcı bilgisi zaten girilmiş.");
        }
        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "İptal edilmiş sevkiyata taşıyıcı atanamaz.");
        }

        transfer.setDriverName(request.getDriverName().trim());
        transfer.setDriverTcId(request.getDriverTcId().trim());
        transfer.setDriverPhone(request.getDriverPhone().trim());
        transfer.setVehiclePlate(request.getVehiclePlate().trim()
                .toUpperCase(java.util.Locale.forLanguageTag("tr-TR")));
        transfer.setCarrierPending(false);

        StockTransfer saved = recordCarrierInDirectory(stockTransferRepository.save(transfer));
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_UPDATE, DomainEntityType.StockTransfer.name(),
                saved.getId(), username,
                String.format("Taşıyıcı bilgisi girildi: %s / %s (#%d)",
                        saved.getDriverName(), saved.getVehiclePlate(), saved.getId()),
                buildTransferMetadata(saved));
        logger.info("Carrier assigned to transfer id: {}", saved.getId());

        return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
    }

    @Override
    public TransferReturnDto recordReturn(Long transferId, TransferReturnRequest request) {
        if (!isCurrentUserAdmin()) {
            throw new WarehouseManagementException(ErrorCode.UNAUTHORIZED_ACTION,
                    "İade kaydı yalnızca yönetici tarafından girilebilir.");
        }
        StockTransfer transfer = getTransferByIdOrThrow(transferId);

        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            // Nothing has left the warehouse yet on a PENDING or IN_TRANSIT shipment, so
            // there is nothing to bring back — cancelling it is the right move and already
            // releases whatever was reserved.
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Yalnızca tamamlanmış sevkiyatlar için iade kaydedilebilir. "
                            + "Henüz tamamlanmamış sevkiyatı iptal edin.");
        }
        if (transfer.getTransferType() != TransferType.CUSTOMER_DELIVERY) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Depolar arası transferin iadesi ters yönde bir transferdir; iade kaydı "
                            + "müşteri sevkiyatları içindir.");
        }
        if (transfer.getOrderId() != null) {
            // Completing an order-linked shipment marks the order DELIVERED. Undoing that
            // properly means deciding the order status and the refund, which is exactly what
            // the storefront return flow exists for — quietly restocking here would leave a
            // delivered order whose goods are back on our shelves.
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu sevkiyat " + transfer.getOrderNumber() + " siparişine bağlı. "
                            + "İadeyi e-ticaret iade akışından yürütün.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime returnedAt = request.getReturnedAt() != null ? request.getReturnedAt() : now;
        if (returnedAt.isAfter(now.plusMinutes(5))) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "İade tarihi ileri bir tarih olamaz.");
        }
        if (transfer.getCompletedDate() != null && returnedAt.isBefore(transfer.getCompletedDate())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "İade tarihi sevkiyatın çıkış tarihinden önce olamaz.");
        }

        List<StockTransferItem> shippedItems = transfer.getItems();
        if (shippedItems == null || shippedItems.isEmpty()) {
            // Transfers predating the multi-item model carry a single product on the header
            // and have no line rows to return against.
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Bu sevkiyatın kalem kaydı bulunmuyor; iade kaydedilemiyor.");
        }
        Map<Long, StockTransferItem> itemsById = shippedItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(StockTransferItem::getId, item -> item, (a, b) -> a));

        // The same line may arrive twice in one request; fold them together before checking
        // the limit, otherwise two halves could each pass and together exceed what went out.
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (TransferReturnRequest.Item line : request.getItems()) {
            if (line.getQuantity() == null || line.getQuantity() < 1) {
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "İade adedi en az 1 olmalıdır.");
            }
            requested.merge(line.getTransferItemId(), line.getQuantity(), Integer::sum);
        }

        List<StockTransferItem> affected = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            StockTransferItem item = itemsById.get(entry.getKey());
            if (item == null) {
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "İade edilmek istenen kalem bu sevkiyata ait değil.");
            }
            int alreadyReturned = item.getReturnedQuantity() == null ? 0 : item.getReturnedQuantity();
            int remaining = item.getQuantity() - alreadyReturned;
            if (entry.getValue() > remaining) {
                String productName = item.getProduct() != null ? item.getProduct().getName() : "Ürün";
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        String.format("%s için iade edilebilecek en fazla adet: %d.",
                                productName, remaining));
            }
            affected.add(item);
        }

        Map<String, Stock> sourceStocks = loadSourceStocks(transfer.getSourceWarehouse(), affected);

        TransferReturn transferReturn = new TransferReturn();
        transferReturn.setTransfer(transfer);
        transferReturn.setReturnedAt(returnedAt);
        transferReturn.setReason(request.getReason());
        transferReturn.setNote(trimToNull(request.getNote()));
        transferReturn.setRecordedBy(CurrentUser.usernameOrSystem());

        int total = 0;
        for (StockTransferItem item : affected) {
            int quantity = requested.get(item.getId());
            Stock stock = sourceStocks.get(stockKey(item));

            // The goods are physically back on the shelf, so the count goes back up — even
            // when they came back damaged. Writing them off is a separate, deliberate stock
            // removal; skipping the restock here would leave goods on the floor that the system
            // says do not exist.
            stock.setQuantity(stock.getQuantity() + quantity);
            Stock saved = stockRepository.save(stock);
            logStockReturnForTransfer(saved, quantity, transfer, transferReturn);

            item.setReturnedQuantity(
                    (item.getReturnedQuantity() == null ? 0 : item.getReturnedQuantity()) + quantity);

            TransferReturnItem returnItem = new TransferReturnItem();
            returnItem.setTransferItem(item);
            returnItem.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
            returnItem.setQuantity(quantity);
            transferReturn.addItem(returnItem);
            total += quantity;
        }
        transferReturn.setTotalQuantity(total);

        transfer.setReturnedQuantity(
                (transfer.getReturnedQuantity() == null ? 0 : transfer.getReturnedQuantity()) + total);
        stockTransferRepository.save(transfer);
        TransferReturn savedReturn = transferReturnRepository.save(transferReturn);

        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_RETURN, DomainEntityType.StockTransfer.name(),
                transfer.getId(), username,
                String.format("Sevkiyat iadesi alındı: %d adet (%s) | %s | Sebep=%s",
                        total, describeReturnItems(transferReturn), describeRoute(transfer),
                        request.getReason()),
                buildTransferMetadata(transfer));
        notificationService.create(buildTransferNotification(
                "Sevkiyat iadesi alındı",
                String.format("#%d numaralı sevkiyattan %d adet ürün depoya geri alındı (%s).",
                        transfer.getId(), total, request.getReason()),
                transfer));
        logger.info("Recorded return of {} units for transfer id: {}", total, transfer.getId());

        return toReturnDto(savedReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferReturnDto> getReturns(Long transferId) {
        return transferReturnRepository.findByTransferId(transferId).stream()
                .map(this::toReturnDto)
                .toList();
    }

    private TransferReturnDto toReturnDto(TransferReturn source) {
        List<TransferReturnDto.Line> lines = new ArrayList<>();
        for (TransferReturnItem item : source.getItems()) {
            Product product = item.getTransferItem() != null ? item.getTransferItem().getProduct() : null;
            lines.add(TransferReturnDto.Line.builder()
                    .transferItemId(item.getTransferItem() != null ? item.getTransferItem().getId() : null)
                    .productId(item.getProductId())
                    .productName(product != null ? product.getName() : null)
                    .productSku(product != null ? product.getSku() : null)
                    .quantity(item.getQuantity())
                    .build());
        }
        return TransferReturnDto.builder()
                .id(source.getId())
                .transferId(source.getTransfer() != null ? source.getTransfer().getId() : null)
                .returnedAt(source.getReturnedAt())
                .reason(source.getReason())
                .note(source.getNote())
                .totalQuantity(source.getTotalQuantity())
                .recordedBy(source.getRecordedBy())
                .createdAt(source.getCreatedAt())
                .items(lines)
                .build();
    }

    private String describeReturnItems(TransferReturn source) {
        return source.getItems().stream()
                .map(item -> {
                    Product product = item.getTransferItem() != null
                            ? item.getTransferItem().getProduct() : null;
                    return (product != null ? product.getName() : "Ürün") + " x" + item.getQuantity();
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Mirrors {@code logStockRemovalForTransfer} so a shipment and its return read as one
     * pair in the movement history. A return that logged nothing would look like stock
     * appearing out of nowhere.
     */
    private void logStockReturnForTransfer(Stock stock, Integer quantity, StockTransfer transfer,
                                           TransferReturn transferReturn) {
        String username = CurrentUser.usernameOrSystem();
        Warehouse warehouse = stock.getWarehouse();
        Product product = stock.getProduct();

        AuditMetadata metadata = AuditMetadata.builder()
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productSku(product != null ? product.getSku() : null)
                .quantity(quantity)
                .customerName(transfer.getCustomerFullName())
                .customerPhone(transfer.getCustomerPhone())
                .transferId(transfer.getId())
                .build();

        String detailsMessage = String.format(
                "Sevkiyat iadesiyle stok artırıldı: +%s adet → Yeni=%s | Transfer #%d | "
                        + "Sebep=%s | Depo=%s, Ürün=%s",
                quantity, stock.getQuantity(), transfer.getId(), transferReturn.getReason(),
                warehouse != null ? warehouse.getName() : "N/A",
                product != null ? product.getName() : "N/A");

        auditService.log(AuditAction.STOCK_ADD, DomainEntityType.Stock.name(), stock.getId(),
                username, detailsMessage, metadata);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        Map<Long, Integer> orderReservations = reservedForOrder(transfer.getOrderId());
        for (StockTransferItem item : items) {
            Stock sourceStock = sourceStocks.get(stockKey(item));
            int fromOrder = reservationShare(orderReservations, sourceStock, item.getQuantity());
            validateSufficientAvailableStock(sourceStock, item.getQuantity(), fromOrder);
            // The order already holds `fromOrder` units on this row — only reserve the remainder,
            // otherwise the same units would be counted twice.
            int shortfall = item.getQuantity() - fromOrder;
            if (shortfall > 0) {
                reserveStockForTransfer(sourceStock, shortfall, transfer);
            }
        }

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        syncLinkedOrderStatus(transfer, com.warehouse.enums.OrderStatus.SHIPPED,
                "Kendi aracımızla sevkiyat yola çıktı — transfer #" + transfer.getId());
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

        Map<Long, Integer> completionReservations = reservedForOrder(transfer.getOrderId());
        if (transfer.getStatus() == TransferStatus.PENDING) {
            for (StockTransferItem item : items) {
                Stock sourceStock = sourceStocks.get(stockKey(item));
                // A never-started transfer holds no reservation of its own, but the linked
                // order does — consume that part from the reservation, the rest directly.
                int fromOrder = reservationShare(completionReservations, sourceStock, item.getQuantity());
                if (fromOrder > 0) deductReservedStock(sourceStock, fromOrder, transfer);
                int direct = item.getQuantity() - fromOrder;
                if (direct > 0) deductStockDirectly(sourceStock, direct, transfer);
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
        syncLinkedOrderStatus(transfer, com.warehouse.enums.OrderStatus.DELIVERED,
                "Kendi aracımızla teslim edildi — transfer #" + transfer.getId());
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
            Map<Long, Integer> orderReservations = reservedForOrder(transfer.getOrderId());
            for (StockTransferItem item : items) {
                Stock sourceStock = sourceStocks.get(stockKey(item));
                // Release only what this transfer reserved on start; the linked order keeps its own.
                int keptForOrder = reservationShare(orderReservations, sourceStock, item.getQuantity());
                int release = item.getQuantity() - keptForOrder;
                if (release > 0) releaseReservedStock(sourceStock, release);
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

        // We allow deleting transfers in any status (in transit, completed, pending)
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
                
                // We allow deleting transfers in any status (in transit, completed, pending)
                AuditMetadata metadata = buildTransferMetadata(transfer);
                stockTransferRepository.delete(transfer);
                auditService.log(AuditAction.TRANSFER_DELETE, DomainEntityType.StockTransfer.name(), transferId, username,
                        String.format("Transfer silindi: %s | Ürünler=%s", describeRoute(transfer), describeItems(transfer)), metadata);
                successCount++;
                logger.debug("Transfer deleted successfully with id: {}", transferId);
            } catch (WarehouseManagementException e) {
                // Catch domain exceptions
                StockTransfer transfer = null;
                try {
                    transfer = getTransferByIdOrThrow(transferId);
                } catch (Exception ex) {
                    // Transfer not found
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
                    null, // No SKU for a transfer
                    e.getErrorCode().getCode(),
                    e.getMessage()
                ));
                logger.warn("Cannot delete transfer with id {}: {}", transferId, e.getMessage());
            } catch (Exception e) {
                // Other errors
                StockTransfer transfer = null;
                try {
                    transfer = getTransferByIdOrThrow(transferId);
                } catch (Exception ex) {
                    // Transfer not found
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

        // Don't require a security code for normal approvals
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

        // The carrier columns are nullable so a depot exit can be recorded before the driver
        // is known, but that is the only shipment allowed to leave them empty. Checked here
        // rather than only on the request DTO so the rule holds for every caller — a blank
        // carrier must not be able to reach the database through some other entry point.
        if (transfer.isCarrierPending()) {
            if (transferType != TransferType.CUSTOMER_DELIVERY) {
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "Taşıyıcısı belirlenmemiş çıkış yalnızca müşteri sevkiyatı olabilir.");
            }
            ValidationUtil.requireNotBlank(transfer.getHandoverToName(), "Handover recipient");
            ValidationUtil.requireNotBlank(transfer.getHandedOverBy(), "Handed over by");
        } else {
            ValidationUtil.requireNotBlank(transfer.getDriverName(), "Driver name");
            ValidationUtil.requireNotBlank(transfer.getDriverTcId(), "Driver TC ID");
            ValidationUtil.requireNotBlank(transfer.getDriverPhone(), "Driver phone");
            ValidationUtil.requireNotBlank(transfer.getVehiclePlate(), "Vehicle plate");
        }
        return transferType;
    }

    /**
     * A customer delivery may be tied to an order (the customer chose "we deliver it
     * ourselves" instead of a cargo provider). Denormalises the order number so listings
     * can show it without joining.
     */
    private void resolveLinkedOrder(StockTransfer transfer, TransferType transferType) {
        if (transfer.getOrderId() == null) {
            transfer.setOrderNumber(null);
            return;
        }
        if (transferType != TransferType.CUSTOMER_DELIVERY) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Sipariş bağlantısı yalnızca müşteri sevkiyatı transferlerinde kullanılabilir");
        }
        com.warehouse.entity.Order order = orderRepository.findById(transfer.getOrderId())
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                        "Sevkiyata bağlanacak sipariş bulunamadı: " + transfer.getOrderId()));
        transfer.setOrderNumber(order.getOrderNumber());
        // The order always carries a customer record, so the delivery inherits the match for free.
        if (transfer.getCustomerId() == null && order.getCustomer() != null) {
            transfer.setCustomerId(order.getCustomer().getId());
        }
    }

    /** Rejects a customer id that does not exist so the FK never fails deep inside a flush. */
    private void validateCustomerLink(Long customerId) {
        if (customerId == null) return;
        if (!customerRepository.existsById(customerId)) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Eşleştirilecek müşteri bulunamadı: " + customerId);
        }
    }

    @Override
    public StockTransfer linkCustomer(Long transferId, Long customerId) {
        StockTransfer transfer = getTransferByIdOrThrow(transferId);
        if (transfer.getTransferType() != TransferType.CUSTOMER_DELIVERY) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Yalnızca müşteri sevkiyatları bir müşteri kaydıyla eşleştirilebilir");
        }
        validateCustomerLink(customerId);
        transfer.setCustomerId(customerId);
        StockTransfer saved = stockTransferRepository.save(transfer);
        String username = CurrentUser.usernameOrSystem();
        auditService.log(AuditAction.TRANSFER_UPDATE, DomainEntityType.StockTransfer.name(), saved.getId(), username,
                customerId == null
                        ? "Sevkiyatın müşteri eşleştirmesi kaldırıldı"
                        : "Sevkiyat, e-ticaret müşterisi #" + customerId + " ile eşleştirildi",
                buildTransferMetadata(saved));
        return stockTransferRepository.findByIdWithRelations(saved.getId()).orElse(saved);
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
        validateSufficientAvailableStock(stock, quantity, 0);
    }

    private void validateSufficientAvailableStock(Stock stock, Integer quantity, int alreadyReservedForThisJob) {
        int available = stock.getAvailableQuantity() + alreadyReservedForThisJob;
        if (available < quantity) {
            logger.warn("Insufficient available stock. Available: {}, Requested: {}", available, quantity);
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
        // Blank is skipped here, not just null. The entity used to reject an empty driver name
        // with @NotBlank; that annotation had to go so depot exits could be recorded before the
        // carrier is known, which would otherwise have left this method able to erase a known
        // driver by sending "".
        if (trimToNull(updatedTransfer.getDriverName()) != null) {
            transfer.setDriverName(updatedTransfer.getDriverName().trim());
        }
        if (trimToNull(updatedTransfer.getDriverTcId()) != null) {
            transfer.setDriverTcId(updatedTransfer.getDriverTcId().trim());
        }
        if (trimToNull(updatedTransfer.getDriverPhone()) != null) {
            transfer.setDriverPhone(updatedTransfer.getDriverPhone().trim());
        }
        if (trimToNull(updatedTransfer.getVehiclePlate()) != null) {
            transfer.setVehiclePlate(updatedTransfer.getVehiclePlate().trim());
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

    private void validateSufficientStockForItems(Warehouse warehouse, List<StockTransferItem> items,
                                                 Map<Long, Integer> orderReservations) {
        Map<String, Stock> stocks = loadSourceStocks(warehouse, items);
        for (StockTransferItem item : items) {
            Stock stock = stocks.get(stockKey(item));
            validateSufficientAvailableStock(stock, item.getQuantity(),
                    reservationShare(orderReservations, stock, item.getQuantity()));
        }
    }

    /**
     * Quantities already reserved on each stock row <em>by this very order</em>. Placing a
     * manual order reserves its lines, so shipping that order with our own vehicle must be
     * allowed to consume exactly that reservation instead of tripping the availability check.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findRecentCustomers(String query) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : stockTransferRepository.findDistinctCustomers(
                com.warehouse.util.TurkishText.searchPattern(query),
                org.springframework.data.domain.PageRequest.of(0, 15))) {
            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("name", row[0]);
            customer.put("phone", row[1]);
            customer.put("address", row[2]);
            customer.put("lastDeliveryAt", row[3]);
            customer.put("deliveryCount", ((Number) row[4]).longValue());
            result.add(customer);
        }
        return result;
    }

    /**
     * Walks the linked order to {@code target} through the order state machine. Stock was
     * already moved by the transfer itself, so the order's own DELIVERED bookkeeping must
     * not run again — this only advances status and writes history.
     */
    private void syncLinkedOrderStatus(StockTransfer transfer, com.warehouse.enums.OrderStatus target, String note) {
        if (transfer.getOrderId() == null) return;
        orderRepository.findById(transfer.getOrderId()).ifPresent(order -> {
            List<com.warehouse.enums.OrderStatus> path = statusPath(order.getStatus(), target);
            if (path.isEmpty()) {
                logger.info("Order {} stays in {} — no valid path to {}", order.getOrderNumber(), order.getStatus(), target);
                return;
            }
            String username = CurrentUser.usernameOrSystem();
            for (com.warehouse.enums.OrderStatus next : path) {
                com.warehouse.enums.OrderStatus previous = order.getStatus();
                order.setStatus(next);
                orderStatusHistoryRepository.save(com.warehouse.util.OrderStatusHistoryFactory.create(
                        order, previous, next, username, "STOCK_TRANSFER", note));
            }
            if (target == com.warehouse.enums.OrderStatus.DELIVERED) {
                order.setActualDeliveryDate(java.time.LocalDate.now());
            }
            orderRepository.save(order);
        });
    }

    /** Shortest sequence of valid transitions from {@code from} to {@code target}, empty if unreachable. */
    private List<com.warehouse.enums.OrderStatus> statusPath(com.warehouse.enums.OrderStatus from,
                                                             com.warehouse.enums.OrderStatus target) {
        if (from == null || target == null || from == target) return List.of();
        Map<com.warehouse.enums.OrderStatus, com.warehouse.enums.OrderStatus> cameFrom = new LinkedHashMap<>();
        java.util.Deque<com.warehouse.enums.OrderStatus> queue = new java.util.ArrayDeque<>();
        queue.add(from);
        cameFrom.put(from, null);
        while (!queue.isEmpty()) {
            com.warehouse.enums.OrderStatus current = queue.poll();
            if (current == target) break;
            for (com.warehouse.enums.OrderStatus next : com.warehouse.util.OrderStatusMachine.getAllowedTransitions(current)) {
                if (cameFrom.containsKey(next)) continue;
                cameFrom.put(next, current);
                queue.add(next);
            }
        }
        if (!cameFrom.containsKey(target)) return List.of();
        List<com.warehouse.enums.OrderStatus> path = new ArrayList<>();
        for (com.warehouse.enums.OrderStatus step = target; step != null && step != from; step = cameFrom.get(step)) {
            path.add(0, step);
        }
        return path;
    }

    /** How many of {@code quantity} on this stock row are already reserved by the linked order. */
    private int reservationShare(Map<Long, Integer> orderReservations, Stock stock, Integer quantity) {
        if (orderReservations.isEmpty() || stock == null || stock.getId() == null) return 0;
        return Math.min(quantity == null ? 0 : quantity, orderReservations.getOrDefault(stock.getId(), 0));
    }

    private Map<Long, Integer> reservedForOrder(Long orderId) {
        if (orderId == null) return Map.of();
        Map<Long, Integer> reserved = new LinkedHashMap<>();
        for (com.warehouse.entity.OrderItem line : orderItemRepository.findByOrderId(orderId)) {
            if (line.getStockId() == null || line.getQuantity() == null) continue;
            reserved.merge(line.getStockId(), line.getQuantity(), Integer::sum);
        }
        return reserved;
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
        String t = value.trim();
        if (t.isEmpty()) return null;

        // Strip surrounding quotes (including smart quotes) that the chat/tool layer might include.
        t = stripWrappingQuotes(t);

        // Collapse whitespace (e.g., double spaces) to make matching more robust.
        t = t.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    private static String stripWrappingQuotes(String s) {
        if (s == null) return null;
        String out = s;
        while (out.length() >= 2 && isQuote(out.charAt(0)) && isQuote(out.charAt(out.length() - 1))) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\''
                || c == '“' || c == '”'
                || c == '‘' || c == '’'
                || c == '`';
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
        private final LocalDateTime startDate;
        private final LocalDateTime endDate;
        private final boolean productNameProvided;
        private final boolean skuProvided;
        private final boolean driverNameProvided;
        private final boolean notesProvided;
        private final boolean customerProvided;
        private final String productNamePattern;
        private final String skuPattern;
        private final String driverPattern;
        private final String notesPattern;
        private final String customerNamePattern;
        private final String customerPhonePattern;

        private TransferFilterParams(StockTransferFilter filter) {
            if (filter == null) {
                this.status = null;
                this.transferType = null;
                this.sourceWarehouseId = null;
                this.destinationWarehouseId = null;
                this.startDate = null;
                this.endDate = null;
                this.productNameProvided = false;
                this.skuProvided = false;
                this.driverNameProvided = false;
                this.notesProvided = false;
                this.customerProvided = false;
                this.productNamePattern = "%";
                this.skuPattern = "%";
                this.driverPattern = "%";
                this.notesPattern = "%";
                this.customerNamePattern = "%";
                this.customerPhonePattern = "%";
            } else {
                this.status = filter.getStatus();
                this.transferType = filter.getTransferType();
                // Treat 0 / negative IDs as "no filter" to avoid accidental empty results from tool-calls.
                this.sourceWarehouseId = normalizeId(filter.getSourceWarehouseId());
                this.destinationWarehouseId = normalizeId(filter.getDestinationWarehouseId());
                this.startDate = filter.getStartDate();
                this.endDate = filter.getEndDate();
                String productName = normalize(filter.getProductName());
                String sku = normalize(filter.getSku());
                String driverName = normalize(filter.getDriverName());
                String notes = normalize(filter.getNotes());
                String customerQuery = normalize(filter.getCustomerQuery());
                this.productNameProvided = productName != null;
                this.skuProvided = sku != null;
                this.driverNameProvided = driverName != null;
                this.notesProvided = notes != null;
                this.customerProvided = customerQuery != null;
                this.productNamePattern = productNameProvided ? likePattern(productName) : "%";
                this.skuPattern = skuProvided ? likePattern(sku) : "%";
                this.driverPattern = driverNameProvided ? likePattern(driverName) : "%";
                this.notesPattern = notesProvided ? likePattern(notes) : "%";
                String digits = customerQuery != null ? customerQuery.replaceAll("\\D", "") : null;
                boolean digitsProvided = digits != null && digits.length() >= 3;
                this.customerNamePattern = customerProvided ? likePattern(customerQuery) : "%";
                this.customerPhonePattern = digitsProvided ? ("%" + digits + "%") : (customerProvided ? likePattern(customerQuery) : "%");
            }
        }

        private static TransferFilterParams from(StockTransferFilter filter) {
            return new TransferFilterParams(filter);
        }

        private static Long normalizeId(Long id) {
            if (id == null) return null;
            return id > 0 ? id : null;
        }

        /**
         * Patterns are built against the normalised {@code *_search} columns, not the raw text:
         * LOWER() alone leaves "ı" and "i" different, so a record typed "Balli" never matched a
         * search for "Ballı". Folding both sides onto ASCII makes the two spellings equal.
         */
        private static String likePattern(String value) {
            return "%" + com.warehouse.util.TurkishText.normalize(value) + "%";
        }
    }
}

