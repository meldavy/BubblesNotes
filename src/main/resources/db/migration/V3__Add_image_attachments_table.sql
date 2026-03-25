-- Image Attachments table for storing uploaded images
-- Images are independent of notes - they are referenced by path in note content
-- User can manage images in a future "My Files" page
CREATE TABLE image_attachments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    width INTEGER,
    height INTEGER,
    created_at BIGINT DEFAULT 0
);

-- Index for finding images by user
CREATE INDEX idx_image_attachments_user_id ON image_attachments(user_id);

-- Check constraint for file size (max 10MB)
ALTER TABLE image_attachments ADD CONSTRAINT chk_image_attachments_file_size 
    CHECK (file_size <= 10485760);

-- Check constraint for allowed content types
ALTER TABLE image_attachments ADD CONSTRAINT chk_image_attachments_content_type 
    CHECK (content_type IN ('image/png', 'image/jpeg', 'image/gif', 'image/webp'));
