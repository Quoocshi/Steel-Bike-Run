-- V3: Tạo bảng driver_locations (persistent, sync từ Redis mỗi 30s)
--     và bảng h3_surge_zones (dynamic pricing cache)

CREATE TABLE IF NOT EXISTS driver_locations (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   UUID            NOT NULL UNIQUE REFERENCES drivers(id) ON DELETE CASCADE,
    h3_index    VARCHAR(20)     NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    heading     FLOAT,
    speed       FLOAT,
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_driver_locations_h3       ON driver_locations(h3_index);
CREATE INDEX IF NOT EXISTS idx_driver_locations_updated  ON driver_locations(updated_at);
CREATE INDEX IF NOT EXISTS idx_driver_locations_driver   ON driver_locations(driver_id);

-- H3 surge zones — cache giá surge theo ô lục giác
CREATE TABLE IF NOT EXISTS h3_surge_zones (
    h3_index            VARCHAR(20)     PRIMARY KEY,
    surge_multiplier    FLOAT           NOT NULL DEFAULT 1.0,
    active_drivers      INT             NOT NULL DEFAULT 0,
    pending_trips       INT             NOT NULL DEFAULT 0,
    calculated_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);
