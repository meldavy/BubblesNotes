/**
 * Utility functions for parsing file attachments from markdown content
 */

export interface AttachmentInfo {
    fileName: string;
    url: string;
    isImage: boolean;
}

// File extensions that indicate actual file attachments (not web URLs)
// This is the SINGLE SOURCE OF TRUTH for all file type handling
export const FILE_ATTACHMENT_EXTENSIONS = [
    // Images (these will be filtered out separately for attachments display)
    '.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.svg', '.ico', '.tiff', '.tif',
    // Documents
    '.pdf', '.doc', '.docx', '.txt', '.rtf', '.odt', '.wps',
    // Spreadsheets
    '.csv', '.xls', '.xlsx', '.ods', '.numbers',
    // Archives
    '.zip', '.gz', '.tar', '.rar', '.7z', '.tgz', '.bz2', '.xz',
    // Code/Text
    '.js', '.ts', '.jsx', '.tsx', '.py', '.java', '.c', '.cpp', '.h', '.hpp',
    '.go', '.rs', '.rb', '.php', '.cs', '.swift', '.kt', '.scala',
    '.json', '.xml', '.yaml', '.yml', '.toml', '.ini', '.cfg', '.conf',
    '.html', '.htm', '.css', '.scss', '.sass', '.less', '.md', '.markdown',
    // Logs
    '.log', '.out', '.err',
    // 3D Files
    '.3mf', '.step', '.stp', '.stl', '.obj', '.fbx', '.dae', '.gltf', '.glb', '.ply', '.off',
    // Executables (if allowed)
    '.exe', '.msi', '.dmg', '.pkg', '.deb', '.rpm',
    // Media
    '.mp3', '.mp4', '.wav', '.avi', '.mov', '.mkv', '.flac', '.ogg', '.m4a', '.wma',
    // Other
    '.bat', '.sh', '.ps1', '.cmd', '.vbs',
];

// Image extensions (subset of FILE_ATTACHMENT_EXTENSIONS)
export const IMAGE_EXTENSIONS = [
    '.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.svg', '.ico', '.tiff', '.tif',
];

/**
 * Check if a file extension is an image
 */
export function isImageExtension(ext: string): boolean {
    return IMAGE_EXTENSIONS.includes(ext.toLowerCase());
}

/**
 * Check if a file extension is allowed for upload
 */
export function isAllowedExtension(ext: string): boolean {
    return FILE_ATTACHMENT_EXTENSIONS.includes(ext.toLowerCase());
}

/**
 * Generate the accept attribute value for file input elements
 * This can be used directly in <input type="file" accept={getFileInputAcceptAttribute()} />
 */
export function getFileInputAcceptAttribute(): string {
    // Use image/* for all images
    // Add specific extensions for non-image files
    const otherExts = FILE_ATTACHMENT_EXTENSIONS
        .filter(ext => !IMAGE_EXTENSIONS.includes(ext))
        .map(ext => ext.toLowerCase());

    return ['image/*', ...otherExts].join(',');
}

/**
 * Generate accept attribute for image-only file inputs
 */
export function getImageInputAcceptAttribute(): string {
    return 'image/*';
}

/**
 * Check if a file is allowed based on its name and/or MIME type
 */
export function isFileAllowed(fileName: string, mimeType?: string): boolean {
    const ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();

    // Check extension first
    if (!isAllowedExtension(ext)) {
        return false;
    }

    // If MIME type provided, also validate it
    if (mimeType) {
        const normalizedType = mimeType.toLowerCase().trim();

        // Block dangerous MIME types
        const blockedPrefixes = [
            'application/x-executable',
            'application/x-msdownload',
            'application/x-msdos-program',
            'application/x-sh',
            'application/x-perl',
            'application/x-php',
            'application/java-archive',
            'application/x-java-applet',
            'application/x-java-jnlp-file',
            'application/x-script',
            'text/x-script',
            'application/x-swf',
            'application/x-shockwave-flash',
        ];

        if (blockedPrefixes.some(prefix => normalizedType.startsWith(prefix))) {
            return false;
        }
    }

    return true;
}

// Web URL patterns that should be excluded from attachments
const WEB_URL_PATTERNS = [
    /^https?:\/\//,  // Standard web URLs
    /^www\./,        // URLs without protocol
    /^\/$/,          // Root path
    /^\/[a-z]/i,     // Web paths starting with / (but not /api/v1/attachments)
];

// Internal file attachment endpoints that should NOT be treated as web URLs
const INTERNAL_ATTACHMENT_ENDPOINTS = [
    '/api/v1/attachments/download',
];

/**
 * Check if a URL is an internal attachment endpoint
 */
