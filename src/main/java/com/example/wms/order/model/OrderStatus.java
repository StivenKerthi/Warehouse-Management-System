package com.example.wms.order.model;

/**
 * All valid states in the order lifecycle state machine.
 *
 * <p>State transitions are enforced exclusively by {@code OrderStateMachine.java}.
 * Never assign a status directly on an Order entity outside of that class.
 *
 * <pre>
 * CREATED → AWAITING_APPROVAL → APPROVED → UNDER_DELIVERY → FULFILLED
 *                             ↘ DECLINED → (re-submittable, unlimited retries)
 * CREATED / AWAITING_APPROVAL / APPROVED / DECLINED → CANCELED
 * </pre>
 *
 * Terminal states: {@link #FULFILLED}, {@link #CANCELED}.
 */
public enum OrderStatus {

    /** Initial state — order is being drafted by the client. */
    CREATED,

    /** Client has submitted the order; awaiting warehouse manager approval. */
    AWAITING_APPROVAL,

    /** Manager approved the order; awaiting delivery scheduling. */
    APPROVED,

    /** Manager declined the order; client may update and re-submit. */
    DECLINED,

    /** Delivery has been scheduled; inventory decremented. */
    UNDER_DELIVERY,

    /** Order delivered and confirmed by the cron job. Terminal state. */
    FULFILLED,

    /** Order was cancelled by the client or manager. Terminal state. */
    CANCELED
}
