/**
 * File Upload Service
 *
 * Handles uploading files (images and general attachments) to the backend API.
 * Files are uploaded independently and return a URL and markdown syntax
 * that can be referenced in note content.
 */

import { fetchWithAuth } from '../api/apiClient';
import { useAuth } from '../contexts/AuthContext';

/**
 * Response from the file upload API
 */
interface UploadResponse {
    url: string;
    fileName: string;
    contentType: string;
    isImage: boolean;
    markdown: string;
}

/**
 * Upload a file (independent of any note).
 * Returns URL and markdown syntax that can be referenced in note content.
 *
 * @param file - The file to upload (image or document)
 * @param getAccessToken - Function to get the current access token
 * @returns Promise resolving to the upload response with URL and markdown
 * @throws Error if upload fails
 */
export async function uploadFile(
    file: File,
    getAccessToken: () => string | null
): Promise<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetchWithAuth(
        '/api/v1/attachments',
        {
            method: 'POST',
            body: formData,
            // Note: Do NOT set Content-Type header - browser will set it with boundary
        },
        getAccessToken
    );

    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(
            errorData.message || errorData.error || `Upload failed: ${response.status}`
        );
    }

    return response.json();
}

/**
 * Get the URL for viewing a file by its storage path.
 *
 * @param storagePath - The storage path of the file
 * @param getAccessToken - Function to get the current access token
 * @returns The full URL to view the file
 */
export function getFileUrl(
    storagePath: string,
    getAccessToken: () => string | null
): string {
    // Build the download URL with the storage path as query parameter
    const baseUrl = '/api/v1/attachments/download';
    const encodedPath = encodeURIComponent(storagePath);
    return `${baseUrl}?path=${encodedPath}`;
}

/**
 * Convert a storage path to markdown syntax.
 *
 * @param storagePath - The storage path of the file
 * @param fileName - The file name
 * @param isImage - Whether the file is an image
 * @param getAccessToken - Function to get the current access token
 * @returns Markdown syntax: ![altText](imageUrl) for images, [fileName](url) for files
 */
export function toMarkdown(
    storagePath: string,
    fileName: string,
    isImage: boolean,
    getAccessToken: () => string | null
): string {
    const fileUrl = getFileUrl(storagePath, getAccessToken);
    if (isImage) {
        return `![${fileName}](${fileUrl})`;
    } else {
        return `[${fileName}](${fileUrl})`;
    }
}

/**
 * FileUploadService hook for use in React components.
 * Provides file upload functions with automatic token management.
 *
 * @returns Object containing uploadFile, getFileUrl, toMarkdown functions
 */
export function useFileUploadService() {
    const { getAccessToken } = useAuth();

    const upload = async (file: File): Promise<UploadResponse> => {
        return uploadFile(file, getAccessToken);
    };

    const getUrl = (storagePath: string): string => {
        return getFileUrl(storagePath, getAccessToken);
    };

    const toMarkdown = (storagePath: string, fileName: string, isImage: boolean): string => {
        // The backend returns the markdown syntax directly, so we don't need to construct it here
        // This function is kept for API compatibility but returns a placeholder
        if (isImage) {
            const fileUrl = getFileUrl(storagePath, getAccessToken);
            return `![${fileName}](${fileUrl})`;
        } else {
            const fileUrl = getFileUrl(storagePath, getAccessToken);
            return `[${fileName}](${fileUrl})`;
        }
    };

    return {
        upload,
        getUrl,
        toMarkdown,
    };
}
