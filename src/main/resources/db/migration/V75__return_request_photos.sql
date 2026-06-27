-- Customer-uploaded photos attached to a return request (proof of defect/damage).
CREATE TABLE return_request_photos (
    id                BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    storage_key       VARCHAR(500) NOT NULL,
    file_name         VARCHAR(255),
    content_type      VARCHAR(100),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_return_photos_return ON return_request_photos(return_request_id);
