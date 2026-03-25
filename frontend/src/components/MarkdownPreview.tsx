import React, { useState, useEffect, useMemo, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Components } from 'react-markdown';
import { URLPreview, URLPreviewList } from './URLPreview';
import { ImageModal } from './ui/ImageModal';
import { useAuth } from '../contexts/AuthContext';
import { fetchWithAuth } from '../api/apiClient';
import { isFileAttachmentUrl } from '../utils/attachmentParser';

interface URLPreviewData {
  url: string;
  title?: string;
  description?: string;
  favicon?: string;
  image?: string;
  siteName?: string;
}

interface AuthenticatedImageProps {
  src: string;
  alt: string;
  className?: string;
  onClick?: (blobUrl: string, alt: string) => void;
}

/**
 * AuthenticatedImage Component - Fetches images with JWT authentication
 * 
 * Since browser <img> tags cannot send authentication headers, this component
 * fetches the image as a blob with the auth token and displays it as a data URL.
 * Uses the shared fetchWithAuth mechanism for automatic token refresh.
 */
const AuthenticatedImage: React.FC<AuthenticatedImageProps> = ({ src, alt, className, onClick }) => {
  const { getAccessToken } = useAuth();
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;

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
        if (!cancelled) {
          const url = URL.createObjectURL(blob);
          setImageUrl(url);
        }
        setLoading(false);
      } catch (err) {
        setError(true);
        setLoading(false);
      }
    };

    fetchImage();

    // Cleanup: revoke the object URL when component unmounts
    return () => {
      cancelled = true;
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [src, getAccessToken]);

  if (loading) {
    return (
      <img
        src=""
        alt={alt}
        className={`${className || ''} animate-pulse bg-neutral-200`}
        style={{ width: '200px', height: '200px', objectFit: 'cover' }}
      />
    );
  }

  if (error || !imageUrl) {
    return (
      <div
        className={`${className || ''} flex items-center justify-center bg-neutral-100 text-neutral-400`}
        style={{ width: '200px', height: '200px' }}
      >
        Image unavailable
      </div>
    );
  }

  return (
    <img
      src={imageUrl}
      alt={alt}
      className={className}
      onClick={onClick ? (e) => { e.stopPropagation(); onClick(imageUrl, alt); } : undefined}
      style={{ cursor: onClick ? 'pointer' : undefined }}
    />
  );
};

interface MarkdownPreviewProps {
    content: string;
    maxLines?: number;
    className?: string;
    showURLPreviews?: boolean;
    urlPreviews?: URLPreviewData[];
    /** Optional callback when image is clicked - receives blob URL */
    onImageClick?: (blobUrl: string, alt: string) => void;
}

/**
 * Download a file using authenticated fetch
 */
async function downloadFileWithAuth(
    url: string,
    getAccessToken: () => string | null,
    filename: string
): Promise<void> {
    try {
        const response = await fetchWithAuth(
            url,
            { method: 'GET' },
            getAccessToken
        );

        if (!response.ok) {
            console.error('Failed to download file:', response.status);
            alert('Failed to download file. Please try again.');
            return;
        }

        const blob = await response.blob();
        const downloadUrl = URL.createObjectURL(blob);

        // Create a temporary link element to trigger the download
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(downloadUrl);
    } catch (error) {
        console.error('Error downloading file:', error);
        alert('Error downloading file. Please try again.');
    }
}

/**
 * Type guard to ensure alt is a string
 */
function ensureString(value: string | undefined | null): string {
    return value ?? 'Image';
}

/**
 * MarkdownPreview Component - Renders markdown content with sanitization
 * 
 * Used for displaying markdown content in cards, previews, and other
 * read-only contexts where the full editor is not needed.
 * 
 * Features:
 * - Sanitized HTML output (rehype-sanitize)
 * - GitHub Flavored Markdown support (remark-gfm)
 * - Custom styling for common markdown elements
 * - Optional line clamping for truncated previews
 * - Optional URL preview cards for links in content
 * - Optional image click handler for modal display
 */
