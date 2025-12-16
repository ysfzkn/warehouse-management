package com.warehouse.cezeri.context;

public record CezeriContext(
        String username,
        String role,
        boolean allowMutations
) {}


