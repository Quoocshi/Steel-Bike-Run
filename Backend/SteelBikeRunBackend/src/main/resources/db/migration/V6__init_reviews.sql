-- V6: Initialize Reviews table for trip ratings
-- Reviews are created by customers after completing a trip to rate their driver.

CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ensure one review per trip per reviewer (customer can only review each trip once)
    CONSTRAINT unique_review_per_trip UNIQUE (trip_id, reviewer_id)
);

-- Index for finding reviews by trip (for displaying on receipt)
CREATE INDEX IF NOT EXISTS idx_reviews_trip ON reviews(trip_id);

-- Index for finding reviews by reviewee (driver) - for calculating average rating
CREATE INDEX IF NOT EXISTS idx_reviews_reviewee ON reviews(reviewee_id);

-- Index for finding reviews by reviewer (customer) - for user's review history
CREATE INDEX IF NOT EXISTS idx_reviews_reviewer ON reviews(reviewer_id);
