package com.warehouse.assistant.store.tools;

import com.warehouse.entity.Product;
import com.warehouse.service.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Returns a product's price and a simple installment breakdown (no interest,
 * hardcoded for the MVP). When a real payment gateway installment API is
 * wired, we'll swap the formula behind this tool — the contract stays stable.
 */
@Component
public class StorePriceInstallmentTool {

    private static final int[] INSTALLMENT_COUNTS = {2, 3, 6, 9, 12};

    private final ProductService productService;

    public StorePriceInstallmentTool(ProductService productService) {
        this.productService = productService;
    }

    public static class PriceInfo {
        public Long productId;
        public String productName;
        public BigDecimal price;
        public List<InstallmentPlan> installmentPlans;
    }

    public static class InstallmentPlan {
        public int count;
        public BigDecimal monthly;
        public BigDecimal total;
    }

    @Tool(description = "STORE: Bir ürünün fiyatını ve taksit seçeneklerini döndürür. 2/3/6/9/12 taksit faizsiz varsayılır.")
    public PriceInfo priceAndInstallments(
            @ToolParam(description = "Ürün id") Long productId) {
        if (productId == null) return null;
        Optional<Product> maybe = productService.getProductByIdWithRelations(productId);
        if (maybe.isEmpty()) return null;
        Product p = maybe.get();
        if (p.getPrice() == null) return null;

        PriceInfo info = new PriceInfo();
        info.productId = p.getId();
        info.productName = p.getName();
        info.price = p.getPrice();

        List<InstallmentPlan> plans = new ArrayList<>();
        for (int n : INSTALLMENT_COUNTS) {
            InstallmentPlan plan = new InstallmentPlan();
            plan.count = n;
            plan.monthly = p.getPrice().divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            plan.total = p.getPrice();
            plans.add(plan);
        }
        info.installmentPlans = plans;
        return info;
    }
}
