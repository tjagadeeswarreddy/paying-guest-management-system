CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    mode VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    tenant_phone_number VARCHAR(10),
    daily_accommodation BOOLEAN NOT NULL DEFAULT FALSE,
    daily_food_option VARCHAR(20),
    daily_collection_amount NUMERIC(12,2) DEFAULT 0,
    daily_collection_transaction_date DATE,
    daily_collection_account_id BIGINT REFERENCES accounts(id),
    daily_stay_days INTEGER,
    room_number VARCHAR(50) NOT NULL,
    rent NUMERIC(12,2) NOT NULL,
    deposit NUMERIC(12,2) NOT NULL,
    joining_date DATE NOT NULL,
    emergency_contact_number VARCHAR(20),
    emergency_contact_relationship VARCHAR(100),
    sharing VARCHAR(20) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    company_name VARCHAR(255),
    company_address TEXT,
    rent_due_amount NUMERIC(12,2) NOT NULL,
    rent_paid_amount NUMERIC(12,2) DEFAULT 0,
    deposit_paid_amount NUMERIC(12,2) DEFAULT 0,
    joining_collection_account_id BIGINT REFERENCES accounts(id),
    verification_status VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_due_generated_for DATE,
    checkout_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS rent_records (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    billing_month DATE NOT NULL,
    due_amount NUMERIC(12,2) NOT NULL,
    paid_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_month UNIQUE (tenant_id, billing_month)
);

CREATE INDEX IF NOT EXISTS idx_rent_records_status ON rent_records(status);
CREATE INDEX IF NOT EXISTS idx_rent_records_billing_month ON rent_records(billing_month);

CREATE TABLE IF NOT EXISTS due_rents (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    billing_month DATE NOT NULL,
    due_amount NUMERIC(12,2) NOT NULL,
    paid_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    account_id BIGINT REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_due_rents_tenant_month UNIQUE (tenant_id, billing_month)
);

CREATE TABLE IF NOT EXISTS collection_rents (
    due_rent_id BIGINT PRIMARY KEY REFERENCES due_rents(id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    billing_month DATE NOT NULL,
    collected_amount NUMERIC(12,2) NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    account_id BIGINT REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_due_rents_status ON due_rents(status);
CREATE INDEX IF NOT EXISTS idx_due_rents_billing_month ON due_rents(billing_month);
CREATE INDEX IF NOT EXISTS idx_collection_rents_billing_month ON collection_rents(billing_month);

CREATE TABLE IF NOT EXISTS rooms (
    id BIGSERIAL PRIMARY KEY,
    room_number VARCHAR(50) NOT NULL UNIQUE,
    bed_capacity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
