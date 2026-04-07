# Warehouse Management System

A REST API for managing warehouse inventory, orders, and deliveries.
Built as a technical test project for Lufthansa Industry Solutions.

---

## What it does

The system supports three user roles. Clients create and submit orders for warehouse items.
Warehouse managers review orders, manage inventory and trucks, and schedule deliveries.
System admins manage users, configure the system, and access SLA reporting.

Orders move through a defined lifecycle: CREATED, AWAITING APPROVAL, APPROVED, UNDER DELIVERY, and FULFILLED.
A scheduled job runs nightly and automatically marks orders as fulfilled when their delivery date is reached.
When an order is fulfilled an event is published to Kafka and recorded in the fulfillment log.

---

## Tech Stack

Spring Boot 3, Spring Security with JWT, Spring Data JPA, Flyway, PostgreSQL, Redis, Kafka, Docker Compose.

---

## Running the project

The only requirement is Docker and Docker Compose. No local Java or Maven installation needed.

    git clone <repo-url>
    cd warehouse-management
    cp .env.example .env
    docker compose up --build

The app will be available at http://localhost:8080

Swagger UI (full API docs and testing interface) is at http://localhost:8080/swagger-ui.html

---

## Default credentials

A seed admin user is created automatically on first startup via Flyway migration.

    username: sysadmin
    password: password

Use these credentials to log in via Swagger and create additional users through the admin API.

---

## Key design decisions

All order status changes go through a single state machine class. Status is never set directly anywhere else.

Inventory stock is only decremented when a delivery is scheduled, not when an order is approved.
This means cancelling an approved order requires no inventory rollback.

The order submit endpoint is idempotent. Clients send an Idempotency-Key header and duplicate
requests within 24 hours return the exact original response from Redis.

The Kafka consumer uses manual acknowledgement. Failed messages are retried up to 3 times
before being routed to a dead-letter topic.

Eligible delivery days and SLA reports are cached in Redis with a 5-minute TTL and evicted
automatically when relevant data changes.

Hibernate is set to validate mode only. All schema changes must go through Flyway migrations.

---

## Kafka vs Azure Service Bus

Kafka is a better fit when throughput is high, the team needs full control over the broker,
or the application must run entirely self-hosted with no cloud dependency. It is more complex
to operate but highly portable.

Azure Service Bus removes the need to manage a broker entirely. Dead-letter queues, retry policies,
and message locking are handled by the platform out of the box, making it simpler to operate
and a natural fit for Azure-hosted environments. The trade-off is vendor lock-in and the inability
to run the full stack locally without an Azure subscription or emulator.

For this project the event volume is low and the logic is straightforward, so either would work.
Kafka keeps the stack fully self-contained and runnable with a single docker compose up.
