import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Button } from './ui/Button';
import { TagInput } from './TagInput';
import { useToastNotifications } from './ui/Toast';
import { useAuth } from '../contexts/AuthContext';
import { MDXEditorComponent } from './MDXEditorComponent';

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
    autoFocus?: boolean;
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
    onTagChange,
    autoFocus = false,
}) => {
    const [saveStatus, setSaveStatus] = useState<'saving' | 'saved' | 'unsaved' | 'error'>('saved');
    const [saveError, setSaveError] = useState<string | null>(null);
    const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const { getAccessToken } = useAuth();
    const toast = useToastNotifications();

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

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Ctrl+S to save
            if (e.ctrlKey && e.key === 's') {
                e.preventDefault();
                handleSave();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [handleSave]);

    return (
        <div className="w-full bg-white rounded-lg border border-neutral-200 shadow-sm overflow-hidden transition-all duration-normal hover:shadow-md md:overflow-visible">
            {/* Editor Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-neutral-200 bg-neutral-50 flex-shrink-0">
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
                </div>
            </div>

            {/* Editor */}
            <div className="p-4">
                <MDXEditorComponent
                    value={value}
                    onChange={onChange}
                    placeholder={placeholder}
                    autoFocus={autoFocus}
                />
            </div>

            {/* Tag Input - placed below content editor */}
            {onTagChange && (
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
            <div className="px-4 py-2 border-t border-neutral-200 bg-neutral-50 flex items-center justify-between flex-shrink-0">
                <span className="text-xs text-neutral-500">{value.length} characters</span>
                <div className="hidden md:flex items-center space-x-3">
                    {saveError && (
                        <span className="text-xs text-error-600">{saveError}</span>
                    )}
                    <span className="text-xs text-neutral-400">Ctrl+S to save</span>
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

            {/* Mobile Save/Cancel Buttons - sticky at bottom */}
            <div className="md:hidden fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-neutral-200 shadow-lg z-50">
                {isEditing && onCancel ? (
                    <div className="flex items-center space-x-3">
                        <Button
                            variant="secondary"
                            size="lg"
                            onClick={onCancel}
                            disabled={isSaving}
                            className="flex-1"
                        >
                            Cancel
                        </Button>
                        <Button
                            variant="primary"
                            size="lg"
                            onClick={handleSave}
                            disabled={isSaving || !value.trim()}
                            className="flex-1"
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
                                'Save Changes'
                            )}
                        </Button>
                    </div>
                ) : onCancel ? (
                    <div className="flex items-center space-x-3">
                        <Button
                            variant="secondary"
                            size="lg"
                            onClick={onCancel}
                            disabled={isSaving}
                            className="flex-1"
                        >
                            Cancel
                        </Button>
                        <Button
                            variant="primary"
                            size="lg"
                            onClick={handleSave}
                            disabled={isSaving || !value.trim()}
                            className="flex-1"
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
                                'Save'
                            )}
                        </Button>
                    </div>
                ) : (
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
                                <span>Save Note</span>
                            </div>
                        )}
                    </Button>
                )}
            </div>
        </div>
    );
};
