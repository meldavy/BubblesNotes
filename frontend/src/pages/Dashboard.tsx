import React, { useState, useEffect, useCallback, useRef } from 'react';
import { NoteEditor } from '../components/NoteEditor';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Header } from '../components/Header';
import { UserProfile } from '../components/UserProfile';
import { InfiniteScroll } from '../components/InfiniteScroll';
import { useAuth } from '../contexts/AuthContext';
import { NoteCard, Note } from '../components/NoteCard';

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
    const [noteTags, setNoteTags] = useState<string[]>([]);

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
                // Optimistically update existing note
                const optimisticNote = {
                    id: editingNoteId,
                    userId: '', // placeholder, will be replaced by server response
                    title: noteTitle || noteContent.split('\n')[0].substring(0, 100),
                    content: noteContent,
                    isPublished: false,
                    previewData: undefined,
                    createdAt: Date.now(),
                    updatedAt: Date.now(),
                };
                setNotes(prev => prev.map(n => (n.id === editingNoteId ? optimisticNote : n)));

                const response = await fetch(`/api/v1/notes/${editingNoteId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        content: noteContent,
                        title: noteTitle || noteContent.split('\n')[0].substring(0, 100),
                        tags: noteTags,
                    }),
                });

                if (response.ok) {
                    const updatedNote = await response.json();
                    // Replace optimistic note with server response
                    setNotes(prev => prev.map(n => (n.id === editingNoteId ? updatedNote : n)));
                    // Reset edit mode
                    setEditingNoteId(null);
                    setNoteContent('');
                    setNoteTitle('');
                } else {
                    // Revert optimistic update on failure
                    setNotes(prev => prev.filter(n => n.id !== editingNoteId));
                    throw new Error('Failed to update note');
                }
            } else {
                // Optimistically add a temporary note before server response
                const tempId = Date.now();
                const optimisticNote = {
                    id: tempId,
                    userId: '',
                    title: noteTitle || noteContent.split('\n')[0].substring(0, 100),
                    content: noteContent,
                    isPublished: false,
                    previewData: undefined,
                    createdAt: Date.now(),
                    updatedAt: Date.now(),
                };
                setNotes(prev => [optimisticNote, ...prev]);
                setNoteContent('');
                setNoteTitle('');

                const response = await fetch('/api/v1/notes', {
                      method: 'POST',
                      headers: {
                          'Content-Type': 'application/json',
                      },
                      credentials: 'include',
                      body: JSON.stringify({
                          content: noteContent,
                          title: noteContent.split('\n')[0].substring(0, 100),
                          tags: noteTags,
                      }),
                  });

                  if (response.ok) {
                       const createdNote = await response.json();
                       // Replace temporary note with actual note from server
                       setNotes(prev => prev.map(n => (n.id === tempId ? createdNote : n)));
                       
                       // Clear tags input after successful save
                       setNoteTags([]);
                   } else {
                    // Remove temporary note on failure
                    setNotes(prev => prev.filter(n => n.id !== tempId));
                    throw new Error('Failed to create note');
                }
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : 'An error occurred');
        } finally {
            setIsSaving(false);
        }
    };

    // No separate load/save tags calls needed; tags come with notes

    const handleEditNote = async (note: Note) => {
        setEditingNoteId(note.id);
        setNoteContent(note.content);
        setNoteTitle(note.title || '');
        
        // Load existing tags for this note from embedded field
        setNoteTags(note.tags || []);
        
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    const handleCancelEdit = async () => {
        setEditingNoteId(null);
        setNoteContent('');
        setNoteTitle('');
        setNoteTags([]); // Clear tags when canceling edit
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
                        noteId={editingNoteId}
                        tags={noteTags}
                        onTagChange={setNoteTags}
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
                                    <NoteCard
                                        key={note.id}
                                        note={note}
                                        isEditing={editingNoteId === note.id}
                                        isDeleting={isDeleting === note.id}
                                        onEdit={() => handleEditNote(note)}
                                        onDelete={() => handleDeleteNote(note.id)}
                                    />
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
