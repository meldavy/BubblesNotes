import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { fetchWithAuth } from '../../api/apiClient';

export interface ImageModalProps {
  /** Whether modal is open */
  isOpen: boolean;

  /** Close handler */
  onClose: () => void;

  /** Image source URL (for external images) */
  src?: string;

  /** Pre-fetched blob URL (for authenticated images - avoids re-fetching) */
  blobUrl?: string;

  /** Image alt text */
  alt?: string;

  /** Title to display above the image */
  title?: string;
}

/**
 * ImageModal Component - Displays a large image in a modal overlay
 * 
 * Features:
 * - Full-screen image view with zoom capability
 * - Click outside or press ESC to close
 * - Loading state while image fetches
 * - Authentication support for protected images
 */
export const ImageModal: React.FC<ImageModalProps> = ({
  isOpen,
  onClose,
  src,
  blobUrl,
  alt = 'Image',
  title,
}) => {
  const modalRef = useRef<HTMLDivElement>(null);
  const { getAccessToken } = useAuth();
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [isZoomed, setIsZoomed] = useState(false);
  const [isBlobUrl, setIsBlobUrl] = useState(false);

  // Handle escape key press
  useEffect(() => {
    if (!isOpen) return;

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscape);

    return () => {
      document.removeEventListener('keydown', handleEscape);
    };
  }, [isOpen, onClose]);

  // Prevent body scroll when modal is open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }

    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  // Handle image loading
  useEffect(() => {
    if (!isOpen) {
      // Reset state when modal closes
      setLoading(true);
      setError(false);
      setIsZoomed(false);
      return;
    }

    // If we have a pre-fetched blob URL, use it directly
    if (blobUrl) {
      setImageUrl(blobUrl);
      setIsBlobUrl(true);
      setLoading(false);
      return;
    }

    // Check if this is an authenticated image URL (old format or new attachment format)
    const isAuthenticatedUrl = src && (
      (src.includes('/api/v1/notes/') && src.includes('/images/') && src.includes('/download')) ||
      src.includes('/api/v1/attachments/download')
    );

    if (isAuthenticatedUrl && src) {
      const fetchImage = async () => {
        try {
          const response = await fetchWithAuth(
            src,
            {
              method: 'GET',
            },
            getAccessToken
          );

          if (!response.ok) {
            setError(true);
            setLoading(false);
            return;
          }

          const blob = await response.blob();
          const url = URL.createObjectURL(blob);
          setImageUrl(url);
          setIsBlobUrl(true);
          setLoading(false);
        } catch (err) {
          setError(true);
          setLoading(false);
        }
      };

      fetchImage();

      // Cleanup: revoke the object URL when component unmounts or modal closes
      return () => {
        if (imageUrl && isBlobUrl) {
          URL.revokeObjectURL(imageUrl);
        }
      };
    } else if (src) {
      // For external images, use the src directly
      setImageUrl(src);
      setIsBlobUrl(false);
      setLoading(false);
    }
  }, [isOpen, src, blobUrl, getAccessToken]);

  // Don't render if not open
  if (!isOpen) return null;

  const handleBackdropClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) {
      onClose();
    }
  };

  const handleImageClick = (event: React.MouseEvent<HTMLImageElement>) => {
    // Toggle zoom on image click
    setIsZoomed(!isZoomed);
    event.stopPropagation();
  };

  return (
    // Outer container - full screen, captures all clicks
    <div
      className="fixed inset-0 z-[9999] bg-neutral-900 bg-opacity-90 flex items-center justify-center overflow-auto"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      {/* Modal Content - centered, clicks outside image bubble to backdrop */}
      <div
        ref={modalRef}
        className="relative z-[10000] w-full max-w-6xl p-2"
      >
        {/* Header with close button (always visible) */}
        <div className="flex items-center justify-between mb-4">
          {title && (
            <h2 className="text-lg font-semibold text-white flex-1">{title}</h2>
          )}
          
          <button
            onClick={onClose}
            className="p-2 rounded-full bg-white/20 hover:bg-white/30 text-white transition-colors duration-fast ml-auto flex items-center justify-center"
            aria-label="Close image modal"
          >
            <svg
              className="w-6 h-6 block"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>

        {/* Image Container */}
        <div className="flex items-center justify-center">
          {loading && (
            <div className="flex flex-col items-center justify-center p-12 bg-neutral-800 rounded-lg">
              <div className="animate-spin rounded-full h-12 w-12 border-4 border-primary-500 border-t-transparent mb-4" />
              <p className="text-neutral-400">Loading image...</p>
            </div>
          )}

          {error && (
            <div className="flex flex-col items-center justify-center p-12 bg-neutral-800 rounded-lg">
              <svg
                className="w-16 h-16 text-error-500 mb-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <p className="text-neutral-400">Failed to load image</p>
            </div>
          )}

          {imageUrl && !loading && !error && (
            <img
              src={imageUrl}
              alt={alt}
              onClick={handleImageClick}
              className={`
                max-w-full max-h-[80vh] rounded-lg shadow-2xl cursor-pointer
                transition-transform duration-300 ease-out
                ${isZoomed ? 'scale-110' : 'scale-100'}
              `}
              style={{
                objectFit: 'contain',
              }}
            />
          )}
        </div>

        {/* Footer with instructions - removed since we have visible close button */}
      </div>
    </div>
  );
};

export default ImageModal;
