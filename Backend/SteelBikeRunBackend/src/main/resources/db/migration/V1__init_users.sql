-- V1: Tạo bảng users

CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(100) NOT NULL UNIQUE,
    phone       VARCHAR(20)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    avatar_url  VARCHAR(500),
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'DRIVER', 'ADMIN')),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