function isInternalAttachmentEndpoint(url: string): boolean {
    const urlPath = url.split('?')[0].toLowerCase();
    return INTERNAL_ATTACHMENT_ENDPOINTS.some(endpoint => urlPath.startsWith(endpoint));
}

/**
 * Extract all file attachments from markdown content
 * 
 * Looks for markdown link syntax: [filename](url)
 * Images use: ![alt](url)
 * 
 * Only includes actual file attachments (files with recognized extensions),
 * NOT regular web URLs like [Google](https://www.google.com)
 * 
 * @param content - The markdown content to parse
 * @returns Array of attachment info objects (non-image files only)
 */
export function extractAttachments(content: string): AttachmentInfo[] {
    const attachments: AttachmentInfo[] = [];
    
    // Regex to match markdown links (not images)
    // Images start with !, regular links don't
    const linkRegex = /(?<!!)\[([^\]]+)\]\(([^)]+)\)/g;
    
    let match;
    while ((match = linkRegex.exec(content)) !== null) {
        const fileName = match[1].trim();
        const url = match[2].trim();
        
        // Skip if this looks like a web URL
        if (isWebUrl(url)) {
            continue;
        }
        
        // Check if this is an actual file attachment
        if (!isFileAttachment(url, fileName)) {
            continue;
        }
        
        // Determine if this is an image based on file extension or URL
        const isImage = isImageUrl(url, fileName);
        
        // Only include non-image attachments
        if (!isImage) {
            attachments.push({
                fileName,
                url,
                isImage: false
            });
        }
    }
    
    // Remove duplicates based on URL
    const seenUrls = new Set<string>();
    const uniqueAttachments: AttachmentInfo[] = [];
    
    for (const attachment of attachments) {
        if (!seenUrls.has(attachment.url)) {
            seenUrls.add(attachment.url);
            uniqueAttachments.push(attachment);
        }
    }
    
    return uniqueAttachments;
}

/**
 * Check if a URL looks like a web URL (not a file attachment)
 * 
 * @param url - The URL to check
 * @returns True if the URL appears to be a web URL
 */
function isWebUrl(url: string): boolean {
    // First check if this is an internal attachment endpoint
    if (isInternalAttachmentEndpoint(url)) {
        return false;
    }
    
    const lowerUrl = url.toLowerCase();
    
    for (const pattern of WEB_URL_PATTERNS) {
        if (pattern.test(lowerUrl)) {
            return true;
        }
    }
    
    return false;
}

/**
 * Check if a URL/filename represents an actual file attachment
 *
 * @param url - The URL to check
 * @param fileName - The filename (optional)
 * @returns True if this appears to be a file attachment
 */
function isFileAttachment(url: string, fileName?: string): boolean {
    const lowerUrl = url.toLowerCase();
    const lowerName = fileName?.toLowerCase() || '';

    // Extract the path portion from URL (before query string)
    const urlPath = lowerUrl.split('?')[0];

    // Check if URL or filename has a recognized file extension
    for (const ext of FILE_ATTACHMENT_EXTENSIONS) {
        if (urlPath.endsWith(ext) || lowerName.endsWith(ext)) {
            return true;
        }
    }

    // Check if URL starts with data: (inline file)
    if (url.startsWith('data:')) {
        return true;
    }

    // Check if URL is an internal file attachment endpoint with a file path
    // e.g., /api/v1/attachments/download?path=files/...
    if (urlPath.startsWith('/api/v1/attachments/download')) {
        // Extract the path parameter value and check if it looks like a file path
        const pathParam = lowerUrl.match(/path=([^&]+)/);
        if (pathParam) {
            const decodedPath = decodeURIComponent(pathParam[1]);
            for (const ext of FILE_ATTACHMENT_EXTENSIONS) {
                if (decodedPath.endsWith(ext)) {
                    return true;
                }
            }
        }
    }

    return false;
}

/**
 * Check if a URL is a file attachment download URL
 * This is used to intercept clicks and use authenticated fetch instead of direct navigation
 */
export function isFileAttachmentUrl(url: string): boolean {
    return url.includes('/api/v1/attachments/download');
}

/**
 * Check if a URL or filename points to an image
 * 
 * @param url - The URL to check
 * @param fileName - The filename (optional, for extension check)
 * @returns True if the URL/filename suggests an image
 */
function isImageUrl(url: string, fileName?: string): boolean {
    // Check URL for image extensions
    const imageExtensions = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp', '.svg'];
    const lowerUrl = url.toLowerCase();
    const lowerName = fileName?.toLowerCase() || '';
    
    for (const ext of imageExtensions) {
        if (lowerUrl.endsWith(ext) || lowerName.endsWith(ext)) {
            return true;
        }
    }
    
    // Check if URL starts with data:image
    if (url.startsWith('data:image/')) {
        return true;
    }
    
    return false;
}
