import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Button } from './ui/Button';
import { MarkdownPreview } from './MarkdownPreview';
import { URLPreviewList } from './URLPreview';
import { TagInput } from './TagInput';
import { InlineLoading } from './ui/Loading';
import { useToastNotifications } from './ui/Toast';
import { useAuth } from '../contexts/AuthContext';
import { uploadFile, useFileUploadService } from '../services/imageUploadService';
import { resizeImage, isSupportedImageFormat, isFileTooLarge, imageNeedsResize } from '../utils/imageProcessor';
import {
    FILE_ATTACHMENT_EXTENSIONS,
    IMAGE_EXTENSIONS,
    isImageExtension,
    isAllowedExtension,
    getFileInputAcceptAttribute,
    getImageInputAcceptAttribute,
    isFileAllowed,
} from '../utils/attachmentParser';
import { parseTaskItems, toggleTaskCheckbox } from '../utils/taskListUtils';

// Maximum file size: 10MB
const MAX_FILE_SIZE = 10 * 1024 * 1024;

// Check if a file is an image
function isImageFile(file: File): boolean {
    return file.type.startsWith('image/');
}

interface URLPreviewData {
  url: string;
  title?: string;
  description?: string;
  favicon?: string;
  image?: string;
  siteName?: string;
}

interface TagInfo {
    id: number;
    name: string;
}

interface NoteEditorProps {
    value: string;
    onChange: (value: string) => void;
    onSave?: () => Promise<void>;
    placeholder?: string;
    isEditing?: boolean;
    onCancel?: () => void;
    isSaving?: boolean;
    noteId?: number | null;
    tags?: string[];
    onTagChange?: (tags: string[]) => void;
}

/**
 * NoteEditor Component - Markdown Note Editing with Auto-Save
 * 
 * Auto-Save Behavior:
 * - Quick Notes (no noteId): Auto-save is DISABLED. Content is only saved to localStorage.
 *   User must manually click "Save" to create a new note via API.
 * - Editing Existing Notes (has noteId): Auto-save updates the existing note via PUT
 *   after 3 seconds of inactivity. Shows "Saving..." / "Saved" indicators.
 * 
 * Draft Preservation:
 * - All content changes are saved to localStorage immediately
 * - Drafts are restored on page reload
 * - Drafts are cleared only after successful manual save
 */
