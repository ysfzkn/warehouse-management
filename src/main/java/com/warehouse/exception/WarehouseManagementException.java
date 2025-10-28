package com.warehouse.exception;

import lombok.Getter;

@Getter
public class WarehouseManagementException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public WarehouseManagementException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = null;
    }

    public WarehouseManagementException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage() + " - %s", formatArgs(args)));
        this.errorCode = errorCode;
        this.args = args;
    }

    public WarehouseManagementException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.args = null;
    }

    public WarehouseManagementException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    private static String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]);
            if (i < args.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}

