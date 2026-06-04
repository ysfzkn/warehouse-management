package com.warehouse.service.payment.protocol;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for resolving BankPosProtocol implementations by protocol name.
 * Automatically discovers all @Component BankPosProtocol beans.
 */
@Component
public class BankPosProtocolFactory {

    private final Map<String, BankPosProtocol> protocols;

    public BankPosProtocolFactory(List<BankPosProtocol> protocolList) {
        this.protocols = protocolList.stream()
                .collect(Collectors.toMap(BankPosProtocol::getProtocolName, Function.identity()));
    }

    public BankPosProtocol getProtocol(String protocolName) {
        BankPosProtocol protocol = protocols.get(protocolName);
        if (protocol == null) {
            throw new IllegalArgumentException("Desteklenmeyen POS protokolu: " + protocolName);
        }
        return protocol;
    }

    public boolean isSupported(String protocolName) {
        return protocols.containsKey(protocolName);
    }
}
