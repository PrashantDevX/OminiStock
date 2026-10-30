package com.warehouse.auth.entity;

/**
 * Role based access model used by the API Gateway.
 * ADMIN   - full access (catalog, stock, users)
 * MANAGER - manage products and stock
 * STAFF   - read catalog/stock and place orders
 */
public enum Role {
    ADMIN,
    MANAGER,
    STAFF
}
