/**
 * Image Processor Utility
 * 
 * Provides client-side image processing capabilities:
 * - Resize images to max 1920px width while preserving aspect ratio
 * - Optimize image quality for web display
 * - Convert images to optimized formats
 */

const MAX_IMAGE_WIDTH = 1920;
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
const JPEG_QUALITY = 0.9;
const WEBP_QUALITY = 0.9;

/**
 * Resize an image file to max 1920px width while preserving aspect ratio.
 * 
 * @param file - The image file to resize
 * @returns Promise resolving to a Blob with the resized image
 */
export async function resizeImage(file: File): Promise<Blob> {
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => {
            // Calculate new dimensions
            const originalWidth = img.width;
            const originalHeight = img.height;
            const newWidth = Math.min(originalWidth, MAX_IMAGE_WIDTH);
            const newHeight = Math.round(originalHeight * (newWidth / originalWidth));

            // Create canvas and draw resized image
            const canvas = document.createElement('canvas');
            canvas.width = newWidth;
            canvas.height = newHeight;

            const ctx = canvas.getContext('2d');
            if (!ctx) {
                reject(new Error('Failed to get canvas context'));
                return;
            }

            // Draw image with smoothing for better quality
            ctx.imageSmoothingEnabled = true;
            ctx.imageSmoothingQuality = 'high';
            ctx.drawImage(img, 0, 0, newWidth, newHeight);

            // Export with optimized quality
            const mimeType = file.type;
            const quality = mimeType === 'image/jpeg' ? JPEG_QUALITY : 
                           mimeType === 'image/webp' ? WEBP_QUALITY : 0.9;

            canvas.toBlob(
                (blob) => {
                    if (!blob) {
                        reject(new Error('Failed to create resized blob'));
                        return;
                    }
                    resolve(blob);
                },
                mimeType,
                quality
            );
        };

        img.onerror = () => {
            reject(new Error('Failed to load image'));
        };

        // Load image from file
        const url = URL.createObjectURL(file);
        img.src = url;

        // Clean up URL object after loading
        img.onload = img.onload; // Re-assign to preserve handler
    });
}

/**
 * Check if an image file exceeds the maximum size limit.
 * 
 * @param file - The image file to check
 * @returns true if file exceeds 10MB, false otherwise
 */
export function isFileTooLarge(file: File): boolean {
    return file.size > MAX_FILE_SIZE;
}

/**
 * Validate that a file is a supported image format.
 * 
 * @param file - The file to validate
 * @returns true if file is a supported image format
 */
export function isSupportedImageFormat(file: File): boolean {
    const supportedTypes = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];
    return supportedTypes.includes(file.type);
}

/**
 * Check if an image needs resizing by loading it and checking dimensions.
 * 
 * @param file - The image file to check
 * @returns Promise resolving to true if image width exceeds 1920px
 */
export async function imageNeedsResize(file: File): Promise<boolean> {
    return new Promise((resolve) => {
        const img = new Image();
        img.onload = () => {
            resolve(img.width > MAX_IMAGE_WIDTH);
        };
        img.onerror = () => {
            resolve(false); // Default to no resize if we can't load
        };
        const url = URL.createObjectURL(file);
        img.src = url;
        // Clean up after loading
        img.onload = img.onload; // Preserve handler
    });
}

/**
 * Process an image file: validate format, check size, and resize if needed.
 * 
 * @param file - The image file to process
 * @returns Promise resolving to a File object ready for upload
 * @throws Error if validation fails
 */
export async function processImageFile(file: File): Promise<File> {
    // Validate format
    if (!isSupportedImageFormat(file)) {
        throw new Error(
            `Unsupported format: ${file.type}. Supported formats: PNG, JPEG, GIF, WebP`
        );
    }

    // Check size before processing
    if (isFileTooLarge(file)) {
        throw new Error(`File size exceeds 10MB limit. Current size: ${(file.size / 1024 / 1024).toFixed(2)}MB`);
    }

    // Check if resize is needed
    const needsResize = await imageNeedsResize(file);

    if (needsResize) {
        const resizedBlob = await resizeImage(file);
        return new File([resizedBlob], file.name, {
            type: resizedBlob.type,
            lastModified: Date.now(),
        });
    }

    return file;
}
