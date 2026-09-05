package com.warehouse.service;

import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.StockTransferDto;
import com.warehouse.entity.StockTransfer;
import com.warehouse.mapper.StockTransferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Depot exit: records the shipment, takes the stock down and issues the receipt as one act.
 *
 * <p>The three steps belong to two services, which is exactly why this class exists. Left to
 * the controller they would run in separate transactions, and a receipt that failed to render
 * would leave the stock already deducted with no paper to show for it — the operator would be
 * looking at an error message while the goods had silently left the books. Here the whole
 * thing commits or none of it does.</p>
 */
@Service
public class ServiceHandoverService {

    private final StockTransferService transferService;
    private final DeliveryReceiptService receiptService;
    private final StockTransferMapper transferMapper;

    public ServiceHandoverService(StockTransferService transferService,
                                  DeliveryReceiptService receiptService,
                                  StockTransferMapper transferMapper) {
        this.transferService = transferService;
        this.receiptService = receiptService;
        this.transferMapper = transferMapper;
    }

    @Transactional
    public Result handOver(ServiceHandoverRequest request, String username) {
        StockTransfer transfer = transferService.createServiceHandover(request);
        DeliveryReceiptDto receipt = receiptService.issue(transfer.getId(), username);
        return new Result(transferMapper.toDto(transfer), receipt);
    }

    /** The shipment and its receipt, so the caller can print without a second round trip. */
    public record Result(StockTransferDto transfer, DeliveryReceiptDto receipt) {}
}
