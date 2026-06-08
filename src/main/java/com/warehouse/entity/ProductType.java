package com.warehouse.entity;

/**
 * Distinguishes a normal sellable product from a bundle/set.
 *
 * <ul>
 *   <li>{@link #SIMPLE} – an ordinary product with its own stock.</li>
 *   <li>{@link #BUNDLE} – a "set" (e.g. "Ankastre Set") composed of several SIMPLE
 *       products via {@link BundleItem}. It has no stock of its own; availability is
 *       derived from its members and member stock is reserved on checkout.</li>
 * </ul>
 */
public enum ProductType {
    SIMPLE,
    BUNDLE
}
