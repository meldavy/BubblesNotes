import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Components } from 'react-markdown';
import { URLPreview, URLPreviewList } from './URLPreview';

interface URLPreviewData {
  url: string;
  title?: string;
  description?: string;
  favicon?: string;
  image?: string;
  siteName?: string;
}

interface MarkdownPreviewProps {
    content: string;
    maxLines?: number;
    className?: string;
    showURLPreviews?: boolean;
    urlPreviews?: URLPreviewData[];
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
 */
export const MarkdownPreview: React.FC<MarkdownPreviewProps> = ({
    content,
    maxLines,
    className = '',
    showURLPreviews = false,
    urlPreviews = []
}) => {
    if (!content || !content.trim()) {
        return <p className="text-neutral-400 italic">No content</p>;
    }

    // Common markdown element styles with proper typing
    const markdownComponents: Components = {
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
        a: ({ href, children, ...props }) => (
            <a
                href={href}
                className="text-primary-600 hover:text-primary-700 underline break-words"
                target="_blank"
                rel="noopener noreferrer"
                {...props}
            >
                {children}
            </a>
        ),
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
        </div>
    );
};
