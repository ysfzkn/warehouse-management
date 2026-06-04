package com.warehouse.enums;

/**
 * Defines application role names aligned with Spring Security ROLE_ prefix usage.
 */
public enum RoleName {
    ADMIN,
    STOCK_IN,    // Can only add stock
    STOCK_OUT;   // Can only remove stock

    public String asAuthority() {
        return "ROLE_" + name();
    }
}



