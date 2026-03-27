import React, { useState, useCallback, useMemo, memo, useRef } from 'react';
import { Card } from './ui/Card';
import { MarkdownPreview } from './MarkdownPreview';
import { URLPreviewList } from './URLPreview';
import { TagChip } from './ui/TagChip';
import { ImageModal } from './ui/ImageModal';
import { extractAttachments, isFileAttachmentUrl } from '../utils/attachmentParser';
import { useAuth } from '../contexts/AuthContext';
import { fetchWithAuth } from '../api/apiClient';

interface URLPreviewData {
  url: string;
  title?: string;
  description?: string;
  favicon?: string;
  image?: string;
  siteName?: string;
}

interface AttachmentBadgeProps {
  fileName: string;
  url: string;
}

export interface Note {
  id: number;
  userId: string;
  title?: string;
  content: string;
  isPublished: boolean;
  previewData?: string; // JSON string of URL previews
  tags?: string[];
  aiTags?: string[]; // AI-generated tags
  aiTitle?: string; // AI-generated title
  aiSummary?: string; // AI-generated summary
  createdAt: number;
  updatedAt: number;
}

export interface NoteCardProps {
    note: Note;
    isEditing: boolean;
    isDeleting: boolean;
    onEdit: (note: Note) => void;
    onDelete: (noteId: number) => void;
    /** Optional callback when a checkbox is toggled in the note content */
    onCheckboxToggle?: (noteId: number, lineIndex: number, currentContent: string) => Promise<void>;
}

/**
 * AttachmentBadge component - displays a clickable badge for file attachments
 * Styled to match TagChip for visual consistency
 */
const AttachmentBadge: React.FC<AttachmentBadgeProps> = ({ fileName, url }) => {
    const { getAccessToken } = useAuth();

    const handleDownload = async (e: React.MouseEvent) => {
        e.stopPropagation();
        e.preventDefault();

        // Check if this is a file attachment URL that requires authentication
        if (isFileAttachmentUrl(url)) {
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

                // Use the fileName prop directly for the download filename
                const link = document.createElement('a');
                link.href = downloadUrl;
                link.download = fileName;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(downloadUrl);
            } catch (error) {
                console.error('Error downloading file:', error);
                alert('Error downloading file. Please try again.');
            }
        } else {
            // For non-authenticated URLs, use window.open
            window.open(url, '_blank');
        }
    };

    return (
        <a
            onClick={handleDownload}
            className="
                inline-flex items-center gap-1
                rounded-md
                px-2 py-1
                text-xs font-medium
                min-h-0 min-w-0
                bg-neutral-400/10
                text-neutral-600
                hover:bg-neutral-400/20
                hover:text-neutral-800
                transition-colors
            "
            title={`Open ${fileName}`}
        >
            <svg className="w-3 h-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
            </svg>
            <span className="truncate max-w-[320px]">
                {fileName}
            </span>
        </a>
    );
};

/**
 * NoteCard component - displays a single note card
 * Shows title, content preview, tags, URL previews, and action buttons
 * 
 * Memoized with React.memo and a custom comparison function to prevent unnecessary re-renders
 * when other notes update. Only re-renders when this specific note's content actually changes.
 */
