-- Rename image_attachments table to file_attachments
-- Remove width/height columns (not needed for general files)
-- Update constraints to support all file types
-- Note: Files are stored independently per user, referenced by markdown links

-- Rename the table
ALTER TABLE image_attachments RENAME TO file_attachments;

-- Drop width and height columns (not needed for general files)
ALTER TABLE file_attachments DROP COLUMN IF EXISTS width;
ALTER TABLE file_attachments DROP COLUMN IF EXISTS height;

-- Rename index
ALTER INDEX idx_image_attachments_user_id RENAME TO idx_file_attachments_user_id;

-- Drop old content type constraint
ALTER TABLE file_attachments DROP CONSTRAINT IF EXISTS chk_image_attachments_content_type;

-- Drop old file size constraint
ALTER TABLE file_attachments DROP CONSTRAINT IF EXISTS chk_image_attachments_file_size;

-- Add new file size constraint (max 10MB)
ALTER TABLE file_attachments ADD CONSTRAINT chk_file_attachments_file_size 
    CHECK (file_size <= 10485760);

-- Add new content type constraint to support all file types
-- Images, Documents, Text, Archives, Code, and generic binary
ALTER TABLE file_attachments ADD CONSTRAINT chk_file_attachments_content_type 
    CHECK (content_type IN (
        -- Images
        'image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/bmp', 'image/tiff',
        -- Documents
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        -- Text
        'text/plain', 'text/csv', 'text/markdown', 'text/x-markdown',
        'application/json', 'application/xml', 'text/xml',
        -- Archives
        'application/zip', 'application/x-rar-compressed', 'application/x-7z-compressed',
        'application/gzip', 'application/x-tar',
        -- Code
        'text/x-python', 'text/x-java-source', 'text/x-c', 'text/x-c++', 'text/x-csharp',
        'application/javascript', 'application/typescript', 'text/x-shellscript',
        'text/x-php', 'text/x-ruby', 'text/x-go', 'text/x-rust',
        -- Generic binary
        'application/octet-stream'
    ));