export const NoteEditor: React.FC<NoteEditorProps> = ({
    value,
    onChange,
    onSave,
    placeholder = "Type your note here...",
    isEditing = false,
    onCancel,
    isSaving = false,
    noteId = null,
    tags = [],
    onTagChange
}) => {
    const [isFocused, setIsFocused] = useState(false);
    const [viewMode, setViewMode] = useState<'edit' | 'preview' | 'split'>('edit');
    const [saveStatus, setSaveStatus] = useState<'saving' | 'saved' | 'unsaved' | 'error'>('saved');
    const [saveError, setSaveError] = useState<string | null>(null);
    const [pendingToggles, setPendingToggles] = useState<Set<number>>(new Set());
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const [previews, setPreviews] = useState<URLPreviewData[]>([]);
    const [isLoadingPreviews, setIsLoadingPreviews] = useState(false);
    const [uploadStatus, setUploadStatus] = useState<{
        status: 'idle' | 'uploading' | 'success' | 'error';
        fileName: string;
        errorMessage: string;
    }>({ status: 'idle', fileName: '', errorMessage: '' });
    const [isDragOver, setIsDragOver] = useState(false);
    const { getAccessToken } = useAuth();
    const toast = useToastNotifications();

    // Fetch previews when noteId is available
    useEffect(() => {
        const fetchPreviews = async () => {
            if (!noteId) {
                setPreviews([]);
                return;
            }

            try {
                setIsLoadingPreviews(true);
                const response = await fetch(`/api/v1/notes/${noteId}/previews`, {
                    credentials: 'include'
                });
                if (response.ok) {
                    const data = await response.json();
                    setPreviews(data);
                }
            } catch (err) {
                console.error('Failed to fetch previews:', err);
            } finally {
                setIsLoadingPreviews(false);
            }
        };

        fetchPreviews();
    }, [noteId]);

    // Draft key for localStorage
    const getDraftKey = () => noteId ? `note-draft-${noteId}` : 'note-draft-quick';

    // Load draft from localStorage on mount
    useEffect(() => {
        const savedDraft = localStorage.getItem(getDraftKey());
        if (savedDraft && !value) {
            onChange(savedDraft);
            setSaveStatus('unsaved');
        }
    }, []); // Run once on mount

    // Save draft to localStorage with debounce (500ms) to avoid excessive writes
    useEffect(() => {
        const timer = setTimeout(() => {
            localStorage.setItem(getDraftKey(), value);
        }, 500);
        return () => clearTimeout(timer);
    }, [value]);

    // Clear draft after successful save
    useEffect(() => {
        if (saveStatus === 'saved' && value) {
            localStorage.removeItem(getDraftKey());
        }
    }, [saveStatus]);

    // Auto-save for editing existing notes only
    useEffect(() => {
        // Only auto-save when editing an existing note (not for quick notes)
        if (!isEditing || !noteId) return;

        // Don't auto-save if content hasn't changed or is empty
        if (!value.trim() || saveStatus === 'saved') return;

        // Clear existing timer
        if (saveTimerRef.current) {
            clearTimeout(saveTimerRef.current);
        }

        // Set timer for auto-save (3 second debounce)
        saveTimerRef.current = setTimeout(async () => {
            setSaveStatus('saving');
            setSaveError(null);

            try {
                if (onSave) {
                    await onSave();
                }
                setSaveStatus('saved');
                
                // Fetch previews after auto-save to update them
                if (noteId) {
                    const response = await fetch(`/api/v1/notes/${noteId}/previews`, {
                        credentials: 'include'
                    });
                    if (response.ok) {
                        const data = await response.json();
                        setPreviews(data);
                    }
                }
            } catch (err) {
                setSaveStatus('error');
                setSaveError(err instanceof Error ? err.message : 'Auto-save failed');
            }
        }, 3000);

        return () => {
            if (saveTimerRef.current) {
                clearTimeout(saveTimerRef.current);
            }
        };
    }, [value, isEditing, noteId, onSave]);

    // Tags are saved as part of the note payload now; no separate calls needed

    // Memoized handleSave to prevent re-creation on each render
    const handleSave = useCallback(async () => {
        if (!value.trim()) return;

        setSaveStatus('saving');
        setSaveError(null);

        try {
             if (onSave) {
                 await onSave();
             }
             
             // Tags are included in note update/create; nothing extra to do here
             
             setSaveStatus('saved');
          } catch (err) {
             setSaveStatus('error');
             setSaveError(err instanceof Error ? err.message : 'Save failed');
         }
    }, [value, onSave, noteId, tags]);

    // Memoized cycleViewMode to prevent re-creation on each render
    const cycleViewMode = useCallback(() => {
        setViewMode(prev => {
            if (prev === 'edit') return 'preview';
            if (prev === 'preview') return 'split';
            return 'edit';
        });
    }, []);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Ctrl+S to save
            if (e.ctrlKey && e.key === 's') {
                e.preventDefault();
                handleSave();
            }
            // Ctrl+Shift+P to toggle view mode
            if (e.ctrlKey && e.shiftKey && e.key === 'P') {
                e.preventDefault();
                cycleViewMode();
            }
            // Enter without modifier to finish editing (blur)
            if (e.key === 'Enter' && !e.shiftKey && !isFocused) {
                e.preventDefault();
                textareaRef.current?.focus();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isFocused, handleSave, cycleViewMode]);

    const setViewModeByLabel = (mode: 'edit' | 'preview' | 'split') => {
        setViewMode(mode);
    };

    // Handle checkbox toggle in task lists
    const handleCheckboxToggle = useCallback(async (lineIndex: number) => {
        // Mark as pending for visual feedback
        setPendingToggles(prev => new Set(prev).add(lineIndex));

        // Toggle the checkbox in the content
        const result = toggleTaskCheckbox(value, lineIndex);
        
        if (result.success && result.updatedContent) {
            // Update the content - this will trigger the existing auto-save mechanism
            onChange(result.updatedContent);
        } else {
            console.error('Checkbox toggle failed:', result.error);
            toast.error('Failed to toggle task');
        }

        // Remove from pending after a short delay (or immediately since we updated content)
        setTimeout(() => {
            setPendingToggles(prev => {
                const next = new Set(prev);
                next.delete(lineIndex);
                return next;
            });
        }, 100);
    }, [value, onChange, toast]);

    // Markdown toolbar buttons
    const toolbarButtons = [
        { icon: 'B', action: () => insertMarkdown('**', '**'), label: 'Bold', shortcut: 'Ctrl+B' },
        { icon: 'I', action: () => insertMarkdown('*', '*'), label: 'Italic', shortcut: 'Ctrl+I' },
        { icon: 'H', action: () => insertMarkdown('\n# ', '', 0), label: 'Heading', shortcut: 'Ctrl+H' },
        { icon: '1', action: () => insertMarkdown('\n1. ', '', 0), label: 'Numbered List', shortcut: 'Ctrl+1' },
        { icon: '•', action: () => insertMarkdown('\n- ', '', 0), label: 'Bullet List', shortcut: 'Ctrl+2' },
        { icon: '>', action: () => insertMarkdown('\n> ', '', 0), label: 'Quote', shortcut: 'Ctrl+Q' },
        { icon: '`', action: () => insertMarkdown('`', '`'), label: 'Inline Code', shortcut: 'Ctrl+E' },
        { icon: '⎇', action: () => insertMarkdown('\n```\n', '\n```', 0), label: 'Code Block', shortcut: 'Ctrl+K' },
        { icon: null, action: () => insertMarkdown('\n- [ ] ', '', 0), label: 'Task List', shortcut: 'Ctrl+T', svgIcon: (
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <rect x="3" y="3" width="18" height="18" rx="2" strokeWidth="2"/>
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 10l4 4L19 6"/>
            </svg>
        ) },
        { icon: null, action: () => insertMarkdown('[]()', '', -1), label: 'Link', shortcut: 'Ctrl+L', svgIcon: (
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
        ) },
    ];

    const insertMarkdown = (before: string, after: string, positionOffset: number = 0) => {
        const textarea = textareaRef.current;
        if (!textarea) return;

        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const text = value;
        const selectedText = text.substring(start, end);
        
        const newText = text.substring(0, start) + before + selectedText + after + text.substring(end);
        onChange(newText);
        
        // Restore focus and set cursor position
        setTimeout(() => {
            textarea.focus();
            const newCursorPos = positionOffset === -1 
                ? start + before.length + selectedText.length + after.length - 1
                : start + before.length + (positionOffset < 0 ? 0 : positionOffset);
            textarea.setSelectionRange(newCursorPos, newCursorPos);
        }, 0);
    };

    // Handle paste event for file upload (images or documents)
    const handlePaste = async (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
        const clipboardItems = e.clipboardData.items;
        
        // Look for a file in clipboard (image or other supported type)
        let file: File | null = null;
        let isImage = false;
        
        for (const item of clipboardItems) {
            // Prioritize images first
            if (item.type && item.type.startsWith('image/')) {
                file = item.getAsFile();
                isImage = true;
                break;
            }
        }
        
        // If no image found, look for other supported file types
        if (!file) {
            for (const item of clipboardItems) {
                if (item.type && (item.type.startsWith('application/') || item.type.startsWith('text/'))) {
                    file = item.getAsFile();
                    isImage = false;
                    break;
                }
            }
        }
        
        // If no file found, let the default paste behavior handle it
        if (!file) {
            return;
        }
        
        // Prevent default paste behavior
        e.preventDefault();
        
        await processFileUpload(file, isImage);
    };

    // Process file upload (reusable for paste and drag-drop) - handles both images and documents
    const processFileUpload = async (file: File, isImageUpload: boolean = true) => {
        // Validate file using shared validation
        if (!isFileAllowed(file.name, file.type)) {
            const errorMsg = `Unsupported file type: ${file.type}. Please upload images, PDFs, documents, text files, archives, 3D files, or other supported formats.`;
            setUploadStatus({
                status: 'error',
                fileName: file.name,
                errorMessage: errorMsg
            });
            toast.error(errorMsg);
            return;
        }
        
        // Check size
        if (file.size > MAX_FILE_SIZE) {
            const errorMsg = `File size exceeds 10MB limit. Current size: ${(file.size / 1024 / 1024).toFixed(2)}MB`;
            setUploadStatus({
                status: 'error',
                fileName: file.name,
                errorMessage: errorMsg
            });
            toast.error(errorMsg);
            return;
        }
        
        // Set uploading status
        setUploadStatus({
            status: 'uploading',
            fileName: file.name,
            errorMessage: ''
        });
        
        try {
            let fileToUpload: File = file;
            
            // For images, check if resize is needed
            if (isImageUpload && isImageFile(file)) {
                const needsResize = await imageNeedsResize(file);
                if (needsResize) {
                    const resizedBlob = await resizeImage(file);
                    fileToUpload = new File([resizedBlob], file.name, {
                        type: resizedBlob.type,
                        lastModified: Date.now(),
                    });
                }
            }
            
            // Upload the file
            const uploadResult = await uploadFile(fileToUpload, getAccessToken);
            
            // Insert markdown at cursor position (use the markdown returned from API)
            const textarea = textareaRef.current;
            if (textarea) {
                const start = textarea.selectionStart;
                const end = textarea.selectionEnd;
                const text = value;
                
                const newText = text.substring(0, start) + uploadResult.markdown + '\n' + text.substring(end);
                onChange(newText);
                
                // Set cursor position after the inserted content
                setTimeout(() => {
                    textarea.focus();
                    const newPos = start + uploadResult.markdown.length + 1;
                    textarea.setSelectionRange(newPos, newPos);
                }, 0);
            }
            
            // Show success toast
            const fileType = isImageFile(file) ? 'Image' : 'File';
            toast.success(`${fileType} "${uploadResult.fileName}" uploaded successfully`);
            
            // Reset upload status
            setUploadStatus({ status: 'idle', fileName: '', errorMessage: '' });
            
        } catch (error) {
            const errorMsg = error instanceof Error ? error.message : 'Upload failed';
            setUploadStatus({
                status: 'error',
                fileName: file.name,
                errorMessage: errorMsg
            });
            toast.error(errorMsg);
        }
    };

    // Drag and drop handlers
    const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(true);
    };

    const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(false);
    };

    const handleDrop = async (e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(false);
        
        const files = e.dataTransfer.files;
        if (files.length === 0) return;
        
        // Find the first supported file (image or document)
        let file: File | null = null;
        for (let i = 0; i < files.length; i++) {
            if (isFileAllowed(files[i].name, files[i].type)) {
                file = files[i];
                break;
            }
        }
        
        if (!file) {
            toast.error('Unsupported file type. Please upload images, documents, archives, 3D files, or other supported formats.');
            return;
        }
        
        // Focus the textarea
        textareaRef.current?.focus();
        
        // Determine if it's an image based on MIME type
        const isImage = file.type.startsWith('image/');
        await processFileUpload(file, isImage);
    };

    // Toolbar upload button handlers
    const imageInputRef = useRef<HTMLInputElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    
    const handleImageUploadClick = () => {
        imageInputRef.current?.click();
    };

    const handleFileUploadClick = () => {
        fileInputRef.current?.click();
    };

    const handleImageInputChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || files.length === 0) return;
        
        const file = files[0];
        
        // Focus the textarea
        textareaRef.current?.focus();
        
        await processFileUpload(file, true);
        
        // Reset file input to allow selecting the same file again
        if (imageInputRef.current) {
            imageInputRef.current.value = '';
        }
    };

    const handleFileInputChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || files.length === 0) return;
        
        const file = files[0];
        
        // Focus the textarea
        textareaRef.current?.focus();
        
        await processFileUpload(file, false);
        
        // Reset file input to allow selecting the same file again
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    return (
        <div className="w-full bg-white rounded-lg border border-neutral-200 shadow-sm overflow-hidden transition-all duration-normal hover:shadow-md">
            {/* Drag and drop overlay */}
            {isDragOver && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-primary-500/20 backdrop-blur-sm pointer-events-none">
                    <div className="bg-white px-8 py-4 rounded-lg shadow-xl border-2 border-primary-500 flex items-center space-x-3">
                        <svg className="w-8 h-8 text-primary-500 animate-bounce" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                        </svg>
                        <span className="text-lg font-medium text-primary-700">Drop file to upload</span>
                    </div>
                </div>
            )}
            
            {/* Editor Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-neutral-200 bg-neutral-50">
                <div className="flex items-center space-x-2">
                    <svg className="w-5 h-5 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    <span className="text-sm font-medium text-neutral-700">
                        {isEditing ? 'Editing Note' : 'Quick Note'}
                    </span>
                    {isEditing && (
                        <span className="text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full">
                            Edit Mode
                        </span>
                    )}
                </div>
                
                <div className="flex items-center space-x-3">
                    {/* Auto-save indicator (only shown when editing existing notes) */}
                    {isEditing && saveStatus === 'saving' && (
                        <div className="flex items-center space-x-1.5">
                            <svg className="w-4 h-4 text-primary-500 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                            </svg>
                            <span className="text-xs text-primary-600 font-medium">Saving...</span>
                        </div>
                    )}
                    {isEditing && saveStatus === 'saved' && value && (
                        <div className="flex items-center space-x-1.5">
                            <svg className="w-4 h-4 text-success-500" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                            </svg>
                            <span className="text-xs text-success-600 font-medium">Saved</span>
                        </div>
                    )}
                    {isEditing && saveStatus === 'error' && (
                        <div className="flex items-center space-x-1.5">
                            <svg className="w-4 h-4 text-error-500" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
                            </svg>
                            <span className="text-xs text-error-600 font-medium">Save Failed</span>
                        </div>
                    )}
                    
                    {/* View mode toggle buttons */}
                    <div className="flex items-center bg-neutral-100 rounded-md p-0.5">
                        <button
                            onClick={() => setViewModeByLabel('edit')}
                            className={`px-3 py-1 text-sm font-medium rounded transition-all duration-fast ${
                                viewMode === 'edit' 
                                    ? 'bg-white text-primary-600 shadow-sm' 
                                    : 'text-neutral-600 hover:text-neutral-800'
                            }`}
                            title="Edit mode"
                        >
                            Edit
                        </button>
                        <button
                            onClick={() => setViewModeByLabel('preview')}
                            className={`px-3 py-1 text-sm font-medium rounded transition-all duration-fast ${
                                viewMode === 'preview' 
                                    ? 'bg-white text-primary-600 shadow-sm' 
                                    : 'text-neutral-600 hover:text-neutral-800'
                            }`}
                            title="Preview mode"
                        >
                            Preview
                        </button>
                        <button
                            onClick={() => setViewModeByLabel('split')}
                            className={`px-3 py-1 text-sm font-medium rounded transition-all duration-fast ${
                                viewMode === 'split' 
                                    ? 'bg-white text-primary-600 shadow-sm' 
                                    : 'text-neutral-600 hover:text-neutral-800'
                            }`}
                            title="Split view"
                        >
                            Split
                        </button>
                    </div>
                </div>
            </div>

            {/* Markdown Toolbar (only in edit or split mode) */}
            {(viewMode === 'edit' || viewMode === 'split') && (
                <div className="flex items-center flex-wrap gap-1 px-4 py-2 border-b border-neutral-200 bg-white">
                    {toolbarButtons.map((btn, index) => (
                        <button
                            key={index}
                            onClick={btn.action}
                            title={`${btn.label} (${btn.shortcut})`}
                            className="p-1.5 text-sm text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800 rounded transition-colors duration-fast flex items-center justify-center"
                        >
                            {btn.svgIcon || btn.icon}
                        </button>
                    ))}
                    {/* Image upload button */}
                    <button
                        onClick={handleImageUploadClick}
                        title="Upload image"
                        className="p-1.5 text-sm text-primary-600 hover:bg-primary-50 rounded transition-colors duration-fast flex items-center justify-center"
                    >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                    </button>
                    {/* File attachment button */}
                    <button
                        onClick={handleFileUploadClick}
                        title="Attach file"
                        className="p-1.5 text-sm text-neutral-600 hover:bg-neutral-100 rounded transition-colors duration-fast flex items-center justify-center"
                    >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
                        </svg>
                    </button>
                    {/* Hidden file inputs */}
                    <input
                        ref={imageInputRef}
                        type="file"
                        accept={getImageInputAcceptAttribute()}
                        onChange={handleImageInputChange}
                        className="hidden"
                    />
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept={getFileInputAcceptAttribute()}
                        onChange={handleFileInputChange}
                        className="hidden"
                    />
                </div>
            )}

            {/* Editor or Preview */}
            <div 
                className="relative"
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
            >
                {(viewMode === 'edit' || viewMode === 'split') && (
                    <div className={`${viewMode === 'split' ? 'w-1/2 float-left border-r border-neutral-200' : 'w-full'}`}>
                        <textarea
                            ref={textareaRef}
                            className="w-full h-64 p-4 resize-none transition-all duration-fast outline-none border-none bg-white font-mono text-sm"
                            value={value}
                            onChange={(e) => {
                                onChange(e.target.value);
                            }}
                            onPaste={handlePaste}
                            onFocus={() => setIsFocused(true)}
                            onBlur={() => setIsFocused(false)}
                            placeholder={placeholder}
                            spellCheck={false}
                        />
                        {/* Upload status indicator */}
                        {uploadStatus.status === 'uploading' && (
                            <div className="px-4 py-2 bg-primary-50 border-t border-primary-200">
                                <InlineLoading message={`Uploading ${uploadStatus.fileName}...`} size="sm" />
                            </div>
                        )}
                        {uploadStatus.status === 'error' && (
                            <div className="px-4 py-2 bg-error-bg border-t border-error-500">
                                <div className="flex items-center text-sm text-error-600">
                                    <svg className="w-4 h-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                                    </svg>
                                    <span>{uploadStatus.errorMessage}</span>
                                </div>
                            </div>
                        )}
                    </div>
                )}
                
                {(viewMode === 'preview' || viewMode === 'split') && (
                    <div className={`${viewMode === 'split' ? 'w-1/2' : 'w-full'} ${viewMode === 'split' ? 'float-left' : ''}`}>
                        <div className="w-full h-64 p-4 overflow-auto bg-neutral-50">
                            {value ? (
                                <MarkdownPreview 
                                    content={value} 
                                    showURLPreviews={true}
                                    urlPreviews={previews}
                                    maxLines={100} // Don't truncate in editor preview
                                    className="text-sm"
                                    onCheckboxToggle={handleCheckboxToggle}
                                    isInteractive={true}
                                />
                            ) : (
                                <p className="text-neutral-400 italic">No content to preview</p>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* Tag Input - placed below content editor */}
            {(viewMode === 'edit' || viewMode === 'split') && onTagChange && (
                <div className="px-4 py-3 border-t border-neutral-200 bg-white">
                    <TagInput
                        tags={tags}
                        onChange={onTagChange}
                        placeholder="Add tags (press Enter)..."
                        maxTags={10}
                    />
                </div>
            )}

            {/* Footer with keyboard shortcuts hint and action buttons */}
            <div className="px-4 py-2 border-t border-neutral-200 bg-neutral-50 flex items-center justify-between">
                <span className="text-xs text-neutral-500">{value.length} characters</span>
                <div className="hidden md:flex items-center space-x-3">
                    {saveError && (
                        <span className="text-xs text-error-600">{saveError}</span>
                    )}
                    <span className="text-xs text-neutral-400">Ctrl+S to save • Ctrl+Shift+P to toggle view</span>
                    {isEditing && onCancel && (
                        <>
                            <Button
                                variant="secondary"
                                size="sm"
                                onClick={onCancel}
                                disabled={isSaving}
                            >
                                Cancel
                            </Button>
                            <Button
                                variant="primary"
                                size="sm"
                                onClick={handleSave}
                                disabled={isSaving || !value.trim()}
                            >
                                {isSaving ? 'Saving...' : 'Save Changes'}
                            </Button>
                        </>
                    )}
                </div>
            </div>

            {/* Mobile Save Button - sticky at bottom */}
            <div className="md:hidden fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-neutral-200 shadow-lg z-40">
                <Button
                    variant="primary"
                    size="lg"
                    onClick={handleSave}
                    disabled={isSaving || !value.trim()}
                    className="w-full"
                >
                    {isSaving ? (
                        <div className="flex items-center justify-center space-x-2">
                            <svg className="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                            </svg>
                            <span>Saving...</span>
                        </div>
                    ) : (
                        <div className="flex items-center justify-center space-x-2">
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
                            </svg>
                            <span>{isEditing ? 'Save Changes' : 'Save Note'}</span>
                        </div>
                    )}
                </Button>
            </div>
        </div>
    );
};
