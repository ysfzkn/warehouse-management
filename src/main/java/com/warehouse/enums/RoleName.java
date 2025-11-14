package com.warehouse.enums;

/**
 * Defines application role names aligned with Spring Security ROLE_ prefix usage.
 */
public enum RoleName {
    ADMIN,
    STOCK_IN,    // Sadece stok ekleyebilir
    STOCK_OUT;   // Sadece stok çıkarabilir

    public String asAuthority() {
        return "ROLE_" + name();
    }
}



