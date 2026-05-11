package org.example.steelbikerunbackend.common.enums;

/**
 * Vòng đời (state machine) của một cuốc xe.
 *
 * <pre>
 * REQUESTED --> ACCEPTED --> IN_PROGRESS --> COMPLETED
 *      |            |              |
 *      +------------+--------------+--------> CANCELLED
 * </pre>
 */
public enum TripStatus {
    REQUESTED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
