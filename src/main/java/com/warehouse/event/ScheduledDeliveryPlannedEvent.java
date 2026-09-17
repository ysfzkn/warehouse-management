package com.warehouse.event;

import org.springframework.context.ApplicationEvent;

/**
 * A delivery was planned for a future date, or an existing plan was moved.
 *
 * <p>Exists so a plan whose reminder is <em>already</em> due does not have to wait for
 * tomorrow's sweep. The reminder job runs once each morning; a shipment booked at noon for
 * the same afternoon, or for tomorrow, would miss both its "bugün teslim" and its "yarın
 * teslim" window and only ever surface as an overdue warning. The listener runs the same
 * per-shipment sweep the job runs, so there is one rule — "a stage that is due is sent" —
 * and no second code path that could drift from it.</p>
 *
 * <p>Delivered {@code AFTER_COMMIT}: the reminder reads the shipment back from the
 * database, and a mail about a row that never committed is worse than a late one.</p>
 */
public class ScheduledDeliveryPlannedEvent extends ApplicationEvent {

    private final Long transferId;

    public ScheduledDeliveryPlannedEvent(Object source, Long transferId) {
        super(source);
        this.transferId = transferId;
    }

    public Long getTransferId() {
        return transferId;
    }
}
