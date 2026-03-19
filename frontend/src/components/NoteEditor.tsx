import React, { useState, useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';

interface NoteEditorProps {
    value: string;
    onChange: (value: string) => void;
    onSave?: () => void;
    placeholder?: string;
}

export const NoteEditor: React.FC<NoteEditorProps> = ({ 
    value, 
    onChange, 
    onSave,
    placeholder = "Type your note here..." 
}) => {
    const [isFocused, setIsFocused] = useState(false);
    const [showPreview, setShowPreview] = useState(false);
    const [autoSaveStatus, setAutoSaveStatus] = useState<'saving' | 'saved' | 'unsaved'>('unsaved');
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Auto-save indicator effect
    useEffect(() => {
        if (value) {
            setAutoSaveStatus('unsaved');
            const timer = setTimeout(() => {
                setAutoSaveStatus('saving');
                // Simulate auto-save debounce
                setTimeout(() => {
                    setAutoSaveStatus('saved');
                }, 300);
            }, 1000);
            return () => clearTimeout(timer);
        } else {
            setAutoSaveStatus('unsaved');
        }
    }, [value]);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Ctrl+S to save
            if (e.ctrlKey && e.key === 's') {
                e.preventDefault();
                handleSave();
            }
            // Enter without modifier to finish editing (blur)
            if (e.key === 'Enter' && !e.shiftKey && !isFocused) {
                e.preventDefault();
                textareaRef.current?.focus();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isFocused, value]);

    const handleSave = () => {
        setAutoSaveStatus('saving');
        if (onSave) {
            onSave();
        }
        setTimeout(() => {
            setAutoSaveStatus('saved');
        }, 300);
    };

    const togglePreview = () => {
        setShowPreview(!showPreview);
    };

    return (
        <div className="w-full">
            {/* Editor Header */}
            <div className="flex items-center justify-between mb-2">
                <span className="text-sm text-gray-500">Quick Note</span>
                <div className="flex items-center gap-3">
                    {/* Auto-save indicator */}
                    {autoSaveStatus === 'saving' && (
                        <span className="text-xs text-blue-500 animate-pulse">Saving...</span>
                    )}
                    {autoSaveStatus === 'saved' && value && (
                        <span className="text-xs text-green-500">Saved</span>
                    )}
                    {/* Preview toggle button */}
                    <button
                        onClick={togglePreview}
                        className={`px-3 py-1 text-sm rounded transition-colors ${
                            showPreview 
                                ? 'bg-blue-500 text-white' 
                                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                        }`}
                    >
                        {showPreview ? 'Edit' : 'Preview'}
                    </button>
                </div>
            </div>

            {/* Editor or Preview */}
            <div className="relative">
                {!showPreview ? (
                    <textarea
                        ref={textareaRef}
                        className={`w-full h-40 p-4 border rounded-lg resize-none transition-all ${
                            isFocused 
                                ? 'border-blue-500 ring-2 ring-blue-200' 
                                : 'border-gray-300 hover:border-gray-400'
                        } bg-white`}
                        value={value}
                        onChange={(e) => {
                            onChange(e.target.value);
                            setAutoSaveStatus('unsaved');
                        }}
                        onFocus={() => setIsFocused(true)}
                        onBlur={() => setIsFocused(false)}
                        placeholder={placeholder}
                    />
                ) : (
                    <div className="w-full h-40 p-4 border border-gray-300 rounded-lg bg-white overflow-auto">
                        {value ? (
                            <ReactMarkdown className="prose prose-sm max-w-none">
                                {value}
                            </ReactMarkdown>
                        ) : (
                            <p className="text-gray-400 italic">No content to preview</p>
                        )}
                    </div>
                )}
            </div>

            {/* Footer with keyboard shortcuts hint */}
            <div className="mt-2 text-xs text-gray-400 flex items-center justify-between">
                <span>{value.length} characters</span>
                <span>Ctrl+S to save • Shift+Enter for new line</span>
            </div>
        </div>
    );
};
