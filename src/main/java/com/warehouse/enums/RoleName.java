package com.warehouse.enums;

/**
 * Defines application role names aligned with Spring Security ROLE_ prefix usage.
 */
public enum RoleName {
    ADMIN,
    STANDARD;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}



