import React, { useState } from 'react';
import { NoteEditor } from '../components/NoteEditor';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';

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
        <div className="min-h-screen bg-neutral-50">
            {/* Header */}
            <header className="bg-white border-b border-neutral-200 shadow-sm sticky top-0 z-sticky transition-all duration-normal">
                <div className="max-w-7xl mx-auto px-4 py-4">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-3">
                            <div className="w-10 h-10 bg-primary-500 rounded-lg flex items-center justify-center shadow-sm">
                                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5H9z" />
                                </svg>
                            </div>
                            <h1 className="text-2xl font-bold text-neutral-800">Notes</h1>
                        </div>
                        
                        <div className="flex items-center space-x-4">
                            {/* Search input */}
                            <div className="relative">
                                <svg 
                                    className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-neutral-400" 
                                    fill="none" 
                                    stroke="currentColor" 
                                    viewBox="0 0 24 24"
                                >
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                </svg>
                                <input
                                    type="text"
                                    placeholder="Search notes..."
                                    className="pl-10 pr-4 py-2 border border-neutral-300 rounded-lg text-sm bg-white text-neutral-700 placeholder-neutral-400 focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all duration-fast w-64"
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </header>

            <main className="max-w-7xl mx-auto px-4 py-6">
                {/* Quick Note Editor - Sticky at top */}
                <section className="mb-8 animate-slide-up">
                    <NoteEditor
                        value={noteContent}
                        onChange={setNoteContent}
                        onSave={handleSaveNote}
                        placeholder="Type a quick note here... (Ctrl+S to save)"
                    />
                </section>

                {/* Notes Grid */}
                <section>
                    <div className="flex items-center justify-between mb-6">
                        <h2 className="text-xl font-semibold text-neutral-800">Your Notes</h2>
                        <span className="text-sm font-medium text-neutral-500 bg-neutral-100 px-3 py-1 rounded-full">
                            {notes.length} notes
                        </span>
                    </div>
                    
                    {notes.length === 0 ? (
                        <div className="bg-white rounded-xl border border-neutral-200 shadow-sm p-12 text-center animate-fade-in">
                            <div className="w-16 h-16 bg-neutral-100 rounded-full flex items-center justify-center mx-auto mb-4">
                                <svg 
                                    className="w-8 h-8 text-neutral-400" 
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
                            </div>
                            <h3 className="text-lg font-semibold text-neutral-800 mb-2">No notes yet</h3>
                            <p className="text-neutral-500 mb-6">Start typing above to create your first note!</p>
                            <Button variant="primary" size="md" onClick={() => document.querySelector('textarea')?.focus()}>
                                Create Your First Note
                            </Button>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {notes.map((note, index) => (
                                <Card 
                                    key={note.id} 
                                    hoverable
                                    className={`animate-slide-up`}
                                >
                                    <article>
                                        <h3 className="font-semibold text-neutral-800 mb-2 line-clamp-1">
                                            {note.title || 'Untitled'}
                                        </h3>
                                        <p className="text-sm text-neutral-600 leading-relaxed line-clamp-3">
                                            {note.content}
                                        </p>
                                        <footer className="mt-4 pt-3 border-t border-neutral-200 flex items-center justify-between">
                                            <time className="text-xs text-neutral-400">
                                                {new Date(note.updated_at).toLocaleDateString('en-US', {
                                                    month: 'short',
                                                    day: 'numeric',
                                                    year: 'numeric'
                                                })}
                                            </time>
                                            <div className="flex items-center space-x-2">
                                                <button 
                                                    className="p-1.5 text-neutral-400 hover:text-primary-600 hover:bg-primary-50 rounded transition-colors duration-fast"
                                                    title="Edit note"
                                                >
                                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                                    </svg>
                                                </button>
                                                <button 
                                                    className="p-1.5 text-neutral-400 hover:text-error-600 hover:bg-error-50 rounded transition-colors duration-fast"
                                                    title="Delete note"
                                                >
                                                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                    </svg>
                                                </button>
                                            </div>
                                        </footer>
                                    </article>
                                </Card>
                            ))}
                        </div>
                    )}
                </section>
            </main>
        </div>
    );
};