export const NoteCard = memo(({
    note,
    isEditing,
    isDeleting,
    onEdit,
    onDelete,
    onCheckboxToggle,
}: NoteCardProps) => {
    // Parse URL previews from previewData
    const urlPreviews: URLPreviewData[] = useMemo(() => {
        if (!note.previewData) return [];
        try {
            return JSON.parse(note.previewData);
        } catch (e) {
            return [];
        }
    }, [note.previewData]);

    // Extract file attachments from content
    const attachments = useMemo(() => {
        return extractAttachments(note.content);
    }, [note.content]);

    // State for image modal
    const [imageModalOpen, setImageModalOpen] = useState(false);
    const [selectedImageBlobUrl, setSelectedImageBlobUrl] = useState<string | null>(null);
    const [selectedImageAlt, setSelectedImageAlt] = useState<string>('Image');

    const handleEdit = () => {
        onEdit(note);
    };

    const handleDelete = () => {
        onDelete(note.id);
    };

    // Handle image click to open modal
    const handleImageClick = useCallback((blobUrl: string, alt: string) => {
        setSelectedImageBlobUrl(blobUrl);
        setSelectedImageAlt(alt);
        setImageModalOpen(true);
    }, []);

    // Handle checkbox toggle - passes the request to parent for optimistic update
    const handleCheckboxToggle = useCallback(async (lineIndex: number, currentContent: string) => {
        console.log('NoteCard: handleCheckboxToggle called with lineIndex:', lineIndex);
        if (!onCheckboxToggle) {
            console.log('NoteCard: No onCheckboxToggle callback');
            return;
        }
        
        // Pass to parent for optimistic update - parent handles the toggle logic and API call
        try {
            await onCheckboxToggle(note.id, lineIndex, currentContent);
            console.log('NoteCard: Checkbox toggle successful');
        } catch (error) {
            console.error('Failed to update note after checkbox toggle:', error);
        }
    }, [note.id, onCheckboxToggle]);

  return (
    <>
      <Card
      hoverable
      className={`animate-slide-up transition-opacity duration-200 ${
        isEditing ? 'opacity-50 pointer-events-none' : ''
      }`}
    >
      <article className="flex flex-col h-full">
        {/* Header with title and editing badge */}
        <div className="flex items-start justify-between mb-2">
          <h3 className="font-semibold text-neutral-800 line-clamp-3">
            {note.aiTitle || note.title || 'Untitled'}
          </h3>
          {isEditing && (
            <span className="text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full">
              Editing
            </span>
          )}
        </div>

        {/* AI Summary (if available) */}
        {note.aiSummary && (
          <div className="mb-2 p-2 bg-accent-purple-50 rounded-md border border-accent-purple-100">
            <p className="text-xs text-accent-purple-700 leading-relaxed">
              {note.aiSummary}
            </p>
          </div>
        )}

        {/* Content preview */}
        <MarkdownPreview
          content={note.content}
          className="text-sm"
          onImageClick={handleImageClick}
          onCheckboxToggle={handleCheckboxToggle}
          isInteractive={!!onCheckboxToggle}
        />

        {/* Tags (user-created) */}
        {note.tags && note.tags.length > 0 && (
          <div className="mt-2 mb-2 flex flex-wrap gap-1.5">
            {note.tags.map(tag => (
              <TagChip
                key={`user-${tag}`}
                label={tag}
                removable={false}
              />
            ))}
          </div>
        )}

        {/* AI-generated tags */}
        {note.aiTags && note.aiTags.length > 0 && (
          <div className="mt-1 mb-2 flex flex-wrap gap-1.5">
            {note.aiTags.map(tag => (
              <TagChip
                key={`ai-${tag}`}
                label={tag}
                removable={false}
                isAiGenerated={true}
              />
            ))}
          </div>
        )}

        {/* URL Preview Cards */}
        {urlPreviews.length > 0 && (
          <div className="mt-3 pt-3 border-t border-neutral-200">
            <h4 className="text-xs font-semibold text-neutral-500 mb-2">Links</h4>
            <URLPreviewList previews={urlPreviews} />
          </div>
        )}

        {/* File Attachments */}
        {attachments.length > 0 && (
          <div className="mt-2 mb-2 flex flex-wrap gap-1.5">
            {attachments.map((attachment, index) => (
              <AttachmentBadge
                key={`attachment-${index}`}
                fileName={attachment.fileName}
                url={attachment.url}
              />
            ))}
          </div>
        )}

        {/* Footer with date and actions */}
        <footer className="mt-auto pt-3 border-t border-neutral-200 flex items-center justify-between">
          <time className="text-xs text-neutral-400">
            {new Date(note.updatedAt).toLocaleDateString('en-US', {
              month: 'short',
              day: 'numeric',
              year: 'numeric',
            })}
          </time>
          <div className="flex items-center space-x-1">
            {/* Edit button */}
            <button
              onClick={handleEdit}
              disabled={isDeleting}
              className="p-1.5 flex items-center justify-center text-neutral-400 hover:text-primary-600 rounded transition-colors duration-fast disabled:opacity-50"
              title="Edit note"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
                />
              </svg>
            </button>

            {/* Delete button */}
            <button
              onClick={handleDelete}
              disabled={isEditing}
              className={`p-1.5 flex items-center justify-center rounded transition-colors duration-fast disabled:opacity-50 ${
                isDeleting
                  ? 'text-primary-600 bg-primary-50 animate-pulse'
                  : 'text-neutral-400 hover:text-error-600 hover:bg-error-50'
              }`}
              title="Delete note"
            >
              {isDeleting ? (
                <svg
                  className="w-4 h-4 animate-spin"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                  />
                </svg>
              ) : (
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                  />
                </svg>
              )}
            </button>
          </div>
        </footer>
      </article>
      </Card>
      {/* Image Modal */}
      <ImageModal
        isOpen={imageModalOpen}
        onClose={() => {
          setImageModalOpen(false);
          setSelectedImageBlobUrl(null);
        }}
        blobUrl={selectedImageBlobUrl || undefined}
        alt={selectedImageAlt}
      />
    </>
  );
}, (prev, next) => {
    // Custom comparison: only re-render if note content actually changed
    // This prevents re-renders when other notes update or when callback props change
    return (
        prev.note.id === next.note.id &&
        prev.note.content === next.note.content &&
        prev.isEditing === next.isEditing &&
        prev.isDeleting === next.isDeleting
    );
});

export default NoteCard;
