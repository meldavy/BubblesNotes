import React from 'react';

interface NoteEditorProps {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
}

export const NoteEditor: React.FC<NoteEditorProps> = ({ 
    value, 
    onChange, 
    placeholder = "Type your note here..." 
}) => {
    return (
        <textarea
            className="w-full h-32 p-4 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
        />
    );
};
