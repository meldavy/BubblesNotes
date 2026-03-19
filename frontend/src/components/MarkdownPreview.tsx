import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Components } from 'react-markdown';

interface MarkdownPreviewProps {
    content: string;
    maxLines?: number;
    className?: string;
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
 */
export const MarkdownPreview: React.FC<MarkdownPreviewProps> = ({
    content,
    maxLines = 3,
    className = ''
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
            <p className="mb-2 text-neutral-600 leading-relaxed" {...props}>{children}</p>
        ),
        ul: ({ children, ...props }) => (
            <ul className="list-disc list-inside mb-2 space-y-1" {...props}>{children}</ul>
        ),
        ol: ({ children, ...props }) => (
            <ol className="list-decimal list-inside mb-2 space-y-1" {...props}>{children}</ol>
        ),
        li: ({ children, ...props }) => (
            <li className="text-neutral-600" {...props}>{children}</li>
        ),
        blockquote: ({ children, ...props }) => (
            <blockquote className="border-l-4 border-primary-300 pl-3 my-2 italic text-neutral-600" {...props}>{children}</blockquote>
        ),
        code: ({ children, ...props }) => (
            <code className="bg-neutral-200 px-1 py-0.5 rounded text-sm font-mono text-neutral-800" {...props}>{children}</code>
        ),
        pre: ({ children, ...props }) => (
            <pre className="bg-neutral-800 text-neutral-100 p-3 rounded-lg overflow-x-auto my-2 text-sm" {...props}>{children}</pre>
        ),
        a: ({ href, children, ...props }) => (
            <a 
                href={href} 
                className="text-primary-600 hover:text-primary-700 underline" 
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
            <th className="border border-neutral-300 px-3 py-1.5 bg-neutral-100 font-semibold text-sm" {...props}>{children}</th>
        ),
        td: ({ children, ...props }) => (
            <td className="border border-neutral-300 px-3 py-1.5 text-sm" {...props}>{children}</td>
        ),
        hr: (props) => <hr className="my-3 border-neutral-300" {...props} />,
    };

    // Apply line clamp style for truncation
    const lineClampStyle = {
        display: '-webkit-box',
        WebkitLineClamp: maxLines,
        WebkitBoxOrient: 'vertical' as const,
        overflow: 'hidden',
    };

    return (
        <div 
            className={`markdown-preview ${className}`}
            style={lineClampStyle}
        >
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeSanitize]}
                components={markdownComponents}
            >
                {content}
            </ReactMarkdown>
        </div>
    );
};
