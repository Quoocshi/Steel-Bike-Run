-- V5: Thêm trạng thái ARRIVED vào trips và cột arrived_at
--
-- Lý do: TripService.arriveAtPickup() cần lưu trạng thái ARRIVED khi driver
--         xác nhận đã đến điểm đón, nhưng DB constraint cũ chỉ cho phép
--         REQUESTED | ACCEPTED | IN_PROGRESS | COMPLETED | CANCELLED.
--         Thêm ARRIVED vào constraint và bổ sung cột timestamp arrived_at.

-- Bước 1: Xóa constraint cũ (nếu tồn tại)
ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_status_check;

-- Bước 2: Thêm constraint mới bao gồm ARRIVED
ALTER TABLE trips
    ADD CONSTRAINT trips_status_check
    CHECK (status IN ('REQUESTED', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- Bước 3: Thêm cột arrived_at để lưu thời điểm driver đến điểm đón
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMP;

-- Cập nhật index active trips để bao gồm ARRIVED
DROP INDEX IF EXISTS idx_trips_status;
CREATE INDEX idx_trips_status ON trips(status)
    WHERE status IN ('REQUESTED', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS');
