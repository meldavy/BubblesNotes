import React, { useState, useEffect, useRef } from 'react';

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

    // Markdown toolbar buttons
    const toolbarButtons = [
        { icon: 'B', action: () => insertMarkdown('**', '**'), label: 'Bold' },
        { icon: 'I', action: () => insertMarkdown('*', '*'), label: 'Italic' },
        { icon: '#', action: () => insertMarkdown('\n# ', ''), label: 'Heading' },
        { icon: '-', action: () => insertMarkdown('\n- ', ''), label: 'List' },
        { icon: '"', action: () => insertMarkdown('\n> ', ''), label: 'Quote' },
        { icon: '`', action: () => insertMarkdown('`', '`'), label: 'Code' },
    ];

    const insertMarkdown = (before: string, after: string) => {
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
            textarea.setSelectionRange(start + before.length, end + before.length);
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
                    <span className="text-sm font-medium text-neutral-700">Quick Note</span>
                </div>
                
                <div className="flex items-center space-x-3">
                    {/* Auto-save indicator */}
                    {autoSaveStatus === 'saving' && (
                        <div className="flex items-center space-x-1.5">
                            <svg className="w-4 h-4 text-primary-500 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                            </svg>
                            <span className="text-xs text-primary-600 font-medium">Saving...</span>
                        </div>
                    )}
                    {autoSaveStatus === 'saved' && value && (
                        <div className="flex items-center space-x-1.5">
                            <svg className="w-4 h-4 text-success-500" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                            </svg>
                            <span className="text-xs text-success-600 font-medium">Saved</span>
                        </div>
                    )}
                    
                    {/* Preview toggle button */}
                    <button
                        onClick={togglePreview}
                        className={`px-3 py-1.5 text-sm font-medium rounded-md transition-all duration-fast ${
                            showPreview 
                                ? 'bg-primary-500 text-white shadow-sm' 
                                : 'bg-neutral-100 text-neutral-700 hover:bg-neutral-200'
                        }`}
                    >
                        {showPreview ? 'Edit' : 'Preview'}
                    </button>
                </div>
            </div>

            {/* Markdown Toolbar (only in edit mode) */}
            {!showPreview && (
                <div className="flex items-center space-x-1 px-4 py-2 border-b border-neutral-200 bg-white">
                    {toolbarButtons.map((btn, index) => (
                        <button
                            key={index}
                            onClick={btn.action}
                            title={btn.label}
                            className="p-1.5 text-sm text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800 rounded transition-colors duration-fast"
                        >
                            {btn.icon}
                        </button>
                    ))}
                </div>
            )}

            {/* Editor or Preview */}
            <div className="relative">
                {!showPreview ? (
                    <textarea
                        ref={textareaRef}
                        className={`w-full h-40 p-4 resize-none transition-all duration-fast outline-none ${
                            isFocused 
                                ? 'border-primary-500 ring-2 ring-primary-100' 
                                : 'border-neutral-200 hover:border-neutral-300'
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
                    <div className="w-full h-40 p-4 border border-neutral-200 bg-white overflow-auto prose prose-sm max-w-none">
                        {value ? (
                            <div className="prose prose-sm max-w-none">
                                {value.split('\n').map((line, index) => (
                                    <p key={index} className="mb-1">{line}</p>
                                ))}
                            </div>
                        ) : (
                            <p className="text-neutral-400 italic">No content to preview</p>
                        )}
                    </div>
                )}
            </div>

            {/* Footer with keyboard shortcuts hint */}
            <div className="px-4 py-2 border-t border-neutral-200 bg-neutral-50 flex items-center justify-between">
                <span className="text-xs text-neutral-500">{value.length} characters</span>
                <span className="text-xs text-neutral-400">Ctrl+S to save • Shift+Enter for new line</span>
            </div>
        </div>
    );
};
