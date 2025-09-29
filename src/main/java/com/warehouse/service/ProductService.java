package com.warehouse.service;

import com.warehouse.entity.Product;
import com.warehouse.entity.Category;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getAllActiveProducts() {
        return productRepository.findAllActive();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Optional<Product> getProductByIdWithStocks(Long id) {
        return productRepository.findByIdWithStocks(id);
    }

    public Optional<Product> getProductBySku(String sku) {
        return productRepository.findBySku(sku);
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + categoryId));

        return productRepository.findByCategoryAndActive(category);
    }

    public List<Product> searchProductsByName(String name) {
        return productRepository.findByNameContainingIgnoreCaseAndActive(name);
    }

    public Product createProduct(Product product) {
        // Validate category exists
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new RuntimeException("Category is required");
        }

        Category category = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + product.getCategory().getId()));

        // Check if SKU already exists
        if (productRepository.existsBySku(product.getSku())) {
            throw new RuntimeException("Product with SKU '" + product.getSku() + "' already exists");
        }

        product.setCategory(category);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        // Validate category exists if being changed
        if (productDetails.getCategory() != null && productDetails.getCategory().getId() != null) {
            if (!product.getCategory().getId().equals(productDetails.getCategory().getId())) {
                Category category = categoryRepository.findById(productDetails.getCategory().getId())
                        .orElseThrow(() -> new RuntimeException("Category not found with id: " + productDetails.getCategory().getId()));
                product.setCategory(category);
            }
        }

        // Check if the new SKU conflicts with existing products
        if (!product.getSku().equals(productDetails.getSku()) &&
            productRepository.existsBySku(productDetails.getSku())) {
            throw new RuntimeException("Product with SKU '" + productDetails.getSku() + "' already exists");
        }

        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setSku(productDetails.getSku());
        product.setPrice(productDetails.getPrice());
        product.setWeight(productDetails.getWeight());
        product.setDimensions(productDetails.getDimensions());
        product.setActive(productDetails.isActive());

        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        if (!product.getStocks().isEmpty()) {
            throw new RuntimeException("Cannot delete product with existing stock");
        }

        productRepository.delete(product);
    }

    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        product.setIsActive(false);
        productRepository.save(product);
    }

    public void activateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        product.setIsActive(true);
        productRepository.save(product);
    }

    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }
}
