package com.warehouse.service;

import com.warehouse.dto.TransferReturnRequest;
import com.warehouse.entity.Category;
import com.warehouse.entity.Customer;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.entity.Product;
import com.warehouse.entity.ReturnRequest;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.CustomerStatus;
import com.warehouse.enums.OrderStatus;
import com.warehouse.enums.ReturnReason;
import com.warehouse.enums.ReturnStatus;
import com.warehouse.enums.TransferReturnOrderOutcome;
import com.warehouse.enums.TransferReturnReason;
import com.warehouse.enums.TransferType;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.OrderItemRepository;
import com.warehouse.repository.OrderRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.ReturnRequestRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Returning a shipment that fulfils an e-commerce order.
 *
 * <p>Two decisions hide in one return here, and they pull in opposite directions: the goods
 * always come back on the shelf, but whether they are still spoken for depends entirely on
 * whether the order survived. Completing the shipment ate the order's reservation; a failed
 * delivery has to give it back, and a returned order must not.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderLinkedTransferReturnTest {

    @Autowired private StockTransferService transferService;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ReturnRequestRepository returnRequestRepository;

    private static final int ON_HAND = 40;
    private static final int ORDERED = 3;

    private Warehouse warehouse;
    private Product product;
    private Stock stock;
    private Order order;
    private Customer customer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = new Category();
        category.setName("Sipariş İade Kategori");
        category.setSlug("siparis-iade-" + System.nanoTime());
        category = categoryRepository.save(category);

        product = new Product();
        product.setName("Buzdolabı Çift Kapılı");
        product.setSku("SIP-001");
        product.setSlug("sip-001-" + System.nanoTime());
        product.setCategory(category);
        product.setPrice(new BigDecimal("18999.00"));
        product.setVatRate(new BigDecimal("20.00"));
        product = productRepository.save(product);

        warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(ON_HAND);
        // Checkout reserved these units when the order was placed.
        stock.setReservedQuantity(ORDERED);
        stock = stockRepository.save(stock);

        customer = new Customer();
        customer.setEmail("iade" + System.nanoTime() + "@test.com");
        customer.setPasswordHash("$2a$10$hashedpassword");
        customer.setFirstName("Ayşe");
        customer.setLastName("Gültekin");
        customer.setPhone("+905001234567");
        customer.setActive(true);
        customer.setEmailVerified(true);
        customer.setKvkkConsent(true);
        customer.setKvkkConsentAt(LocalDateTime.now());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setFailedLoginCount(0);
        customer = customerRepository.save(customer);

        order = new Order();
        order.setOrderNumber("ORD-IADE-" + (System.nanoTime() % 100000));
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PREPARING);
        order.setSubtotal(new BigDecimal("56997.00"));
        order.setShippingCost(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setVatTotal(new BigDecimal("11399.40"));
        order.setSctTotal(BigDecimal.ZERO);
        order.setGrandTotal(new BigDecimal("68396.40"));
        order.setPaymentMethod("CREDIT_CARD");
        order.setInstallmentCount(1);
        order.setDistanceSalesContractAccepted(true);
        order.setShippingAddressSnapshot(Map.of("city", "Niğde", "addressLine", "Kale Mah."));
        order.setBillingAddressSnapshot(Map.of("city", "Niğde", "addressLine", "Kale Mah."));
        order.setIpAddress("127.0.0.1");
        order = orderRepository.save(order);

        OrderItem line = new OrderItem();
        line.setOrder(order);
        line.setProduct(product);
        line.setQuantity(ORDERED);
        line.setUnitPrice(product.getPrice());
        line.setVatRate(product.getVatRate());
        line.setSctRate(BigDecimal.ZERO);
        line.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(ORDERED)));
        line.setProductSnapshot(Map.of("name", product.getName(), "sku", product.getSku()));
        line.setWarehouseId(warehouse.getId());
        line.setStockId(stock.getId());
        orderItemRepository.save(line);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Ships the order with our own vehicle, all the way to DELIVERED. */
    private StockTransfer deliver() {
        StockTransfer transfer = new StockTransfer();
        transfer.setSourceWarehouse(warehouse);
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCustomerFullName("Ayşe Gültekin");
        transfer.setCustomerPhone("05001234567");
        transfer.setCustomerAddress("Kale Mah. Paşakapı Cad. No: 28 Niğde");
        transfer.setDriverName("Ahmet Yılmaz");
        transfer.setDriverTcId("12345678901");
        transfer.setDriverPhone("05551234567");
        transfer.setVehiclePlate("51 ATS 303");
        transfer.setOrderId(order.getId());
        transfer.setTransferDate(LocalDateTime.now());

        StockTransferItem item = new StockTransferItem();
        item.setProduct(product);
        item.setStockId(stock.getId());
        item.setQuantity(ORDERED);
        transfer.addItem(item);

        StockTransfer created = transferService.createTransfer(transfer);
        transferService.startTransfer(created.getId());
        return transferService.completeTransfer(created.getId(), null);
    }

    private TransferReturnRequest returnAll(StockTransfer shipped, TransferReturnOrderOutcome outcome) {
        TransferReturnRequest request = new TransferReturnRequest();
        request.setReason(TransferReturnReason.UNDELIVERED);
        request.setOrderOutcome(outcome);
        TransferReturnRequest.Item line = new TransferReturnRequest.Item();
        line.setTransferItemId(shipped.getItems().get(0).getId());
        line.setQuantity(ORDERED);
        request.setItems(List.of(line));
        return request;
    }

    private Stock reload() {
        return stockRepository.findById(stock.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Sevkiyat tamamlanınca sipariş teslim edilmiş sayılır ve rezervasyon tükenir")
    void deliveringConsumesTheReservation() {
        deliver();

        assertThat(reload().getQuantity()).isEqualTo(ON_HAND - ORDERED);
        assertThat(reload().getReservedQuantity()).as("rezervasyon sevkiyatla tüketildi").isZero();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Sipariş açık kalırsa dönen adet yeniden o siparişe rezerve edilir")
    void keepingTheOrderReReservesTheGoods() {
        StockTransfer shipped = deliver();

        transferService.recordReturn(shipped.getId(),
                returnAll(shipped, TransferReturnOrderOutcome.KEEP_ORDER));

        assertThat(reload().getQuantity()).isEqualTo(ON_HAND);
        // The order is still owed these units. Leaving them unreserved would let the next
        // customer buy goods that are already promised to this one.
        assertThat(reload().getReservedQuantity()).isEqualTo(ORDERED);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .as("teslimat denemesi tutmadı ama sipariş yaşıyor")
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Sipariş iade edilirse mal serbest döner ve sipariş RETURNED olur")
    void returningTheOrderFreesTheGoods() {
        StockTransfer shipped = deliver();

        transferService.recordReturn(shipped.getId(),
                returnAll(shipped, TransferReturnOrderOutcome.RETURN_ORDER));

        assertThat(reload().getQuantity()).isEqualTo(ON_HAND);
        assertThat(reload().getReservedQuantity())
                .as("kimse beklemiyor, mal serbest").isZero();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    @DisplayName("Siparişe bağlı sevkiyatta sipariş kararı verilmeden iade kaydedilemez")
    void orderLinkedReturnDemandsADecision() {
        StockTransfer shipped = deliver();
        TransferReturnRequest request = returnAll(shipped, null);

        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(), request))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("Siparişin ne olacağını");

        assertThat(reload().getQuantity()).isEqualTo(ON_HAND - ORDERED);
    }

    @Test
    @DisplayName("Kısmi iadede sipariş iade edildi olarak işaretlenemez")
    void partialReturnCannotCloseTheOrder() {
        StockTransfer shipped = deliver();

        TransferReturnRequest request = new TransferReturnRequest();
        request.setReason(TransferReturnReason.REFUSED);
        request.setOrderOutcome(TransferReturnOrderOutcome.RETURN_ORDER);
        TransferReturnRequest.Item line = new TransferReturnRequest.Item();
        line.setTransferItemId(shipped.getItems().get(0).getId());
        line.setQuantity(1);
        request.setItems(List.of(line));

        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(), request))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("tamamı geri gelmedi");

        // Nothing moved: the whole return is one transaction.
        assertThat(reload().getQuantity()).isEqualTo(ON_HAND - ORDERED);
        assertThat(transferService.getReturns(shipped.getId())).isEmpty();
    }

    @Test
    @DisplayName("E-ticaret iadesi zaten stoğa işlenmişse aynı mal ikinci kez iade edilemez")
    void aSettledStorefrontReturnBlocksTheShipmentReturn() {
        StockTransfer shipped = deliver();

        // ReturnRequestServiceImpl.markReceived restocks on its own. Recording a shipment
        // return on top of that would add the same units twice, and both writes look entirely
        // ordinary in isolation.
        ReturnRequest storefrontReturn = new ReturnRequest();
        storefrontReturn.setReturnNumber("RET-" + (System.nanoTime() % 100000));
        storefrontReturn.setOrder(order);
        storefrontReturn.setCustomer(customer);
        storefrontReturn.setStatus(ReturnStatus.RECEIVED);
        storefrontReturn.setReason(ReturnReason.CHANGED_MIND);
        storefrontReturn.setCreatedAt(LocalDateTime.now());
        returnRequestRepository.save(storefrontReturn);

        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(),
                returnAll(shipped, TransferReturnOrderOutcome.RETURN_ORDER)))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("ikinci kez");

        assertThat(reload().getQuantity()).isEqualTo(ON_HAND - ORDERED);
    }

    @Test
    @DisplayName("Siparişe bağlı olmayan sevkiyatta sipariş kararı reddedilir")
    void outcomeIsRejectedWhenThereIsNoOrder() {
        StockTransfer shipped = deliver();
        StockTransfer standalone = transferRepository.findById(shipped.getId()).orElseThrow();
        standalone.setOrderId(null);
        standalone.setOrderNumber(null);
        transferRepository.save(standalone);

        assertThatThrownBy(() -> transferService.recordReturn(shipped.getId(),
                returnAll(shipped, TransferReturnOrderOutcome.KEEP_ORDER)))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("sipariş kararı verilemez");
    }
}
