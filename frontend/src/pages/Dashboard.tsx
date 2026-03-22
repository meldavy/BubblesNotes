import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Menu, Transition } from '@headlessui/react';
import { ChevronDownIcon } from '@heroicons/react/24/solid';
import { NoteEditor } from '../components/NoteEditor';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Header } from '../components/Header';
import { UserProfile } from '../components/UserProfile';
import { InfiniteScroll } from '../components/InfiniteScroll';
import { SearchBar } from '../components/ui/SearchBar';
import { useAuth } from '../contexts/AuthContext';
import { NoteCard, Note } from '../components/NoteCard';
import { fetchWithAuth } from '../api/apiClient';

export const Dashboard: React.FC = () => {
    const { isAuthenticated, getAccessToken } = useAuth();
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
    
    // Helper function to get headers with JWT token
    const getAuthHeaders = (includeContentType: boolean = true): Record<string, string> => {
        const headers: Record<string, string> = {};
        const token = getAccessToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
        if (includeContentType) {
            headers['Content-Type'] = 'application/json';
        }
        return headers;
    };
    
    // Search state
    const [searchQuery, setSearchQuery] = useState('');
    const [isSearching, setIsSearching] = useState(false);
    const [allTags, setAllTags] = useState<string[]>([]);
    const [selectedTag, setSelectedTag] = useState<string | null>(null);
    const [showLoading, setShowLoading] = useState(false);
    const loadingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    // Refs to access latest values in fetchNotes without recreating the callback
    const searchQueryRef = useRef<string>(searchQuery);
    const selectedTagRef = useRef<string | null>(selectedTag);
    

    const fetchNotes = useCallback(async (isInitialLoad: boolean = false, append: boolean = false) => {
        // Prevent duplicate initial loads only on first render
        if (isInitialLoad && hasInitialLoaded.current) {
            return;
        }
        if (isInitialLoad) {
            hasInitialLoaded.current = true;
        }

        try {
            // Set loading state for initial load or refresh (not append)
            // Always show loading when fetching (except for append/pagination)
            if (!append) {
                setIsLoading(true);
                // Show loading skeleton only after a short delay to prevent flicker
                loadingTimeoutRef.current = setTimeout(() => {
                    setShowLoading(true);
                }, 200);
            } else {
                setIsLoadingMore(true);
            }
            setError(null);

            // Determine which API to call based on search state
            const currentSearchQuery = searchQueryRef.current;
            const currentSelectedTag = selectedTagRef.current;
            
            if (currentSearchQuery.trim()) {
                // Use search API
                const response = await fetchWithAuth(
                    '/api/v1/search',
                    {
                        method: 'POST',
                        headers: getAuthHeaders(),
                        body: JSON.stringify({
                            query: currentSearchQuery.trim(),
                            limit: 20,
                            cursor: nextCursor !== null ? nextCursor : undefined
                        })
                    },
                    getAccessToken
                );

                if (!response.ok) {
                    throw new Error('Failed to search notes');
                }

                const data = await response.json();
                const searchResults = data.results || [];

                if (append) {
                    setNotes(prev => [...prev, ...searchResults]);
                } else {
                    // Replace notes for search or initial load
                    setNotes(searchResults);
                }

                // Update cursor for search results
                if (searchResults.length < 20) {
                    setNextCursor(null);
                } else if (searchResults.length > 0) {
                    const minId = Math.min(...searchResults.map((n: Note) => n.id));
                    setNextCursor(minId);
                }
            } else if (currentSelectedTag) {
                // Use tag filter API
                const url = new URL('/api/v1/notes', window.location.origin);
                url.searchParams.set('limit', '20');
                url.searchParams.set('tag', currentSelectedTag);
                
                if (nextCursor !== null && !isInitialLoad) {
                    url.searchParams.set('cursor', nextCursor.toString());
                }

                const response = await fetchWithAuth(
                    url.toString(),
                    {
                        headers: getAuthHeaders(false),
                    },
                    getAccessToken
                );

                if (!response.ok) {
                    throw new Error('Failed to fetch notes by tag');
                }

                const data = await response.json();

                if (append) {
                    setNotes(prev => [...prev, ...data]);
                } else {
                    // Replace notes for tag filter or initial load
                    setNotes(data);
                }

                if (data.length < 20) {
                    setNextCursor(null);
                } else if (data.length > 0) {
                    const minId = Math.min(...data.map((n: Note) => n.id));
                    setNextCursor(minId);
                }
            } else {
                // Use regular notes API
                const url = new URL('/api/v1/notes', window.location.origin);
                const limit = 20;
                url.searchParams.set('limit', limit.toString());
                
                if (nextCursor !== null && !isInitialLoad) {
                    url.searchParams.set('cursor', nextCursor.toString());
                }

                const response = await fetchWithAuth(
                    url.toString(),
                    {
                        headers: getAuthHeaders(false),
                    },
                    getAccessToken
                );

                if (!response.ok) {
                    throw new Error('Failed to fetch notes');
                }

                const data = await response.json();
                
                if (append) {
                    setNotes(prev => [...prev, ...data]);
                } else {
                    // Replace notes for initial load
                    setNotes(data);
                }
                
                if (data.length < limit) {
                    setNextCursor(null);
                } else if (data.length > 0) {
                    const minId = Math.min(...data.map((n: Note) => n.id));
                    setNextCursor(minId);
                }
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : 'An error occurred');
        } finally {
            // Clear any pending timeout
            if (loadingTimeoutRef.current) {
                clearTimeout(loadingTimeoutRef.current);
                loadingTimeoutRef.current = null;
            }
            // Set loading false for initial load or refresh (not append)
            if (!append) {
                setIsLoading(false);
                setShowLoading(false);
            } else {
                setIsLoadingMore(false);
            }
        }
    }, [nextCursor]);

    // Fetch tags for the tag filter dropdown
    const fetchTags = useCallback(async () => {
        try {
            const token = getAccessToken();
            if (!token) {
                console.warn('No access token available, skipping tags fetch');
                return;
            }
            const response = await fetchWithAuth(
                '/api/v1/tags',
                {
                    headers: getAuthHeaders(false),
                },
                getAccessToken
            );
            if (response.ok) {
                const data = await response.json();
                setAllTags(data);
            }
        } catch (err) {
            console.error('Failed to fetch tags:', err);
        }
    }, [getAccessToken]);

    // Trigger search when searchQuery changes (debounced)
    useEffect(() => {
        if (!isAuthenticated) {
            return;
        }
        
        const debounceTimer = setTimeout(() => {
            // Use false for isInitialLoad since search is a refresh, not initial load
            fetchNotes(false);
        }, 300); // 300ms debounce to avoid excessive API calls
        
        return () => {
            clearTimeout(debounceTimer);
        };
    }, [searchQuery, selectedTag, isAuthenticated]);

    useEffect(() => {
        if (isAuthenticated && !hasInitialLoaded.current) {
            fetchNotes(true);
            fetchTags();
        }
    }, [isAuthenticated, fetchTags]);

    // Reset search when authentication changes
    useEffect(() => {
        if (!isAuthenticated) {
            setSearchQuery('');
            setSelectedTag(null);
            setNotes([]);
            setNextCursor(null);
        }
    }, [isAuthenticated]);

    // Keep refs in sync with state values
    useEffect(() => {
        searchQueryRef.current = searchQuery;
        selectedTagRef.current = selectedTag;
    }, [searchQuery, selectedTag]);

    // Cleanup loading timeout on unmount
    useEffect(() => {
        return () => {
            if (loadingTimeoutRef.current) {
                clearTimeout(loadingTimeoutRef.current);
            }
        };
    }, []);

    // Handle search submission
    const handleSearch = (query: string) => {
        setSearchQuery(query);
        setSelectedTag(null); // Clear tag filter when searching
        setNextCursor(null); // Reset pagination
    };

    // Handle tag selection
    const handleTagSelect = (tag: string | null) => {
        setSelectedTag(tag);
        setSearchQuery(tag || ''); // Use tag as search query to trigger search API
        setNextCursor(null); // Reset pagination
    };

    // Clear search and filter
    const handleClearSearch = () => {
        setSearchQuery('');
        setSelectedTag(null);
        setNextCursor(null);
    };

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

                const response = await fetchWithAuth(
                    `/api/v1/notes/${editingNoteId}`,
                    {
                        method: 'PUT',
                        headers: getAuthHeaders(),
                        body: JSON.stringify({
                            content: noteContent,
                            title: noteTitle || noteContent.split('\n')[0].substring(0, 100),
                            tags: noteTags,
                        }),
                    },
                    getAccessToken
                );

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

                const response = await fetchWithAuth(
                    '/api/v1/notes',
                    {
                        method: 'POST',
                        headers: getAuthHeaders(),
                        body: JSON.stringify({
                            content: noteContent,
                            title: noteContent.split('\n')[0].substring(0, 100),
                            tags: noteTags,
                        }),
                    },
                    getAccessToken
                );

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
            const response = await fetchWithAuth(
                `/api/v1/notes/${noteId}`,
                {
                    method: 'DELETE',
                    headers: getAuthHeaders(false),
                },
                getAccessToken
            );

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
    }, [nextCursor, isLoadingMore]);

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

            <main className="max-w-7xl mx-auto px-4 py-6 pb-20 md:pb-6">
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
                    {/* Search and Filter Bar */}
                    <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-6 gap-4">
                        <h2 className="text-xl font-semibold text-neutral-800">
                            {searchQuery ? `Search Results for "${searchQuery}"` : selectedTag ? `Notes tagged "${selectedTag}"` : 'Your Notes'}
                        </h2>
                        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                            {/* Search Bar */}
                            <div className="w-full sm:w-72">
                                <SearchBar
                                    placeholder="Search notes..."
                                    value={searchQuery}
                                    onChange={setSearchQuery}
                                    onSearch={handleSearch}
                                    suggestions={allTags.filter(tag => tag.toLowerCase().includes(searchQuery.toLowerCase())).slice(0, 5)}
                                    showClear={true}
                                />
                            </div>
                            {/* Tag Filter Dropdown */}
                            <Menu as="div" className="relative">
                                <Menu.Button className="
                                    flex items-center justify-between
                                    h-10 px-4 py-0 leading-none
                                    rounded-lg
                                    border border-neutral-300
                                    bg-white
                                    text-sm text-neutral-700
                                    focus:outline-none
                                    focus:ring-2
                                    focus:ring-primary-500
                                    focus:border-primary-500
                                    transition-all
                                    duration-[150ms]
                                    ease-out
                                    cursor-pointer
                                    hover:border-neutral-400
                                ">
                                    <span className="truncate">
                                        {selectedTag || 'All Tags'}
                                    </span>
                                    <ChevronDownIcon className="w-4 h-4 text-neutral-500 ml-2 flex-shrink-0" />
                                </Menu.Button>
                                <Transition
                                    enter="transition ease-out duration-100"
                                    enterFrom="transform opacity-0 scale-95"
                                    enterTo="transform opacity-100 scale-100"
                                    leave="transition ease-in duration-75"
                                    leaveFrom="transform opacity-100 scale-100"
                                    leaveTo="transform opacity-0 scale-0"
                                >
                                    <Menu.Items className="
                                        absolute right-0 mt-2
                                        w-48 origin-top-right
                                        bg-white rounded-lg
                                        shadow-lg ring-1 ring-black ring-opacity-5
                                        focus:outline-none z-50
                                    ">
                                        <div className="py-1">
                                            <Menu.Item>
                                                {() => (
                                                    <button
                                                        onClick={() => handleTagSelect(null)}
                                                        className="
                                                            w-full text-left
                                                            px-4 py-2
                                                            text-sm
                                                            text-neutral-700
                                                            hover:bg-neutral-100
                                                            focus:bg-neutral-100
                                                            focus:outline-none
                                                        "
                                                    >
                                                        All Tags
                                                    </button>
                                                )}
                                            </Menu.Item>
                                            {allTags.map(tag => (
                                                <Menu.Item key={tag}>
                                                    {() => (
                                                        <button
                                                            onClick={() => handleTagSelect(tag)}
                                                            className={`
                                                                w-full text-left
                                                                px-4 py-2
                                                                text-sm
                                                                focus:outline-none
                                                                ${selectedTag === tag
                                                                    ? 'bg-primary-50 text-primary-700'
                                                                    : 'text-neutral-700 hover:bg-neutral-100 focus:bg-neutral-100'
                                                                }
                                                            `}
                                                        >
                                                            {tag}
                                                        </button>
                                                    )}
                                                </Menu.Item>
                                            ))}
                                        </div>
                                    </Menu.Items>
                                </Transition>
                            </Menu>
                            {/* Clear Filter Button */}
                            {(searchQuery || selectedTag) && (
                                <Button variant="ghost" size="sm" onClick={handleClearSearch}>
                                    <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                    Clear
                                </Button>
                            )}
                        </div>
                    </div>

                    {/* Active Filter Display */}
                    {(searchQuery || selectedTag) && (
                        <div className="flex items-center flex-wrap gap-2 mb-4">
                            <span className="text-sm text-neutral-500">Active filter:</span>
                            {searchQuery && (
                                <span className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium bg-primary-50 text-primary-700 border border-primary-200">
                                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                    </svg>
                                    {searchQuery}
                                </span>
                            )}
                            {selectedTag && (
                                <span className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium bg-accent-purple-50 text-accent-purple-700 border border-accent-purple-200">
                                    <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                                    </svg>
                                    {selectedTag}
                                </span>
                            )}
                        </div>
                    )}

                    <div className="flex items-center justify-between mb-4">
                        <span className="text-sm font-medium text-neutral-500 bg-neutral-100 px-3 py-1 rounded-full">
                            {notes.length} {notes.length === 1 ? 'note' : 'notes'}
                        </span>
                    </div>

                    {/* Loading State - only show skeleton after 200ms to prevent flicker */}
                    {isLoading && showLoading && (
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
