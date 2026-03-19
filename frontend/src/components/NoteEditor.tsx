import React, { useState, useEffect, useRef, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Button } from './ui/Button';

interface NoteEditorProps {
    value: string;
    onChange: (value: string) => void;
    onSave?: () => Promise<void>;
    placeholder?: string;
    isEditing?: boolean;
    onCancel?: () => void;
    isSaving?: boolean;
    noteId?: number | null;
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
    noteId = null
}) => {
    const [isFocused, setIsFocused] = useState(false);
    const [viewMode, setViewMode] = useState<'edit' | 'preview' | 'split'>('edit');
    const [saveStatus, setSaveStatus] = useState<'saving' | 'saved' | 'unsaved' | 'error'>('saved');
    const [saveError, setSaveError] = useState<string | null>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

    // Memoized handleSave to prevent re-creation on each render
    const handleSave = useCallback(async () => {
        if (!value.trim()) return;

        setSaveStatus('saving');
        setSaveError(null);

        try {
            if (onSave) {
                await onSave();
            }
            setSaveStatus('saved');
        } catch (err) {
            setSaveStatus('error');
            setSaveError(err instanceof Error ? err.message : 'Save failed');
        }
    }, [value, onSave]);

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
        { icon: '[ ]', action: () => insertMarkdown('\n- [ ] ', '', 0), label: 'Task List', shortcut: 'Ctrl+T' },
        { icon: '🔗', action: () => insertMarkdown('[]()', '', -1), label: 'Link', shortcut: 'Ctrl+L' },
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

    return (
        <div className="w-full bg-white rounded-lg border border-neutral-200 shadow-sm overflow-hidden transition-all duration-normal hover:shadow-md">
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
                            className="p-1.5 text-sm text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800 rounded transition-colors duration-fast"
                        >
                            {btn.icon}
                        </button>
                    ))}
                </div>
            )}

            {/* Editor or Preview */}
            <div className="relative">
                {(viewMode === 'edit' || viewMode === 'split') && (
                    <div className={`${viewMode === 'split' ? 'w-1/2 float-left border-r border-neutral-200' : 'w-full'}`}>
                        <textarea
                            ref={textareaRef}
                            className="w-full h-64 p-4 resize-none transition-all duration-fast outline-none border-none bg-white font-mono text-sm"
                            value={value}
                            onChange={(e) => {
                                onChange(e.target.value);
                            }}
                            onFocus={() => setIsFocused(true)}
                            onBlur={() => setIsFocused(false)}
                            placeholder={placeholder}
                            spellCheck={false}
                        />
                    </div>
                )}
                
                {(viewMode === 'preview' || viewMode === 'split') && (
                    <div className={`${viewMode === 'split' ? 'w-1/2' : 'w-full'} ${viewMode === 'split' ? 'float-left' : ''}`}>
                        <div className="w-full h-64 p-4 overflow-auto bg-neutral-50">
                            {value ? (
                                <ReactMarkdown
                                    remarkPlugins={[remarkGfm]}
                                    rehypePlugins={[rehypeSanitize]}
                                    components={{
                                        // Custom component styling
                                        h1: ({ children }) => <h1 className="text-2xl font-bold text-neutral-800 mb-4">{children}</h1>,
                                        h2: ({ children }) => <h2 className="text-xl font-semibold text-neutral-700 mb-3">{children}</h2>,
                                        h3: ({ children }) => <h3 className="text-lg font-semibold text-neutral-700 mb-2">{children}</h3>,
                                        p: ({ children }) => <p className="mb-3 text-neutral-700 leading-relaxed">{children}</p>,
                                        ul: ({ children }) => <ul className="list-disc list-inside mb-3 space-y-1">{children}</ul>,
                                        ol: ({ children }) => <ol className="list-decimal list-inside mb-3 space-y-1">{children}</ol>,
                                        li: ({ children }) => <li className="text-neutral-600">{children}</li>,
                                        blockquote: ({ children }) => (
                                            <blockquote className="border-l-4 border-primary-300 pl-4 my-3 italic text-neutral-600 bg-white rounded-r">{children}</blockquote>
                                        ),
                                        code: ({ children, className }) => (
                                            <code className="bg-neutral-200 px-1.5 py-0.5 rounded text-sm font-mono text-neutral-800">{children}</code>
                                        ),
                                        pre: ({ children }) => (
                                            <pre className="bg-neutral-800 text-neutral-100 p-4 rounded-lg overflow-x-auto my-3">
                                                {children}
                                            </pre>
                                        ),
                                        a: ({ href, children }) => (
                                            <a href={href} className="text-primary-600 hover:text-primary-700 underline" target="_blank" rel="noopener noreferrer">
                                                {children}
                                            </a>
                                        ),
                                        table: ({ children }) => (
                                            <div className="overflow-x-auto my-3">
                                                <table className="min-w-full border-collapse border border-neutral-300">{children}</table>
                                            </div>
                                        ),
                                        th: ({ children }) => (
                                            <th className="border border-neutral-300 px-4 py-2 bg-neutral-100 font-semibold">{children}</th>
                                        ),
                                        td: ({ children }) => (
                                            <td className="border border-neutral-300 px-4 py-2">{children}</td>
                                        ),
                                    }}
                                >
                                    {value}
                                </ReactMarkdown>
                            ) : (
                                <p className="text-neutral-400 italic">No content to preview</p>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* Footer with keyboard shortcuts hint and action buttons */}
            <div className="px-4 py-2 border-t border-neutral-200 bg-neutral-50 flex items-center justify-between">
                <span className="text-xs text-neutral-500">{value.length} characters</span>
                <div className="flex items-center space-x-3">
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
        </div>
    );
};
