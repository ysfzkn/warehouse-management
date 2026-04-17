package com.warehouse.service.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendResult {

    private boolean success;
    private String providerName;
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;

    public static SmsSendResult success(String providerName, String messageId) {
        return SmsSendResult.builder()
                .success(true)
                .providerName(providerName)
                .providerMessageId(messageId)
                .build();
    }

    public static SmsSendResult failure(String providerName, String errorCode, String errorMessage) {
        return SmsSendResult.builder()
                .success(false)
                .providerName(providerName)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
