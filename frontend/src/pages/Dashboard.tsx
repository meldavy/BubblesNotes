import React, { useState } from 'react';
import { NoteEditor } from '../components/NoteEditor';

interface Note {
    id: string;
    title?: string;
    content: string;
    created_at: string;
    updated_at: string;
}

export const Dashboard: React.FC = () => {
    const [noteContent, setNoteContent] = useState('');
    const [isSaving, setIsSaving] = useState(false);
    const [notes, setNotes] = useState<Note[]>([]);

    const handleSaveNote = async () => {
        if (!noteContent.trim()) return;
        
        setIsSaving(true);
        try {
            const response = await fetch('/api/v1/notes', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    content: noteContent,
                    title: noteContent.split('\n')[0].substring(0, 100),
                }),
            });

            if (response.ok) {
                const data = await response.json();
                // Add new note to the list
                setNotes(prev => [{
                    id: data.id,
                    content: noteContent,
                    title: noteContent.split('\n')[0].substring(0, 100),
                    created_at: new Date().toISOString(),
                    updated_at: new Date().toISOString(),
                }, ...prev]);
                setNoteContent('');
            } else {
                console.error('Failed to save note:', response.statusText);
            }
        } catch (error) {
            console.error('Error saving note:', error);
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-50">
            {/* Header */}
            <header className="bg-white shadow-sm sticky top-0 z-10">
                <div className="max-w-7xl mx-auto px-4 py-4">
                    <div className="flex items-center justify-between">
                        <h1 className="text-2xl font-bold text-gray-800">Notes</h1>
                        <div className="flex items-center gap-4">
                            {/* Search placeholder */}
                            <input
                                type="text"
                                placeholder="Search notes..."
                                className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                            />
                        </div>
                    </div>
                </div>
            </header>

            <main className="max-w-7xl mx-auto px-4 py-6">
                {/* Quick Note Editor - Sticky at top */}
                <section className="mb-8 bg-white p-4 rounded-xl shadow-sm border border-gray-200">
                    <NoteEditor
                        value={noteContent}
                        onChange={setNoteContent}
                        onSave={handleSaveNote}
                        placeholder="Type a quick note here... (Ctrl+S to save)"
                    />
                </section>

                {/* Notes Grid */}
                <section>
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-lg font-semibold text-gray-700">Your Notes</h2>
                        <span className="text-sm text-gray-500">{notes.length} notes</span>
                    </div>
                    
                    {notes.length === 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            <div className="col-span-full text-center py-12 bg-white rounded-xl shadow-sm border border-gray-200">
                                <svg 
                                    className="mx-auto h-12 w-12 text-gray-400" 
                                    fill="none" 
                                    viewBox="0 0 24 24" 
                                    stroke="currentColor"
                                >
                                    <path 
                                        strokeLinecap="round" 
                                        strokeLinejoin="round" 
                                        strokeWidth={2} 
                                        d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" 
                                    />
                                </svg>
                                <p className="mt-2 text-gray-500">No notes yet</p>
                                <p className="text-sm text-gray-400">Start typing above to create your first note!</p>
                            </div>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {notes.map((note) => (
                                <article 
                                    key={note.id} 
                                    className="bg-white p-4 rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition-shadow cursor-pointer"
                                >
                                    <h3 className="font-semibold text-gray-800 mb-2 truncate">
                                        {note.title || 'Untitled'}
                                    </h3>
                                    <p className="text-sm text-gray-600 line-clamp-3">
                                        {note.content}
                                    </p>
                                    <footer className="mt-3 text-xs text-gray-400">
                                        {new Date(note.updated_at).toLocaleDateString()}
                                    </footer>
                                </article>
                            ))}
                        </div>
                    )}
                </section>
            </main>
        </div>
    );
};
