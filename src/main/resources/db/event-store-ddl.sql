-- DDL for event_store and related tables for OrderFlowX

CREATE TABLE IF NOT EXISTS event_store (
  id bigserial PRIMARY KEY,
  aggregate_id varchar(50) NOT NULL,
  aggregate_type varchar(50) NOT NULL,
  event_type varchar(100) NOT NULL,
  version bigint NOT NULL,
  event_data text NOT NULL,
  event_metadata text,
  event_version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  correlation_id varchar(100),
  causation_id varchar(100),
  record_version bigint DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_store_aggregate_version ON event_store (aggregate_id, version);
CREATE INDEX IF NOT EXISTS idx_event_store_aggregate ON event_store (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_event_store_event_type ON event_store (event_type);
CREATE INDEX IF NOT EXISTS idx_event_store_created_at ON event_store (created_at);

-- Orders projection for quick queries
CREATE TABLE IF NOT EXISTS orders (
  id bigserial PRIMARY KEY,
  order_id varchar(50) NOT NULL UNIQUE,
  customer_id varchar(50) NOT NULL,
  amount numeric(19,2) NOT NULL,
  currency varchar(3) NOT NULL,
  status varchar(30) NOT NULL,
  inventory_reservation_id varchar(50),
  payment_id varchar(50),
  cancellation_reason varchar(500),
  failure_reason varchar(500),
  confirmed_at timestamptz,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now(),
  version bigint DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at);

-- Idempotency table for persistent idempotency fallback (optional)
CREATE TABLE IF NOT EXISTS idempotency_keys (
  key varchar(100) PRIMARY KEY,
  order_id varchar(50),
  created_at timestamptz DEFAULT now(),
  expires_at timestamptz
);

-- Audit log table
CREATE TABLE IF NOT EXISTS audit_log (
  id bigserial PRIMARY KEY,
  aggregate_id varchar(50) NOT NULL,
  event_type varchar(100) NOT NULL,
  event_data text NOT NULL,
  created_at timestamptz DEFAULT now()
);
