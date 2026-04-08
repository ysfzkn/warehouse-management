package com.warehouse.service.impl;

import com.warehouse.dto.StockRequestDto;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockRequest;
import com.warehouse.entity.Product;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.StockRequestStatus;
import com.warehouse.enums.StockRequestType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockRequestRepository;
import com.warehouse.service.StockRequestService;
import com.warehouse.service.StockService;
import com.warehouse.service.NotificationService;
import com.warehouse.constants.NotificationMessages;
import com.warehouse.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of StockRequestService
 */
@Service
@RequiredArgsConstructor
public class StockRequestServiceImpl implements StockRequestService {

    private static final Logger logger = LoggerFactory.getLogger(StockRequestServiceImpl.class);

    private final StockRequestRepository stockRequestRepository;
    private final StockService stockService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public StockRequest createRequest(Long stockId,
                                      StockRequestType type,
                                      Integer quantity,
                                      String notes,
                                      Long productId,
                                      Long warehouseId,
                                      String customerName,
                                      String customerPhone) {
        logger.info("Creating stock request: stockId={}, type={}, quantity={}, productId={}, warehouseId={}",
                stockId, type, quantity, productId, warehouseId);

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Stock stock;

        if (stockId != null) {
            stock = stockService.getStockByIdOrThrow(stockId);
        } else {
            if (productId == null || warehouseId == null) {
                throw new IllegalArgumentException("Stock ID or (productId & warehouseId) must be provided");
            }

            stock = stockService.getStockByProductAndWarehouse(productId, warehouseId)
                    .orElseGet(() -> {
                        logger.info("No stock found for product {} and warehouse {}, creating placeholder stock with quantity 0",
                                productId, warehouseId);
                        Stock newStock = new Stock();
                        Product product = new Product();
                        product.setId(productId);
                        Warehouse warehouse = new Warehouse();
                        warehouse.setId(warehouseId);
                        newStock.setProduct(product);
                        newStock.setWarehouse(warehouse);
                        newStock.setQuantity(0);
                        newStock.setReservedQuantity(0);
                        newStock.setConsignedQuantity(0);
                        newStock.setCustomerName(customerName);
                        newStock.setCustomerPhone(customerPhone);
                        return stockService.createStock(newStock);
                    });
        }
        String username = CurrentUser.usernameOrSystem();

        // Check if REMOVE request has sufficient stock
        if (type == StockRequestType.REMOVE) {
            int available = stock.getAvailableQuantity();
            if (available < quantity) {
                logger.warn("Insufficient stock for removal request. Available: {}, Requested: {}", available, quantity);
                throw new WarehouseManagementException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }

        StockRequest request = new StockRequest();
        request.setStock(stock);
        request.setType(type);
        request.setQuantity(quantity);
        request.setStatus(StockRequestStatus.PENDING);
        request.setRequestedBy(username);
        request.setRequestedAt(OffsetDateTime.now());
        request.setNotes(notes);

        StockRequest saved = stockRequestRepository.save(request);
        logger.info("Stock request created successfully with id: {}", saved.getId());

        // Create notification for admin
        String notificationMessage = String.format(
            "%s kullanıcısı %s için %s talebi oluşturdu. Miktar: %d adet. Depo: %s",
            username,
            stock.getProduct().getName(),
            type == StockRequestType.ADD ? "stok ekleme" : "stok çıkarma",
            quantity,
            stock.getWarehouse().getName()
        );
        
        notificationService.create(
            NotificationMessages.STOCK_REQUEST_CREATED_TITLE,
            notificationMessage,
            "StockRequest",
            saved.getId()
        );
        
        logger.info("Notification created for stock request: {}", saved.getId());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRequestDto> getAllRequests() {
        logger.debug("Fetching all stock requests");
        return stockRequestRepository.findAllByOrderByRequestedAtDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockRequestDto> getRequestsByStatus(StockRequestStatus status) {
        logger.debug("Fetching stock requests by status: {}", status);
        return stockRequestRepository.findRequestsWithDetailsByStatus(status)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockRequestDto> getRequestsForCurrentUser(StockRequestStatus status) {
        String username = CurrentUser.usernameOrSystem();
        logger.debug("Fetching stock requests for user: {} status: {}", username, status);
        List<StockRequest> requests = status != null
                ? stockRequestRepository.findAllDetailsByRequestedByAndStatus(username, status)
                : stockRequestRepository.findAllDetailsByRequestedBy(username);
        return requests.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public long getPendingRequestsCount() {
        return stockRequestRepository.countByStatus(StockRequestStatus.PENDING);
    }

    @Override
    @Transactional
    public void approveRequest(Long requestId) {
        logger.info("Approving stock request: {}", requestId);

        StockRequest request = getRequestById(requestId);

        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be approved");
        }

        String reviewer = CurrentUser.usernameOrSystem();

        // Execute the stock operation (pass the user's note for audit/notification trail)
        Stock stock = request.getStock();
        if (request.getType() == StockRequestType.ADD) {
            stockService.addToStock(stock.getId(), request.getQuantity(), request.getNotes());
        } else {
            stockService.removeFromStock(stock.getId(), request.getQuantity(), request.getNotes());
        }

        // Update request status
        request.setStatus(StockRequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(OffsetDateTime.now());
        stockRequestRepository.save(request);

        // Create notification for requester
        String notificationMessage = String.format(
            "Stok talebi onaylandı: %s - %s (%d adet). Yönetici: %s",
            request.getStock().getProduct().getName(),
            request.getType() == StockRequestType.ADD ? "Ekleme" : "Çıkarma",
            request.getQuantity(),
            reviewer
        );
        
        notificationService.create(
            NotificationMessages.STOCK_REQUEST_APPROVED_TITLE,
            notificationMessage,
            "StockRequest",
            requestId
        );

        logger.info("Stock request approved successfully: {}", requestId);
    }

    @Override
    @Transactional
    public void rejectRequest(Long requestId, String rejectionReason) {
        logger.info("Rejecting stock request: {}", requestId);

        StockRequest request = getRequestById(requestId);

        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected");
        }

        String reviewer = CurrentUser.usernameOrSystem();

        request.setStatus(StockRequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(OffsetDateTime.now());
        request.setRejectionReason(rejectionReason);
        stockRequestRepository.save(request);

        // Create notification for requester
        String notificationMessage = String.format(
            "Stok talebi reddedildi: %s - %s (%d adet). Yönetici: %s%s",
            request.getStock().getProduct().getName(),
            request.getType() == StockRequestType.ADD ? "Ekleme" : "Çıkarma",
            request.getQuantity(),
            reviewer,
            rejectionReason != null && !rejectionReason.isEmpty() ? ". Neden: " + rejectionReason : ""
        );
        
        notificationService.create(
            NotificationMessages.STOCK_REQUEST_REJECTED_TITLE,
            notificationMessage,
            "StockRequest",
            requestId
        );

        logger.info("Stock request rejected successfully: {}", requestId);
    }

    @Override
    public StockRequest getRequestById(Long id) {
        return stockRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stock request not found: " + id));
    }

    @Override
    public StockRequestDto toDto(StockRequest request) {
        StockRequestDto dto = new StockRequestDto();
        dto.setId(request.getId());
        dto.setStockId(request.getStock().getId());
        dto.setProductName(request.getStock().getProduct().getName());
        dto.setProductSku(request.getStock().getProduct().getSku());
        dto.setWarehouseName(request.getStock().getWarehouse().getName());
        dto.setType(request.getType());
        dto.setQuantity(request.getQuantity());
        dto.setStatus(request.getStatus());
        dto.setRequestedBy(request.getRequestedBy());
        dto.setRequestedAt(request.getRequestedAt());
        dto.setReviewedBy(request.getReviewedBy());
        dto.setReviewedAt(request.getReviewedAt());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setNotes(request.getNotes());
        dto.setCurrentStockQuantity(request.getStock().getQuantity());
        dto.setAvailableQuantity(request.getStock().getAvailableQuantity());
        return dto;
    }

    @Override
    @Transactional
    public void deleteOwnPendingRequest(Long requestId) {
        StockRequest request = getRequestById(requestId);
        String username = CurrentUser.usernameOrSystem();
        String role = CurrentUser.getRole();
        boolean isAdmin = role != null && role.equalsIgnoreCase("ADMIN");

        if (!isAdmin && (request.getRequestedBy() == null || !request.getRequestedBy().equalsIgnoreCase(username))) {
            throw new WarehouseManagementException(ErrorCode.UNAUTHORIZED_ACTION);
        }

        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new WarehouseManagementException(ErrorCode.ONLY_PENDING_REQUESTS_CAN_BE_DELETED);
        }

        stockRequestRepository.delete(request);
        logger.info("Stock request {} deleted by {}", requestId, username);
    }
}

