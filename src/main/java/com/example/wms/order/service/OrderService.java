package com.example.wms.order.service;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.common.util.OrderNumberGenerator;
import com.example.wms.inventory.model.InventoryItem;
import com.example.wms.inventory.repository.InventoryItemRepository;
import com.example.wms.order.dto.CreateOrderRequest;
import com.example.wms.order.dto.IdempotentResponse;
import com.example.wms.order.dto.OrderDto;
import com.example.wms.order.dto.OrderItemRequest;
import com.example.wms.order.dto.OrderSummaryDto;
import com.example.wms.order.dto.UpdateOrderRequest;
import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderItem;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.repository.OrderRepository;
import com.example.wms.order.statemachine.OrderStateMachine;
import com.example.wms.user.model.User;
import com.example.wms.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Core order service — handles the order lifecycle from the client's perspective.
 *
 * <h2>Implemented in this class (TASK-026)</h2>
 * <ul>
 *   <li>{@link #createOrder} — validates items, locks price snapshots, generates order number</li>
 *   <li>{@link #updateOrder} — replaces items when order is in CREATED or DECLINED status</li>
 * </ul>
 *
 * <h2>Manager operations (TASK-032)</h2>
 * <ul>
 *   <li>{@link #approveOrder} — AWAITING_APPROVAL → APPROVED</li>
 *   <li>{@link #declineOrder} — AWAITING_APPROVAL → DECLINED (persists decline reason)</li>
 *   <li>{@link #listManagerOrders} — paginated all-orders view (optional status filter)</li>
 *   <li>{@link #getManagerOrder} — fetch any order by ID (no ownership restriction)</li>
 * </ul>
 *
 * <h2>Status change rule</h2>
 * Status changes are <strong>never</strong> made directly in this class.
 * All transitions go through {@link OrderStateMachine} — the single source of truth
 * for valid transitions and audit log writes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderStateMachine orderStateMachine;
    private final IdempotencyStore idempotencyStore;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates a new order for the given client.
     *
     * <p>Steps:
     * <ol>
     *   <li>Loads the client entity (404 if not found).</li>
     *   <li>Resolves each requested inventory item (404 if any item is not found).</li>
     *   <li>Locks the {@code unitPriceSnapshot} from the inventory item's current price.
     *       Items without a price are snapshotted as {@code 0.00}.</li>
     *   <li>Generates a unique order number via {@link OrderNumberGenerator}.</li>
     *   <li>Persists the order (status is defaulted to {@code CREATED} by {@code @PrePersist}).</li>
     *   <li>Writes the initial {@code null → CREATED} audit log entry via
     *       {@link OrderStateMachine#initialize}.</li>
     * </ol>
     *
     * @param clientId the UUID of the authenticated client
     * @param request  the order creation payload (validated before reaching this method)
     * @return the persisted order as a full detail DTO
     * @throws EntityNotFoundException if the client or any requested inventory item does not exist
     */
    @Transactional
    public OrderDto createOrder(UUID clientId, CreateOrderRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + clientId));

        Order order = Order.builder()
                .orderNumber(orderNumberGenerator.next())
                .client(client)
                .build();

        buildAndAttachItems(request.items(), order);

        Order saved = orderRepository.save(order);

        // Write the initial null → CREATED audit log entry in the same transaction
        orderStateMachine.initialize(saved, client.getUsername());

        log.debug("Order '{}' created by client '{}'", saved.getOrderNumber(), client.getUsername());

        return OrderDto.from(saved);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Replaces the item list of an existing order.
     *
     * <p>Only allowed when the order is in {@code CREATED} or {@code DECLINED} status.
     * An update on any other status throws {@link BusinessException} (HTTP 422).
     *
     * <p>The update is a <strong>full replacement</strong>: the existing items are cleared
     * and the new items are inserted. Removed items are deleted by JPA's
     * {@code orphanRemoval = true} on the {@code orderItems} collection.
     *
     * <p>Ownership is enforced — only the owning client may update the order.
     * A non-existent order and an order owned by another client both return HTTP 404
     * to prevent existence enumeration.
     *
     * @param orderId  the UUID of the order to update
     * @param clientId the UUID of the authenticated client
     * @param request  the update payload (validated before reaching this method)
     * @return the updated order as a full detail DTO
     * @throws EntityNotFoundException if the order does not exist or does not belong to this client
     * @throws BusinessException       if the order is not in {@code CREATED} or {@code DECLINED} status
     * @throws EntityNotFoundException if any requested inventory item does not exist
     */
    @Transactional
    public OrderDto updateOrder(UUID orderId, UUID clientId, UpdateOrderRequest request) {
        Order order = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus != OrderStatus.CREATED && currentStatus != OrderStatus.DECLINED) {
            throw new BusinessException(
                    "ORDER_NOT_EDITABLE",
                    String.format("Order '%s' cannot be updated in status %s. " +
                                  "Updates are only allowed in CREATED or DECLINED status.",
                            order.getOrderNumber(), currentStatus));
        }

        // Full replacement: clear existing items (orphanRemoval deletes them from DB on flush)
        order.getOrderItems().clear();
        buildAndAttachItems(request.items(), order);

        // No explicit save() needed — the order is a managed entity; changes are
        // flushed automatically when the transaction commits.
        log.debug("Order '{}' items updated by client '{}'",
                order.getOrderNumber(), order.getClient().getUsername());

        return OrderDto.from(order);
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    /**
     * Submits an order for manager approval, transitioning it from
     * {@code CREATED} or {@code DECLINED} → {@code AWAITING_APPROVAL}.
     *
     * <h2>Idempotency</h2>
     * <p>The caller must supply a non-blank {@code idempotencyKey}.
     * On first call the result is stored in Redis under
     * {@code idempotency:{clientId}:{idempotencyKey}} with a 24-hour TTL.
     * Subsequent calls with the same key within the TTL window return the
     * cached response without touching the database.
     *
     * <h2>Status guard</h2>
     * <p>The transition is delegated to {@link OrderStateMachine}. An order
     * that is already in {@code AWAITING_APPROVAL} (or any other non-submittable
     * state) causes the state machine to throw {@link com.example.wms.common.exception.StateMachineException}
     * (HTTP 409).
     *
     * <h2>Redis failure semantics</h2>
     * <p>A Redis write failure after a successful DB commit is logged as WARN
     * and swallowed — the order has been submitted. The worst case is that an
     * identical duplicate request re-executes (the state machine will then
     * return 409 for the second attempt, which is the correct observable behaviour).
     *
     * @param orderId        the UUID of the order to submit
     * @param clientId       the UUID of the authenticated client
     * @param idempotencyKey the value of the {@code Idempotency-Key} request header;
     *                       {@code null} or blank triggers a 400 Bad Request
     * @return the updated order reflecting its new {@code AWAITING_APPROVAL} status
     * @throws BusinessException       (400) if {@code idempotencyKey} is null or blank
     * @throws EntityNotFoundException (404) if the order does not exist or is not owned by this client
     * @throws com.example.wms.common.exception.StateMachineException (409) if the transition is invalid
     */
    @Transactional
    public OrderDto submitOrder(UUID orderId, UUID clientId, @Nullable String idempotencyKey) {

        // 1. Validate idempotency key presence — 400 before any DB work
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(
                    "MISSING_IDEMPOTENCY_KEY",
                    "The 'Idempotency-Key' header is required for order submission.",
                    HttpStatus.BAD_REQUEST);
        }

        // 2. Cache hit — return stored response without touching the DB
        Optional<IdempotentResponse> cached = idempotencyStore.find(clientId, idempotencyKey);
        if (cached.isPresent()) {
            log.debug("Idempotent replay for key '{}:{}' — returning cached response",
                    clientId, idempotencyKey);
            return cached.get().body();
        }

        // 3. Ownership check — 404 for not-found and not-owned (indistinguishable)
        Order order = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        // 4. Transition CREATED/DECLINED → AWAITING_APPROVAL.
        //    The state machine validates the transition and throws StateMachineException (409)
        //    if the current status does not permit it (e.g. already AWAITING_APPROVAL).
        orderStateMachine.transition(
                order,
                OrderStatus.AWAITING_APPROVAL,
                order.getClient().getUsername(),
                null);

        // 5. Build the response while still inside the transaction so lazy associations load
        OrderDto response = OrderDto.from(order);

        // 6. Store in Redis after business logic succeeds.
        //    Wrapped in try-catch inside IdempotencyStore — a Redis failure must not
        //    roll back the already-committed DB transaction.
        idempotencyStore.store(clientId, idempotencyKey, new IdempotentResponse(200, response));

        log.info("Order '{}' submitted by client '{}'",
                order.getOrderNumber(), order.getClient().getUsername());

        return response;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Resolves an authenticated client's username to their UUID.
     *
     * <p>Called by the controller once per request so that a single {@code UUID clientId}
     * can be passed to the write methods ({@link #createOrder}, {@link #updateOrder},
     * {@link #submitOrder}, {@link #cancelOrder}).
     *
     * @param username the value of {@link org.springframework.security.core.Authentication#getName()}
     * @return the client's UUID
     * @throws EntityNotFoundException if no active user exists with that username
     */
    public UUID resolveClientId(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    }

    /**
     * Returns a paginated list of orders owned by the given client, optionally
     * filtered by status.
     *
     * @param clientId the UUID of the authenticated client
     * @param status   optional status filter; {@code null} returns all statuses
     * @param pageable pagination and sort parameters
     * @return a page of slim order summaries (no line-item details)
     */
    public Page<OrderSummaryDto> listOrders(UUID clientId, @Nullable OrderStatus status,
                                            Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findByClientIdAndStatus(clientId, status, pageable)
                : orderRepository.findByClientId(clientId, pageable);
        return page.map(OrderSummaryDto::from);
    }

    /**
     * Returns the full detail view of a single order, including all line items.
     *
     * <p>Ownership is enforced — a non-existent order and an order owned by another
     * client both return HTTP 404.
     *
     * @param orderId  the UUID of the order
     * @param clientId the UUID of the authenticated client
     * @return the full order DTO
     * @throws EntityNotFoundException if the order does not exist or is not owned by this client
     */
    public OrderDto getOrder(UUID orderId, UUID clientId) {
        return orderRepository.findByIdAndClientId(orderId, clientId)
                .map(OrderDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    // -------------------------------------------------------------------------
    // Cancel
    // -------------------------------------------------------------------------

    /**
     * Cancels an order owned by the given client.
     *
     * <p>Valid source states (enforced by {@link OrderStateMachine}):
     * {@code CREATED}, {@code AWAITING_APPROVAL}, {@code APPROVED}, {@code DECLINED}.
     * Attempting to cancel an order in {@code UNDER_DELIVERY}, {@code FULFILLED},
     * or {@code CANCELED} causes the state machine to throw
     * {@link com.example.wms.common.exception.StateMachineException} (HTTP 409).
     *
     * <p>No inventory impact — inventory is only decremented when delivery is scheduled
     * (APPROVED → UNDER_DELIVERY), so cancellation from any earlier state requires
     * no rollback.
     *
     * <p>Ownership is enforced — only the owning client may cancel the order.
     * A non-existent order and an order owned by another client both return HTTP 404
     * to prevent existence enumeration.
     *
     * @param orderId  the UUID of the order to cancel
     * @param clientId the UUID of the authenticated client
     * @throws EntityNotFoundException if the order does not exist or does not belong to this client
     * @throws com.example.wms.common.exception.StateMachineException (409) if the transition is invalid
     */
    @Transactional
    public void cancelOrder(UUID orderId, UUID clientId) {
        Order order = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        orderStateMachine.transition(
                order,
                OrderStatus.CANCELED,
                order.getClient().getUsername(),
                null);

        log.info("Order '{}' cancelled by client '{}'",
                order.getOrderNumber(), order.getClient().getUsername());
    }

    // -------------------------------------------------------------------------
    // Manager operations (TASK-032)
    // -------------------------------------------------------------------------

    /**
     * Approves an order, transitioning it from {@code AWAITING_APPROVAL} → {@code APPROVED}.
     *
     * <p>The transition is validated by {@link OrderStateMachine} — any order not currently
     * in {@code AWAITING_APPROVAL} will cause a {@link com.example.wms.common.exception.StateMachineException}
     * (HTTP 409). The SLA report cache is evicted automatically by the state machine on
     * every status change.
     *
     * @param orderId         the UUID of the order to approve
     * @param managerUsername the username of the authenticated manager performing the action
     * @return the updated order DTO reflecting {@code APPROVED} status
     * @throws EntityNotFoundException if the order does not exist
     * @throws com.example.wms.common.exception.StateMachineException (409) if the order is
     *         not in {@code AWAITING_APPROVAL}
     */
    @Transactional
    public OrderDto approveOrder(UUID orderId, String managerUsername) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        orderStateMachine.transition(order, OrderStatus.APPROVED, managerUsername, null);

        log.info("Order '{}' approved by manager '{}'", order.getOrderNumber(), managerUsername);

        return OrderDto.from(order);
    }

    /**
     * Declines an order, transitioning it from {@code AWAITING_APPROVAL} → {@code DECLINED}.
     *
     * <p>The {@code reason} is persisted on the order entity and must be non-blank —
     * the state machine enforces this and throws {@link com.example.wms.common.exception.BusinessException}
     * (HTTP 422) if absent. The SLA report cache is evicted automatically.
     *
     * <p>A declined order may be updated and re-submitted by the client (unlimited retries).
     *
     * @param orderId         the UUID of the order to decline
     * @param managerUsername the username of the authenticated manager performing the action
     * @param reason          the human-readable reason for declining (must not be blank)
     * @return the updated order DTO reflecting {@code DECLINED} status with the reason set
     * @throws EntityNotFoundException if the order does not exist
     * @throws com.example.wms.common.exception.StateMachineException (409) if the order is
     *         not in {@code AWAITING_APPROVAL}
     * @throws com.example.wms.common.exception.BusinessException (422) if {@code reason} is blank
     */
    @Transactional
    public OrderDto declineOrder(UUID orderId, String managerUsername, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        orderStateMachine.transition(order, OrderStatus.DECLINED, managerUsername, reason);

        log.info("Order '{}' declined by manager '{}' — reason: {}",
                order.getOrderNumber(), managerUsername, reason);

        return OrderDto.from(order);
    }

    /**
     * Returns a paginated list of all orders, optionally filtered by status.
     *
     * <p>Used by the manager dashboard. When no status filter is provided, all orders
     * across all statuses are returned sorted by submission time (newest first).
     *
     * @param status   optional status filter; {@code null} returns all statuses
     * @param pageable pagination and sort parameters
     * @return a page of slim order summaries
     */
    public Page<OrderSummaryDto> listManagerOrders(@Nullable OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findByStatusOrderBySubmittedAtDesc(status, pageable)
                : orderRepository.findAll(pageable);
        return page.map(OrderSummaryDto::from);
    }

    /**
     * Returns the full detail view of any order by ID (no ownership restriction).
     *
     * <p>Used by the manager — a manager may view any order regardless of which
     * client owns it.
     *
     * @param orderId the UUID of the order
     * @return the full order DTO including all line items
     * @throws EntityNotFoundException if the order does not exist
     */
    public OrderDto getManagerOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves each {@link OrderItemRequest} to a persisted {@link InventoryItem},
     * builds an {@link OrderItem} with a locked price snapshot, and attaches it to
     * both sides of the bidirectional {@code Order ↔ OrderItem} relationship.
     *
     * <p>If an inventory item has no price set ({@code unitPrice == null}), the snapshot
     * is recorded as {@code 0.00} — this is valid for items like raw materials that
     * are tracked by volume only.
     *
     * @param itemRequests the line items from the client request
     * @param order        the managed (or pre-persist) order to attach items to
     * @throws EntityNotFoundException if any requested inventory item ID does not exist
     */
    private void buildAndAttachItems(List<OrderItemRequest> itemRequests, Order order) {
        for (OrderItemRequest req : itemRequests) {
            InventoryItem inventoryItem = inventoryItemRepository.findById(req.inventoryItemId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Inventory item not found: " + req.inventoryItemId()));

            BigDecimal priceSnapshot = inventoryItem.getUnitPrice() != null
                    ? inventoryItem.getUnitPrice()
                    : BigDecimal.ZERO;

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .inventoryItem(inventoryItem)
                    .requestedQuantity(req.requestedQuantity())
                    .unitPriceSnapshot(priceSnapshot)
                    .deadlineDate(req.deadlineDate())
                    .build();

            order.getOrderItems().add(item);
        }
    }
}
