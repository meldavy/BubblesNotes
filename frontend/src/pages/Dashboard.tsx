import React from 'react';
import { NoteEditor } from '../components/NoteEditor';

export const Dashboard: React.FC = () => {
    const [noteContent, setNoteContent] = React.useState('');

    return (
        <div className="min-h-screen bg-gray-50">
            <header className="bg-white shadow-sm sticky top-0 z-10">
                <div className="max-w-7xl mx-auto px-4 py-4">
                    <h1 className="text-2xl font-bold text-gray-800">Notes</h1>
                </div>
            </header>

            <main className="max-w-7xl mx-auto px-4 py-6">
                {/* Quick Note Editor - Sticky at top */}
                <div className="mb-6">
                    <NoteEditor
                        value={noteContent}
                        onChange={setNoteContent}
                        placeholder="Type a quick note here... (Ctrl+S to save)"
                    />
                </div>

                {/* Notes Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {/* Note cards will be rendered here */}
                    <div className="col-span-full text-center py-8 text-gray-500">
                        No notes yet. Create your first note above!
                    </div>
                </div>
            </main>
        </div>
    );
};
