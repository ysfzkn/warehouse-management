package com.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PaymentProperties {

    private String provider = "IYZICO";
    private boolean sandbox = true;

    private IyzicoConfig iyzico = new IyzicoConfig();
    private BankTransferConfig bankTransfer = new BankTransferConfig();

    @Getter @Setter
    public static class IyzicoConfig {
        private String apiKey = "";
        private String secretKey = "";
        private String baseUrl = "https://sandbox-api.iyzipay.com";
        private String callbackUrl = "http://localhost:3000/store/odeme/callback";
        private int timeoutMinutes = 15;
    }

    @Getter @Setter
    public static class BankTransferConfig {
        private int deadlineHours = 48;
        private String bankName = "";
        private String iban = "";
        private String accountHolder = "";
    }
}
