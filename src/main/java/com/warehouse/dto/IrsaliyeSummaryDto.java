package com.warehouse.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A waybill and what it currently adds up to across the stock table.
 *
 * <p>Fed to the type-ahead on the stock entry screens. One irsaliye covers a whole delivery, so
 * the operator entering the tenth line of a sixty-line dispatch wants to see that the number they
 * are typing already carries fifty-nine — that is the difference between "same delivery" and
 * "typo that quietly started a second one".</p>
 */
public class IrsaliyeSummaryDto {

    /** As typed by whoever entered it; matching happens on the folded key, not on this. */
    public String irsaliyeNo;
    public LocalDate irsaliyeDate;
    /** Stock rows carrying this waybill — i.e. how many distinct products came in on it. */
    public long stockCount;
    /** Units across those rows. */
    public long totalQuantity;
    public LocalDateTime lastUpdated;

    public static IrsaliyeSummaryDto of(com.warehouse.repository.StockRepository.IrsaliyeSummary row) {
        IrsaliyeSummaryDto dto = new IrsaliyeSummaryDto();
        dto.irsaliyeNo = row.getIrsaliyeNo();
        dto.irsaliyeDate = row.getIrsaliyeDate();
        dto.stockCount = row.getStockCount();
        dto.totalQuantity = row.getTotalQuantity();
        dto.lastUpdated = row.getLastUpdated();
        return dto;
    }
}
