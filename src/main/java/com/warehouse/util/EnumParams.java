package com.warehouse.util;

import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Parses an enum filter coming from a query string.
 *
 * <p>These used to be wrapped in {@code try { ... } catch (Exception ignored) {}}, which turned a
 * typo into "no filter at all": the caller asked for one status and got the whole list back,
 * with nothing indicating the filter had been dropped. A bad value is a client error, so it is
 * reported as one.</p>
 */
public final class EnumParams {

    private EnumParams() {}

    /** Returns null for a blank value; throws a 400-style error for an unrecognised one. */
    public static <E extends Enum<E>> E parse(Class<E> type, String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    label + " değeri geçersiz: " + raw + ". Geçerli değerler: " + allowed);
        }
    }
}
