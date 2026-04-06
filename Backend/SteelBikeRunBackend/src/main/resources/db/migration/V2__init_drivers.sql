-- V2: Tạo bảng drivers

CREATE TABLE IF NOT EXISTS drivers (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL UNIQUE REFERENCES users(id),
    vehicle_plate       VARCHAR(20) NOT NULL UNIQUE,
    vehicle_model       VARCHAR(100) NOT NULL,
    vehicle_color       VARCHAR(50)  NOT NULL,
    license_number      VARCHAR(50) NOT NULL UNIQUE,
    is_online           BOOLEAN     NOT NULL DEFAULT FALSE,
    rating              FLOAT       NOT NULL DEFAULT 5.0,
    total_trips         INT         NOT NULL DEFAULT 0,
    last_face_scan_at   TIMESTAMP,
    face_scan_passed    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_drivers_user_id  ON drivers(user_id);
CREATE INDEX IF NOT EXISTS idx_drivers_is_online ON drivers(is_online);
