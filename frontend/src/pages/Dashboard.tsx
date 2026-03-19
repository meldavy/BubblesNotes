import React, { useState, useEffect, useCallback, useRef } from 'react';
import { NoteEditor } from '../components/NoteEditor';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Header } from '../components/Header';
import { UserProfile } from '../components/UserProfile';
import { InfiniteScroll } from '../components/InfiniteScroll';
import { useAuth } from '../contexts/AuthContext';

interface Note {
    id: number;
    userId: string;
    title?: string;
    content: string;
    isPublished: boolean;
    createdAt: number;
    updatedAt: number;
}

export const Dashboard: React.FC = () => {
    const { isAuthenticated } = useAuth();
    const [noteContent, setNoteContent] = useState('');
    const [noteTitle, setNoteTitle] = useState('');
    const [isSaving, setIsSaving] = useState(false);
    const [notes, setNotes] = useState<Note[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [nextCursor, setNextCursor] = useState<number | null>(null);
    const [isLoadingMore, setIsLoadingMore] = useState(false);
    const [editingNoteId, setEditingNoteId] = useState<number | null>(null);
    const [isDeleting, setIsDeleting] = useState<number | null>(null);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const touchStartY = React.useRef<number | null>(null);
    const hasInitialLoaded = useRef(false);

    const fetchNotes = useCallback(async (isInitialLoad: boolean = false, append: boolean = false) => {
        // Prevent duplicate initial loads
        if (isInitialLoad && hasInitialLoaded.current) {
            return;
        }
        if (isInitialLoad) {
            hasInitialLoaded.current = true;
        }

        try {
            if (isInitialLoad) {
                setIsLoading(true);
            } else if (!append) {
                setIsLoadingMore(true);
            }
            setError(null);

            const url = new URL('/api/v1/notes', window.location.origin);
            const limit = 20; // Use smaller limit for pagination
            url.searchParams.set('limit', limit.toString());
            
            // Add cursor if we have one (for pagination)
            if (nextCursor !== null && !isInitialLoad) {
                url.searchParams.set('cursor', nextCursor.toString());
            }

            const response = await fetch(url.toString(), {
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error('Failed to fetch notes');
            }

            const data = await response.json();
            
            if (append) {
                // Append new notes to existing list
                setNotes(prev => [...prev, ...data]);
            } else if (isInitialLoad) {
                // Replace all notes on initial load
                setNotes(data);
            }
            
            // Update cursor for next page
            // If we got fewer notes than the limit, we've reached the end
            if (data.length < limit) {
                setNextCursor(null); // No more pages
            } else if (data.length > 0) {
                // Use the smallest ID from this batch as the cursor for next page
                const minId = Math.min(...data.map((n: Note) => n.id));
                setNextCursor(minId);
            }
        } catch (err) {
            if (isInitialLoad) {
                setError(err instanceof Error ? err.message : 'An error occurred');
            } else {
                setError(err instanceof Error ? err.message : 'An error occurred');
            }
        } finally {
            if (isInitialLoad) {
                setIsLoading(false);
            } else {
                setIsLoadingMore(false);
            }
        }
    }, [nextCursor]);

    useEffect(() => {
        if (isAuthenticated && !hasInitialLoaded.current) {
            fetchNotes(true);
        }
    }, [isAuthenticated, fetchNotes]);

    const handleSaveNote = async () => {
        if (!noteContent.trim()) return;
        
        setIsSaving(true);
        try {
            if (editingNoteId !== null) {
                // Update existing note
                const response = await fetch(`/api/v1/notes/${editingNoteId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        content: noteContent,
                        title: noteTitle || noteContent.split('\n')[0].substring(0, 100),
                    }),
                });

                if (response.ok) {
                    const updatedNote = await response.json();
                    // Update the note in the list
                    setNotes(prev => prev.map(n =>
                        n.id === editingNoteId ? updatedNote : n
                    ));
                    // Reset edit mode
                    setEditingNoteId(null);
                    setNoteContent('');
                    setNoteTitle('');
                } else {
                    throw new Error('Failed to update note');
                }
            } else {
                // Create new note
                const response = await fetch('/api/v1/notes', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    credentials: 'include',
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
                        userId: '',
                        content: noteContent,
                        title: noteContent.split('\n')[0].substring(0, 100),
                        isPublished: true,
                        createdAt: Date.now(),
                        updatedAt: Date.now(),
                    }, ...prev]);
                    setNoteContent('');
                    setNoteTitle('');
                } else {
                    throw new Error('Failed to create note');
                }
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : 'An error occurred');
        } finally {
            setIsSaving(false);
        }
    };

    const handleEditNote = (note: Note) => {
        setEditingNoteId(note.id);
        setNoteContent(note.content);
        setNoteTitle(note.title || '');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    const handleCancelEdit = () => {
        setEditingNoteId(null);
        setNoteContent('');
        setNoteTitle('');
    };

    const handleDeleteNote = async (noteId: number) => {
        // Confirm deletion
        if (!window.confirm('Are you sure you want to delete this note? This action cannot be undone.')) {
            return;
        }

        const previousNotes = [...notes];
        setIsDeleting(noteId);
        setNotes(prev => prev.filter(n => n.id !== noteId));

        try {
            const response = await fetch(`/api/v1/notes/${noteId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error('Failed to delete note');
            }
        } catch (err) {
            // Restore the note on error
            setNotes(previousNotes);
            setError(err instanceof Error ? err.message : 'Failed to delete note');
        } finally {
            setIsDeleting(null);
        }
    };

    const handleRefresh = useCallback(async () => {
        if (isRefreshing) return;
        setIsRefreshing(true);
        try {
            await fetchNotes();
        } finally {
            setIsRefreshing(false);
        }
    }, [isRefreshing]);

    const handleLoadMore = useCallback(() => {
        if (nextCursor !== null && !isLoadingMore) {
            fetchNotes(false, true);
        }
    }, [nextCursor, isLoadingMore, fetchNotes]);

    const handleTouchStart = (e: React.TouchEvent) => {
        if (window.scrollY === 0) {
            touchStartY.current = e.touches[0].clientY;
        }
    };

    const handleTouchEnd = (e: React.TouchEvent) => {
        if (touchStartY.current === null) return;
        
        const touchEndY = e.changedTouches[0].clientY;
        const diff = touchEndY - touchStartY.current;
        
        // If pulled down more than 80px, trigger refresh
        if (diff > 80) {
            handleRefresh();
        }
        
        touchStartY.current = null;
    };

    return (
        <div
            className="min-h-screen bg-neutral-50"
            onTouchStart={handleTouchStart}
            onTouchEnd={handleTouchEnd}
        >
            {/* Pull-to-refresh indicator */}
            {isRefreshing && (
                <div className="fixed top-0 left-0 right-0 z-50 flex justify-center pt-2">
                    <div className="bg-primary-600 text-white px-4 py-2 rounded-b-lg flex items-center space-x-2">
                        <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                        <span className="text-sm">Refreshing...</span>
                    </div>
                </div>
            )}
            
            {/* Header */}
            <Header title="BubblesNotes" showAuth={false} />

            <main className="max-w-7xl mx-auto px-4 py-6">
                {/* Quick Note Editor - Sticky at top */}
                <section className="mb-8 animate-slide-up">
                    <NoteEditor
                        value={noteContent}
                        onChange={setNoteContent}
                        onSave={handleSaveNote}
                        placeholder={editingNoteId !== null ? "Edit your note here... (Ctrl+S to save)" : "Type a quick note here... (Ctrl+S to save)"}
                        isEditing={editingNoteId !== null}
                        onCancel={editingNoteId !== null ? handleCancelEdit : undefined}
                        isSaving={isSaving}
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

                    {/* Loading State */}
                    {isLoading && (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {[...Array(6)].map((_, i) => (
                                <Card key={i} className="animate-pulse">
                                    <div className="h-4 bg-neutral-200 rounded w-3/4 mb-3"></div>
                                    <div className="h-3 bg-neutral-200 rounded w-full mb-2"></div>
                                    <div className="h-3 bg-neutral-200 rounded w-2/3"></div>
                                </Card>
                            ))}
                        </div>
                    )}

                    {/* Error State */}
                    {!isLoading && error && (
                        <div className="bg-error-50 border border-error-200 rounded-lg p-4 text-center">
                            <p className="text-error-700 mb-2">{error}</p>
                            <Button variant="secondary" size="sm" onClick={() => fetchNotes()}>
                                Retry
                            </Button>
                        </div>
                    )}

                    {/* Empty State */}
                    {!isLoading && !error && notes.length === 0 ? (
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
                        <>
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                {notes.map((note) => (
                                    <Card
                                        key={note.id}
                                        hoverable
                                        className={`animate-slide-up transition-opacity duration-200 ${
                                            editingNoteId === note.id ? 'opacity-50 pointer-events-none' : ''
                                        }`}
                                    >
                                        <article>
                                            <div className="flex items-start justify-between mb-2">
                                                <h3 className="font-semibold text-neutral-800 line-clamp-1">
                                                    {note.title || 'Untitled'}
                                                </h3>
                                                {editingNoteId === note.id && (
                                                    <span className="text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full">
                                                        Editing
                                                    </span>
                                                )}
                                            </div>
                                            <p className="text-sm text-neutral-600 leading-relaxed line-clamp-3">
                                                {note.content}
                                            </p>
                                            <footer className="mt-4 pt-3 border-t border-neutral-200 flex items-center justify-between">
                                                <time className="text-xs text-neutral-400">
                                                    {new Date(note.updatedAt).toLocaleDateString('en-US', {
                                                        month: 'short',
                                                        day: 'numeric',
                                                        year: 'numeric'
                                                    })}
                                                </time>
                                                <div className="flex items-center space-x-1">
                                                    <button
                                                        onClick={() => handleEditNote(note)}
                                                        disabled={isDeleting === note.id}
                                                        className="p-1.5 text-neutral-400 hover:text-primary-600 hover:bg-primary-50 rounded transition-colors duration-fast disabled:opacity-50"
                                                        title="Edit note"
                                                    >
                                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                                        </svg>
                                                    </button>
                                                    <button
                                                        onClick={() => handleDeleteNote(note.id)}
                                                        disabled={editingNoteId === note.id}
                                                        className={`p-1.5 rounded transition-colors duration-fast disabled:opacity-50 ${
                                                            isDeleting === note.id
                                                                ? 'text-primary-600 bg-primary-50 animate-pulse'
                                                                : 'text-neutral-400 hover:text-error-600 hover:bg-error-50'
                                                        }`}
                                                        title="Delete note"
                                                    >
                                                        {isDeleting === note.id ? (
                                                            <svg className="w-4 h-4 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                                            </svg>
                                                        ) : (
                                                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                            </svg>
                                                        )}
                                                    </button>
                                                </div>
                                            </footer>
                                        </article>
                                    </Card>
                                ))}
                            </div>
                            {/* Load more indicator */}
                            {nextCursor !== null && (
                                <InfiniteScroll
                                    onLoadMore={handleLoadMore}
                                    isLoading={isLoadingMore}
                                    hasMore={nextCursor !== null}
                                />
                            )}
                        </>
                    )}
                </section>
            </main>
        </div>
    );
};
