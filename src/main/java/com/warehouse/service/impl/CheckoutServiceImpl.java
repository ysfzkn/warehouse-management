package com.warehouse.service.impl;

import com.warehouse.dto.store.*;
import com.warehouse.entity.*;
import com.warehouse.constants.ShippingConstants;
import com.warehouse.enums.OrderStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import com.warehouse.service.CartService;
import com.warehouse.service.CheckoutService;
import com.warehouse.entity.Stock;
import com.warehouse.repository.StockRepository;
import com.warehouse.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutServiceImpl.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final CartService cartService;
    private final CargoProviderRepository cargoProviderRepository;

    public CheckoutServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                                CustomerRepository customerRepository, CustomerAddressRepository addressRepository,
                                OrderRepository orderRepository, StockService stockService,
                                StockRepository stockRepository, CartService cartService,
                                CargoProviderRepository cargoProviderRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.cargoProviderRepository = cargoProviderRepository;
        this.stockService = stockService;
        this.stockRepository = stockRepository;
        this.cartService = cartService;
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutValidationResponse validateCheckout(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen giriş yaparak ürün ekleyin."));

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return CheckoutValidationResponse.builder().valid(false).errors(List.of("Sepetiniz boş. Lütfen ürün ekleyerek devam edin.")).build();
        }

        List<String> errors = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                errors.add(product.getName() + " artik satilamaz.");
                continue;
            }
            List<Stock> stocks = stockService.getStocksByProduct(product.getId());
            int available = stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();
            if (available < item.getQuantity()) {
                errors.add(product.getName() + " icin yeterli stok yok. Mevcut: " + available);
            }
            BigDecimal price = product.getSalePrice() != null ? product.getSalePrice() : product.getPrice();
            if (price != null) subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal shippingCost = ShippingConstants.calculateShippingCost(subtotal);
        BigDecimal total = subtotal.add(shippingCost);

        return CheckoutValidationResponse.builder()
            .valid(errors.isEmpty())
            .subtotal(subtotal)
            .shippingCost(shippingCost)
            .total(total)
            .errors(errors)
            .build();
    }

    @Override
    public PlaceOrderResponse placeOrder(Long customerId, PlaceOrderRequest request, String ipAddress, String userAgent) {
        if (!request.isDistanceSalesContractAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Mesafeli satis sozlesmesini onaylamaniz gerekiyor.");
        }

        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.CUSTOMER_NOT_FOUND));

        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen giriş yaparak ürün ekleyin."));

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepetiniz boş. Lütfen ürün ekleyerek devam edin.");
        }

        CustomerAddress shippingAddr = addressRepository.findById(request.getShippingAddressId())
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Teslimat adresi bulunamadı."));

        Long billingAddrId = request.getBillingAddressId() != null ? request.getBillingAddressId() : request.getShippingAddressId();
        CustomerAddress billingAddr = addressRepository.findById(billingAddrId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Fatura adresi bulunamadı."));

        // Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem ci : items) {
            Product p = ci.getProduct();
            BigDecimal price = p.getSalePrice() != null && p.getSalePrice().compareTo(BigDecimal.ZERO) > 0 ? p.getSalePrice() : p.getPrice();
            if (price == null) price = BigDecimal.ZERO;
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(ci.getQuantity()));
            BigDecimal vat = p.getVatRate() != null ? lineTotal.multiply(p.getVatRate()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

            subtotal = subtotal.add(lineTotal);
            vatTotal = vatTotal.add(vat);

            // Pessimistic lock stock reservation (SELECT FOR UPDATE)
            List<Stock> availableStocks = stockRepository.findAvailableByProductForUpdate(p.getId());
            int remaining = ci.getQuantity();
            Long reservedWarehouseId = null;
            Long reservedStockId = null;
            for (Stock stock : availableStocks) {
                int avail = stock.getAvailableQuantity();
                if (avail <= 0) continue;
                int toReserve = Math.min(remaining, avail);
                stock.setReservedQuantity(stock.getReservedQuantity() + toReserve);
                stockRepository.save(stock);
                remaining -= toReserve;
                reservedWarehouseId = stock.getWarehouse().getId();
                reservedStockId = stock.getId();
                if (remaining <= 0) break;
            }
            if (remaining > 0) {
                throw new WarehouseManagementException(ErrorCode.STOCK_RESERVATION_FAILED,
                    p.getName() + " icin yeterli stok bulunamadi.");
            }

            OrderItem oi = new OrderItem();
            oi.setProduct(p);
            oi.setProductSnapshot(Map.of("name", p.getName(), "sku", p.getSku(), "price", price.toString()));
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPrice(price);
            oi.setVatRate(p.getVatRate() != null ? p.getVatRate() : BigDecimal.ZERO);
            oi.setSctRate(p.getSctRate() != null ? p.getSctRate() : BigDecimal.ZERO);
            oi.setLineTotal(lineTotal);
            oi.setWarehouseId(reservedWarehouseId);
            oi.setStockId(reservedStockId);
            orderItems.add(oi);
        }

        // Calculate shipping cost — dynamic from CargoProvider or fallback to ShippingConstants
        BigDecimal shippingCost;
        BigDecimal shippingVat = BigDecimal.ZERO;
        CargoProvider cargoProvider = null;

        Long cargoProviderId = request.getCargoProviderId();
        if (cargoProviderId != null) {
            cargoProvider = cargoProviderRepository.findById(cargoProviderId).orElse(null);
        }

        if (cargoProvider != null) {
            // Calculate total desi from cart items
            BigDecimal totalDesi = BigDecimal.ZERO;
            for (var ci : items) {
                Product prod = ci.getProduct();
                if (prod != null) {
                    // Physical weight
                    BigDecimal weight = prod.getWeight() != null ? BigDecimal.valueOf(prod.getWeight()) : BigDecimal.ZERO;
                    // Volumetric weight: (L x W x H) / 3000
                    BigDecimal volumetric = BigDecimal.ZERO;
                    if (prod.getLengthCm() != null && prod.getWidthCm() != null && prod.getHeightCm() != null) {
                        volumetric = BigDecimal.valueOf(prod.getLengthCm())
                            .multiply(BigDecimal.valueOf(prod.getWidthCm()))
                            .multiply(BigDecimal.valueOf(prod.getHeightCm()))
                            .divide(new BigDecimal("3000"), 2, java.math.RoundingMode.HALF_UP);
                    }
                    BigDecimal desi = weight.max(volumetric);
                    totalDesi = totalDesi.add(desi.multiply(BigDecimal.valueOf(ci.getQuantity())));
                }
            }
            shippingCost = cargoProvider.calculateShippingCost(totalDesi, subtotal);
            shippingVat = cargoProvider.calculateVat(shippingCost);
        } else {
            shippingCost = ShippingConstants.calculateShippingCost(subtotal);
        }

        BigDecimal grandTotal = subtotal.add(shippingCost).add(shippingVat).add(vatTotal);

        // Generate order number
        String orderNumber = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setShippingAddressSnapshot(addressToMap(shippingAddr));
        order.setBillingAddressSnapshot(addressToMap(billingAddr));
        order.setSubtotal(subtotal);
        order.setShippingCost(shippingCost);
        order.setShippingVat(shippingVat);
        order.setVatTotal(vatTotal);
        order.setGrandTotal(grandTotal);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setSctTotal(BigDecimal.ZERO);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setCustomerNote(request.getCustomerNote());
        order.setIpAddress(ipAddress);
        order.setUserAgent(userAgent);
        order.setDistanceSalesContractAccepted(true);
        order.setDistanceSalesContractAcceptedAt(LocalDateTime.now());

        if (cargoProvider != null) {
            order.setCargoProviderId(cargoProvider.getId());
            order.setCargoProviderName(cargoProvider.getName());
            // Also set legacy enum for backward compatibility
            try { order.setCargoCompany(com.warehouse.enums.CargoCompany.valueOf(cargoProvider.getCode())); } catch (Exception ignored) {}
        } else if (request.getCargoCompany() != null) {
            try { order.setCargoCompany(com.warehouse.enums.CargoCompany.valueOf(request.getCargoCompany())); } catch (Exception ignored) {}
        }

        order = orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
        }
        order.setItems(orderItems);
        order = orderRepository.save(order);

        // NOTE: Cart is NOT cleared here — cleared after successful payment
        // This allows cart recovery if payment fails

        logger.info("Order created: {} for customer {}", orderNumber, customerId);

        return PlaceOrderResponse.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .grandTotal(order.getGrandTotal())
            .status(order.getStatus().name())
            .build();
    }

    private Map<String, Object> addressToMap(CustomerAddress addr) {
        Map<String, Object> map = new HashMap<>();
        map.put("firstName", addr.getFirstName());
        map.put("lastName", addr.getLastName());
        map.put("phone", addr.getPhone());
        map.put("city", addr.getCity());
        map.put("district", addr.getDistrict());
        map.put("addressLine", addr.getAddressLine());
        map.put("postalCode", addr.getPostalCode());
        return map;
    }
}
