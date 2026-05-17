package org.example.steelbikerunbackend.common.enums;

/**
 * Vòng đời (state machine) của một cuốc xe.
 *
 * <pre>
 * REQUESTED --> ACCEPTED --> ARRIVED --> IN_PROGRESS --> COMPLETED
 *      |            |            |              |
 *      +------------+------------+--------------+--------> CANCELLED
 * </pre>
 */
public enum TripStatus {
    REQUESTED,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
