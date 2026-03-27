import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Components } from 'react-markdown';
import { URLPreview, URLPreviewList } from './URLPreview';
import { ImageModal } from './ui/ImageModal';
import { useAuth } from '../contexts/AuthContext';
import { fetchWithAuth } from '../api/apiClient';
import { isFileAttachmentUrl } from '../utils/attachmentParser';
import { parseTaskItems, toggleTaskCheckbox, ToggleResult } from '../utils/taskListUtils';

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
 * 
 * Uses a shared image cache (passed via imageCacheRef) to prevent re-fetching
 * images when the parent component re-renders.
 */
const AuthenticatedImage: React.FC<AuthenticatedImageProps & { imageCacheRef?: React.MutableRefObject<Map<string, string>> }> = ({ src, alt, className, onClick, imageCacheRef }) => {
  const { getAccessToken } = useAuth();
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const fetchImage = async () => {
      try {
        // Check if image is already cached
        if (imageCacheRef?.current.has(src)) {
          setImageUrl(imageCacheRef.current.get(src)!);
          setLoading(false);
          return;
        }

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
          // Cache the image URL
          imageCacheRef?.current.set(src, url);
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
    // Note: We don't revoke cached URLs as they may be used by other instances
    return () => {
      cancelled = true;
      // Only revoke if not cached
      if (imageUrl && !imageCacheRef?.current.has(src)) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [src, getAccessToken, imageCacheRef]);

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
    /** 
     * Optional callback for checkbox toggle in task lists
     * Receives the line index and current content, returns the updated content
     */
    onCheckboxToggle?: (lineIndex: number, currentContent: string) => void;
    /** 
     * When true, checkboxes in task lists are interactive (clickable)
     * When false, checkboxes are rendered as disabled (read-only)
     */
    isInteractive?: boolean;
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
    onCheckboxToggle,
    isInteractive = false,
}) => {
    const { getAccessToken } = useAuth();
    const [selectedImage, setSelectedImage] = useState<{ blobUrl: string; alt: string } | null>(null);
    
    // Shared image cache to prevent re-fetching images on re-renders
    // This ref persists across re-renders, so images are cached as long as the component is mounted
    const imageCacheRef = useRef<Map<string, string>>(new Map());
    
    // Ref to track the latest content value for use in callbacks
    const contentRef = useRef(content);
    useEffect(() => {
        contentRef.current = content;
    }, [content]);

    // Parse task items to get their line indices and checkbox states
    const taskItems = useMemo(() => parseTaskItems(content), [content]);
    
    // Counter for tracking task item index during rendering
    const taskItemCounter = useRef(0);

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

    // Handle checkbox click on task list items
    const handleTaskItemClick = useCallback((lineIndex: number) => {
        console.log('MarkdownPreview: handleTaskItemClick called with lineIndex:', lineIndex);
        if (isInteractive && onCheckboxToggle) {
            // Use contentRef.current to get the latest content, not the stale closure value
            const latestContent = contentRef.current;
            console.log('MarkdownPreview: Calling onCheckboxToggle with lineIndex:', lineIndex, 'content:', latestContent);
            onCheckboxToggle(lineIndex, latestContent);
        } else {
            console.log('MarkdownPreview: Not interactive or no callback, isInteractive:', isInteractive, 'onCheckboxToggle:', !!onCheckboxToggle);
        }
    }, [isInteractive, onCheckboxToggle]);

    if (!content || !content.trim()) {
        return <p className="text-neutral-400 italic">No content</p>;
    }

    // Memoized markdown components to prevent re-creation on every render
    // This is critical for performance - without this, all images would re-fetch on every keystroke
    const markdownComponents = useMemo<Components>(() => {
        // Reset the task item counter for each render
        let taskRenderIndex = 0;
        
        return {
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
        li: ({ children, ...props }) => {
            // Check if this is a task list item by checking for checkbox in children
            const childrenArray = React.Children.toArray(children);
            const hasCheckbox = childrenArray.some(
                child => React.isValidElement(child) && (child.props as any)?.type === 'checkbox'
            );
            
            // For task list items, use a counter to track which task item we're rendering
            // This avoids issues with duplicate text content
            if (hasCheckbox && isInteractive && onCheckboxToggle) {
                const currentTaskIndex = taskRenderIndex++;
                
                // Get the task item at this index
                const taskItem = taskItems[currentTaskIndex];
                
                if (taskItem) {
                    const lineIndexAttr = taskItem.lineIndex.toString();
                    
                    console.log('MarkdownPreview li: Task item', currentTaskIndex, 'lineIndex:', taskItem.lineIndex, 'text:', taskItem.text);
                    
                    // Wrap children to add data-line-index to checkbox
                    const wrappedChildren = React.Children.map(children, (child) => {
                        if (React.isValidElement(child) && (child.props as any)?.type === 'checkbox') {
                            return React.cloneElement(child, {
                                ...(child.props as any),
                                'data-line-index': lineIndexAttr,
                            } as any);
                        }
                        return child;
                    });
                    return <li className="text-neutral-600 break-words task-list-item" {...props}>{wrappedChildren}</li>;
                } else {
                    console.log('MarkdownPreview li: No task item at index', currentTaskIndex, 'total task items:', taskItems.length);
                }
            }
            
            return <li className="text-neutral-600 break-words task-list-item" {...props}>{children}</li>;
        },
        input: ({ ...props }) => {
            // Handle checkbox inputs for task lists
            if (props.type === 'checkbox') {
                const checked = props.checked as boolean;
                
                // If interactive, make the checkbox clickable
                if (isInteractive && onCheckboxToggle) {
                    // The line index is passed via data-line-index on the checkbox
                    const propsAny = props as any;
                    const lineIndexAttr = propsAny['data-line-index'] as string | undefined;
                    const lineIndex = lineIndexAttr ? parseInt(lineIndexAttr, 10) : -1;
                    
                    console.log('MarkdownPreview input: Checkbox clicked, lineIndex:', lineIndex, 'checked:', checked);
                    
                    const handleClick = (e: React.MouseEvent) => {
                        e.preventDefault();
                        e.stopPropagation();
                        console.log('MarkdownPreview input: handleClick called, lineIndex:', lineIndex);
                        if (lineIndex >= 0) {
                            console.log('MarkdownPreview input: Calling handleTaskItemClick');
                            handleTaskItemClick(lineIndex);
                        } else {
                            console.log('MarkdownPreview input: Invalid lineIndex, not calling handler');
                        }
                    };
                    
                    // Create props without disabled, and explicitly set disabled to undefined
                    const { disabled: _, ...restProps } = props;
                    
                    return (
                        <input
                            type="checkbox"
                            checked={checked}
                            disabled={undefined}
                            className="task-list-checkbox interactive"
                            style={{ cursor: 'pointer' }}
                            onClick={handleClick}
                            {...restProps}
                        />
                    );
                }
                
                // Non-interactive: render as disabled
                return (
                    <input
                        type="checkbox"
                        checked={checked}
                        disabled={true}
                        className="task-list-checkbox"
                        {...props}
                    />
                );
            }
            return <input {...props} />;
        },
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
                return <AuthenticatedImage src={imageSrc} alt={imageAlt} className={className} onClick={handleImageClick} imageCacheRef={imageCacheRef} />;
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
    } as Components;
    }, [taskItems, isInteractive, onCheckboxToggle]);

    // Create a map of line index to task item for quick lookup
    const taskMap = useMemo(() => {
        const map = new Map<number, { checked: boolean; text: string }>();
        taskItems.forEach(item => {
            map.set(item.lineIndex, { checked: item.checked, text: item.text });
        });
        return map;
    }, [taskItems]);

    // Custom li renderer that handles task list items with clickable checkboxes
    const TaskListItemRenderer = () => {
        if (!isInteractive || !onCheckboxToggle) {
            return null;
        }

        // Render clickable checkboxes for each task item
        return (
            <div className="task-checkbox-renderer">
                {taskItems.map((item) => (
                    <button
                        key={item.lineIndex}
                        type="button"
                        className="task-checkbox-btn"
                        onClick={() => handleTaskItemClick(item.lineIndex)}
                        aria-label={item.checked ? 'Uncheck task' : 'Check task'}
                    >
                        <span className={`task-checkbox ${item.checked ? 'checked' : ''}`}>
                            {item.checked ? '✓' : ''}
                        </span>
                    </button>
                ))}
            </div>
        );
    };

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