export const MarkdownPreview: React.FC<MarkdownPreviewProps> = ({
    content,
    maxLines,
    className = '',
    showURLPreviews = false,
    urlPreviews = [],
    onImageClick,
}) => {
    const { getAccessToken } = useAuth();
    const [selectedImage, setSelectedImage] = useState<{ blobUrl: string; alt: string } | null>(null);

    // Handle image click - open modal with blob URL
    const handleImageClick = useCallback((blobUrl: string, alt: string) => {
        if (onImageClick) {
            onImageClick(blobUrl, alt);
        } else {
            // Default behavior: open modal
            setSelectedImage({ blobUrl, alt });
        }
    }, [onImageClick]);

    // Handle file attachment download
    const handleFileAttachmentClick = useCallback(async (href: string, event: React.MouseEvent<HTMLAnchorElement>, filename: string) => {
        if (isFileAttachmentUrl(href)) {
            event.preventDefault();
            await downloadFileWithAuth(href, getAccessToken, filename);
        }
        // For non-attachment URLs, allow normal navigation
    }, [getAccessToken]);

    if (!content || !content.trim()) {
        return <p className="text-neutral-400 italic">No content</p>;
    }

    // Memoized markdown components to prevent re-creation on every render
    // This is critical for performance - without this, all images would re-fetch on every keystroke
    const markdownComponents = useMemo<Components>(() => ({
        h1: ({ children, ...props }) => (
            <h1 className="text-xl font-bold text-neutral-800 mb-2" {...props}>{children}</h1>
        ),
        h2: ({ children, ...props }) => (
            <h2 className="text-lg font-semibold text-neutral-700 mb-2" {...props}>{children}</h2>
        ),
        h3: ({ children, ...props }) => (
            <h3 className="text-base font-semibold text-neutral-700 mb-1" {...props}>{children}</h3>
        ),
        p: ({ children, ...props }) => (
            <p className="mb-2 text-neutral-600 leading-relaxed break-words" {...props}>{children}</p>
        ),
        ul: ({ children, ...props }) => (
            <ul className="list-disc list-inside mb-2 space-y-1" {...props}>{children}</ul>
        ),
        ol: ({ children, ...props }) => (
            <ol className="list-decimal list-inside mb-2 space-y-1" {...props}>{children}</ol>
        ),
        li: ({ children, ...props }) => (
            <li className="text-neutral-600 break-words" {...props}>{children}</li>
        ),
        blockquote: ({ children, ...props }) => (
            <blockquote className="border-l-4 border-primary-300 pl-3 my-2 italic text-neutral-600 break-words" {...props}>{children}</blockquote>
        ),
        code: ({ children, className, ...props }) => {
            // Check if this is a code block (has language class) - render without background
            if (className?.match(/language-\w+/)) {
                return (
                    <code className="font-mono text-sm break-words text-inherit" {...props}>{children}</code>
                );
            }
            // Inline code - use the original styling
            return (
                <code className="bg-neutral-200 px-1 py-0.5 rounded text-sm font-mono text-neutral-800 break-words" {...props}>{children}</code>
            );
        },
        pre: ({ children, ...props }) => {
            // For code blocks, add a custom class to enable CSS override
            return (
                <pre className="bg-neutral-800 text-neutral-100 p-3 rounded-lg overflow-x-auto my-2 text-sm code-block" {...props}>
                    {children}
                </pre>
            );
        },
        a: ({ href, children, ...props }) => {
            const isAttachment = href && isFileAttachmentUrl(href);
            // Extract filename from children for file downloads
            let filename = 'download';
            if (typeof children === 'string') {
                filename = children;
            } else if (Array.isArray(children)) {
                // Join array children and strip any non-text content
                filename = children.filter(c => typeof c === 'string').join('') || 'download';
            }
            return (
                <a
                    href={href}
                    className="text-primary-600 hover:text-primary-700 underline break-words"
                    target={isAttachment ? undefined : '_blank'}
                    rel={isAttachment ? undefined : 'noopener noreferrer'}
                    onClick={(e) => href && isAttachment && handleFileAttachmentClick(href, e, filename)}
                    {...props}
                >
                    {children}
                </a>
            );
        },
        table: ({ children, ...props }) => (
            <div className="overflow-x-auto my-2" {...props}>
                <table className="min-w-full border-collapse border border-neutral-300 text-sm">{children}</table>
            </div>
        ),
        th: ({ children, ...props }) => (
            <th className="border border-neutral-300 px-3 py-1.5 bg-neutral-100 font-semibold text-sm break-words" {...props}>{children}</th>
        ),
        td: ({ children, ...props }) => (
            <td className="border border-neutral-300 px-3 py-1.5 text-sm break-words" {...props}>{children}</td>
        ),
        hr: (props) => <hr className="my-3 border-neutral-300" {...props} />,
        img: (props) => {
            const { src, alt, className } = props;
            const imageAlt: string = ensureString(alt);
            // Ensure src is a string
            const imageSrc: string = typeof src === 'string' ? src : '';
            // Check if this is an authenticated image URL (contains /api/v1/notes/.../images/.../download OR /api/v1/attachments/download)
            if (imageSrc && (
                (imageSrc.includes('/api/v1/notes/') && imageSrc.includes('/images/') && imageSrc.includes('/download')) ||
                imageSrc.includes('/api/v1/attachments/download')
            )) {
                return <AuthenticatedImage src={imageSrc} alt={imageAlt} className={className} onClick={handleImageClick} />;
            }
            // For external images, render normally with click handler
            return (
                <img
                    src={imageSrc}
                    alt={imageAlt}
                    className={`${className || ''} max-w-full h-auto rounded-lg my-2`}
                    style={{ maxWidth: '200px', cursor: onImageClick ? 'pointer' : 'default' }}
                    onClick={(e) => { e.stopPropagation(); handleImageClick(imageSrc, imageAlt); }}
                />
            );
        },
    }), [handleImageClick, handleFileAttachmentClick, onImageClick]);

    return (
        <div className={`markdown-preview break-words ${className}`}>
            {maxLines !== undefined ? (
                <div
                    style={{
                        display: '-webkit-box',
                        WebkitLineClamp: maxLines,
                        WebkitBoxOrient: 'vertical' as const,
                        overflow: 'hidden',
                    }}
                >
                    <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        rehypePlugins={[rehypeSanitize]}
                        components={markdownComponents}
                    >
                        {content}
                    </ReactMarkdown>
                </div>
            ) : (
                <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    rehypePlugins={[rehypeSanitize]}
                    components={markdownComponents}
                >
                    {content}
                </ReactMarkdown>
            )}
            
            {/* URL Preview Cards - shown only when enabled and previews are available */}
            {showURLPreviews && urlPreviews && urlPreviews.length > 0 && (
                <div className="mt-4">
                    <h4 className="text-sm font-semibold text-neutral-700 mb-2">Links</h4>
                    <URLPreviewList previews={urlPreviews} />
                </div>
            )}
            
            {/* Image Modal - shown when an image is clicked */}
            {selectedImage && (
                <ImageModal
                    isOpen={true}
                    onClose={() => setSelectedImage(null)}
                    blobUrl={selectedImage.blobUrl}
                    alt={selectedImage.alt}
                />
            )}
        </div>
    );
};
