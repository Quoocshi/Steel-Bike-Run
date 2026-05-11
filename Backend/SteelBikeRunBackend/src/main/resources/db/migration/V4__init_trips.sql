-- V4: Tạo bảng trips (cuốc xe)
--     Mỗi cuốc xe ghi lại snapshot giá tại thời điểm đặt để tránh thay đổi sau này.

CREATE TABLE IF NOT EXISTS trips (
    id                  UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID                NOT NULL REFERENCES users(id),
    driver_id           UUID                REFERENCES drivers(id),  -- NULL cho đến khi matched
    pickup_lat          DOUBLE PRECISION    NOT NULL,
    pickup_lng          DOUBLE PRECISION    NOT NULL,
    pickup_h3_index     VARCHAR(20)         NOT NULL,
    dest_lat            DOUBLE PRECISION    NOT NULL,
    dest_lng            DOUBLE PRECISION    NOT NULL,
    dest_address        VARCHAR(500)        NOT NULL,
    status              VARCHAR(20)         NOT NULL DEFAULT 'REQUESTED',
    base_price          NUMERIC(10, 2)      NOT NULL,
    surge_multiplier    NUMERIC(5, 2)       NOT NULL DEFAULT 1.0,
    final_price         NUMERIC(10, 2)      NOT NULL,
    distance_km         FLOAT               NOT NULL,
    duration_minutes    INT                 NOT NULL,
    requested_at        TIMESTAMP           NOT NULL DEFAULT NOW(),
    accepted_at         TIMESTAMP,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP
);

-- Index tra cứu lịch sử trip theo customer/driver (có thứ tự mới nhất trước)
CREATE INDEX IF NOT EXISTS idx_trips_customer   ON trips(customer_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_trips_driver     ON trips(driver_id, requested_at DESC);
-- Index lọc trip đang active (tránh full table scan)
CREATE INDEX IF NOT EXISTS idx_trips_status     ON trips(status) WHERE status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS');
-- Index GROUP BY h3 để tính surge pricing
CREATE INDEX IF NOT EXISTS idx_trips_h3         ON trips(pickup_h3_index);
