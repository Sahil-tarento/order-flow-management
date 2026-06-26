# System Design Document: Temporal Workflow & Saga Pattern in Order Flow Management

## 1. Overview
This project implements a distributed order processing system using **Temporal** to orchestrate a robust **Saga Pattern**. The system ensures that long-running transactions across multiple microservices or domains are executed reliably, with automatic retries and built-in compensation mechanisms to handle failures gracefully.

## 2. Architecture & Patterns Used

### 2.1 The Saga Pattern
In a distributed system, a traditional two-phase commit (2PC) is often too restrictive or impossible across different services. Instead, we use the **Saga Pattern**, which breaks a large transaction into a sequence of smaller, local transactions.

If a local transaction fails, the Saga executes a series of **compensating transactions** to undo the changes made by the preceding successful transactions.

In our project, the Order Saga consists of:
1. **Reserve Inventory**
2. **Process Payment**
3. **Confirm Order**

If `Process Payment` fails, the Saga automatically triggers a compensation step to `Release Inventory` and `Cancel Order`.

### 2.2 Temporal Orchestration
We use **Temporal** as the orchestrator for our Saga. Temporal is a stateful execution engine that provides durable execution. It ensures that our workflow code executes exactly once, handling state persistence, retries, and timeouts automatically.

Temporal consists of:
*   **Temporal Server (Cluster):** The backend that maintains state, queues, and timers. (Running in our Docker Compose setup, backed by PostgreSQL).
*   **Temporal Workers:** Our Spring Boot application acts as a worker, polling the Temporal Server for tasks (Workflows and Activities) to execute.

## 3. Implementation Details

### 3.1 Workflows
A **Workflow** in Temporal defines the overall business logic and orchestration of the Saga. It determines *what* happens and *when*, but not *how*.

*   **Interface:** `OrderWorkflow.java`
*   **Implementation:** `OrderWorkflowImpl.java`

**Key Characteristics of our Workflow:**
*   **Deterministic:** Workflow code must be deterministic so Temporal can replay it to recover state. (e.g., we use `Workflow.getLogger()` instead of SLF4J).
*   **Orchestration Logic:** It sequentially calls `reserveInventory`, `processPayment`, and `confirmOrder`.
*   **Compensation Logic:** Wrapped in a `try-catch` block. If an exception occurs, it explicitly catches it and calls the compensating activities (`releaseInventory`, `cancelOrder`).

```java
// Inside OrderWorkflowImpl.java
try {
    reservationId = activities.reserveInventory(orderId);
    paymentId = activities.processPayment(orderId, amount);
    activities.confirmOrder(orderId);
} catch (Exception e) {
    // Compensation Logic
    if (reservationId != null) {
        activities.releaseInventory(orderId, reservationId);
    }
    activities.cancelOrder(orderId, e.getMessage());
    throw e;
}
```

### 3.2 Activities
An **Activity** is a single, idempotent step in a Workflow. Activities are the bridge to the outside world (databases, APIs, third-party services) and perform the actual work (the *how*).

*   **Interface:** `OrderActivities.java`
*   **Implementation:** `OrderActivitiesImpl.java` (Spring-managed Bean)

**Configured with Retry Policies:**
In `OrderWorkflowImpl`, we configure `ActivityOptions` with `RetryOptions`. If an Activity fails (e.g., network timeout), Temporal automatically retries it based on these policies before throwing an exception to the Workflow.

```java
RetryOptions.newBuilder()
    .setInitialInterval(Duration.ofMillis(500))
    .setBackoffCoefficient(2.0)
    .setMaximumAttempts(3)
    .build()
```

### 3.3 Worker Configuration
The Temporal Worker (`TemporalWorkerConfig.java`) connects our Spring Boot app to the Temporal Server.

*   It creates a `Worker` listening to a specific Task Queue (`ORDER_PROCESSING_TASK_QUEUE`).
*   It registers the Workflow class (`OrderWorkflowImpl.class`).
*   It registers the Activity instances (our injected `OrderActivitiesImpl` bean).

## 4. Connection to Infrastructure (PostgreSQL)

It's important to distinguish between application data and Temporal's internal data:

1.  **Application Data:** The Spring Boot app connects directly to PostgreSQL (`orderflow` db) via JDBC to store order aggregates.
2.  **Temporal Internal State:** The Temporal Server container connects to the *same* PostgreSQL instance (but uses separate databases like `temporal` and `temporal_visibility`) to store workflow history, execution state, and visibility data. This is configured entirely within `docker-compose.yml`.

## 5. Summary of Benefits
By using Temporal for the Saga Pattern, we achieve:
*   **Resilience:** Network blips are handled by automatic activity retries.
*   **Durability:** If the Spring Boot app crashes mid-workflow, Temporal remembers the exact state. When the app restarts, the workflow resumes right where it left off.
*   **Visibility:** The Temporal UI (`localhost:8088`) provides real-time visualization of workflow progress, history, and failures.
*   **Clean Code:** Complex failure and compensation logic is kept separate from standard business logic, avoiding deep "callback hell" or complex database state machines.
