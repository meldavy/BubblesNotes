/**
 * MDXEditor Component - Rich Text Markdown Editor
 *
 * This component provides a WYSIWYG markdown editor using MDXEditor,
 * with custom integration for the BubblesNotes upload system.
 *
 * Features:
 * - Rich text editing mode (like Google Docs/Notion)
 * - Custom toolbar with consistent iconography (Heroicons)
 * - Image upload via drag-drop, paste, or toolbar button
 * - File attachment support
 * - Preserves existing upload system integration
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
    MDXEditor,
    MDXEditorMethods,
    headingsPlugin,
    listsPlugin,
    quotePlugin,
    thematicBreakPlugin,
    markdownShortcutPlugin,
    codeBlockPlugin,
    codeMirrorPlugin,
    tablePlugin,
    imagePlugin,
    linkPlugin,
    linkDialogPlugin,
    toolbarPlugin,
    diffSourcePlugin,
    BoldItalicUnderlineToggles,
    BlockTypeSelect,
    ListsToggle,
    CodeToggle,
    CreateLink,
    InsertTable,
    InsertThematicBreak,
    InsertCodeBlock,
    DiffSourceToggleWrapper,
    ButtonWithTooltip,
    Separator,
} from '@mdxeditor/editor';
import '@mdxeditor/editor/style.css';
import { useFileUploadService } from '../services/imageUploadService';
import { useAuth } from '../contexts/AuthContext';
import { useToastNotifications } from './ui/Toast';
import {
    isImageExtension,
    getImageInputAcceptAttribute,
    getFileInputAcceptAttribute,
    isFileAllowed,
    IMAGE_EXTENSIONS,
} from '../utils/attachmentParser';
import { resizeImage, imageNeedsResize } from '../utils/imageProcessor';

// Maximum file size: 10MB
const MAX_FILE_SIZE = 10 * 1024 * 1024;

interface MDXEditorComponentProps {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    autoFocus?: boolean;
}

/**
 * Custom SVG icons using Heroicons-style design
 */
const Icons = {
    ImageUpload: () => (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
    ),
    FileAttach: () => (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
        </svg>
    ),
};

/**
 * Custom toolbar component with consistent iconography
 */
const CustomToolbar: React.FC = () => {
    return (
        <>
            {/* Text Formatting */}
            <BoldItalicUnderlineToggles />
            <ListsToggle options={['check']} />

            <Separator />

            {/* Block Type */}
            <BlockTypeSelect />
            <CodeToggle />

            <Separator />

            {/* Insert Elements */}
            <InsertCodeBlock />
            <InsertTable />
            <InsertThematicBreak />

            <Separator />

            {/* Link */}
            <CreateLink />
        </>
    );
};

/**
 * MDXEditor Component with custom upload integration
 */
export const MDXEditorComponent: React.FC<MDXEditorComponentProps> = ({
                                                                          value,
                                                                          onChange,
                                                                          placeholder = "Type your note here...",
                                                                          autoFocus = false,
                                                                      }) => {
    const editorRef = useRef<MDXEditorMethods | null>(null);
    const imageInputRef = useRef<HTMLInputElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const { upload } = useFileUploadService();
    const toast = useToastNotifications();
    const [isDragOver, setIsDragOver] = useState(false);

    /**
     * Custom image upload handler for MDXEditor
     * This integrates with the existing upload service
     * Note: MDXEditor expects a URL to be returned, not markdown
     */
    const handleImageUpload = useCallback(async (imageFile: File): Promise<string> => {
        try {
            // Validate file - MIME type check is sufficient for images
            if (!imageFile.type.startsWith('image/')) {
                throw new Error('Please select an image file');
            }

            if (imageFile.size > MAX_FILE_SIZE) {
                throw new Error(`File size exceeds 10MB limit. Current size: ${(imageFile.size / 1024 / 1024).toFixed(2)}MB`);
            }

            // Process image if needed
            let fileToUpload: File = imageFile;
            const needsResize = await imageNeedsResize(imageFile);
            if (needsResize) {
                const resizedBlob = await resizeImage(imageFile);
                fileToUpload = new File([resizedBlob], imageFile.name, {
                    type: resizedBlob.type,
                    lastModified: Date.now(),
                });
            }

            // Upload the file
            const uploadResult = await upload(fileToUpload);

            // Return the URL (not markdown) - MDXEditor will create the image syntax
            return uploadResult.url;
        } catch (error) {
            const errorMsg = error instanceof Error ? error.message : 'Image upload failed';
            toast.error(errorMsg);
            throw error;
        }
    }, [upload, toast]);

    /**
     * Custom file attachment handler
     */
    const handleFileUpload = useCallback(async (file: File): Promise<string> => {
        try {
            // Validate file
            if (!isFileAllowed(file.name, file.type)) {
                throw new Error(`Unsupported file type: ${file.type}`);
            }

            if (file.size > MAX_FILE_SIZE) {
                throw new Error(`File size exceeds 10MB limit. Current size: ${(file.size / 1024 / 1024).toFixed(2)}MB`);
            }

            // Upload the file
            const uploadResult = await upload(file);

            // Return the markdown syntax for the uploaded file
            return uploadResult.markdown;
        } catch (error) {
            const errorMsg = error instanceof Error ? error.message : 'File upload failed';
            toast.error(errorMsg);
            throw error;
        }
    }, [upload, toast]);

    /**
     * Process dropped files (images or attachments) on the editor wrapper
     * This handles file attachments (non-images) since imagePlugin only handles images
     */
    const handleDrop = useCallback(async (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(false);

        const files = e.dataTransfer.files;
        if (files.length === 0) return;

        let handled = false;

        for (let i = 0; i < files.length; i++) {
            const file = files[i];

            // Skip images - let MDXEditor's imagePlugin handle them
            if (isImageFile(file)) {
                handled = true;  // Mark as handled but let imagePlugin process it
                continue;
            }

            // Handle non-image file attachments
            if (isFileAllowed(file.name, file.type)) {
                try {
                    const markdown = await handleFileUpload(file);
                    editorRef.current?.insertMarkdown(markdown + '\n');
                    toast.success('File attached successfully');
                    handled = true;
                } catch (error) {
                    console.error('File upload failed:', error);
                }
            } else {
                toast.error(`Unsupported file type: ${file.type}`);
            }
        }

        // If we only had images, don't prevent default - let imagePlugin handle it
        // If we handled files, we've already inserted markdown
    }, [handleFileUpload, toast]);

    /**
     * Handle paste event for file uploads
     * Note: This is for pasting image files from clipboard. MDXEditor handles pasting images from other sources.
     */
    const handlePaste = useCallback(async (e: React.ClipboardEvent) => {
        const clipboardItems = e.clipboardData.items;

        // Look for image files in clipboard
        let imageFile: File | null = null;
        for (const item of clipboardItems) {
            if (item.type && item.type.startsWith('image/')) {
                imageFile = item.getAsFile();
                break;
            }
        }

        if (imageFile) {
            e.preventDefault();
            try {
                // Upload and get the URL
                const url = await handleImageUpload(imageFile);
                // Construct markdown with the filename as alt text
                const markdown = `![${imageFile.name}](${url})`;
                editorRef.current?.insertMarkdown(markdown + '\n');
                toast.success('Image uploaded successfully');
            } catch (error) {
                console.error('Image paste failed:', error);
            }
            return;
        }

        // Look for other file types
        let file: File | null = null;
        for (const item of clipboardItems) {
            if (item.type && (item.type.startsWith('application/') || item.type.startsWith('text/'))) {
                file = item.getAsFile();
                break;
            }
        }

        if (file && isFileAllowed(file.name, file.type)) {
            e.preventDefault();
            try {
                const markdown = await handleFileUpload(file);
                editorRef.current?.setMarkdown(editorRef.current?.getMarkdown() + '\n' + markdown);
                toast.success('File attached successfully');
            } catch (error) {
                console.error('File paste failed:', error);
            }
        }
    }, [handleImageUpload, handleFileUpload, toast]);

    /**
     * Helper function to check if a file is an image based on its name or type
     */
    const isImageFile = (file: File): boolean => {
        // First check MIME type
        if (file.type.startsWith('image/')) {
            return true;
        }
        // Then check file extension
        const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();
        return IMAGE_EXTENSIONS.includes(ext);
    };

    /**
     * Handle image input change (from file picker)
     * For toolbar buttons, we need to construct markdown ourselves since handleImageUpload returns URL
     */
    const handleImageInputChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || files.length === 0) {
            console.log('[MDXEditor] No files selected');
            return;
        }

        const file = files[0];
        console.log('[MDXEditor] Selected file:', file.name, file.type);
        console.log('[MDXEditor] Current editor markdown:', editorRef.current?.getMarkdown());
        console.log('[MDXEditor] Current value prop:', value);

        if (!isImageFile(file)) {
            console.error('[MDXEditor] File is not an image:', file.name);
            toast.error(`"${file.name}" is not a valid image file. Please select an image file.`);
            // Reset input to allow selecting the same file again
            if (imageInputRef.current) {
                imageInputRef.current.value = '';
            }
            return;
        }

        try {
            // Upload and get the URL
            const url = await handleImageUpload(file);
            console.log('[MDXEditor] Uploaded image URL:', url);

            // Construct markdown with the filename as alt text
            const markdown = `![${file.name}](${url})`;
            console.log('[MDXEditor] Inserting markdown:', markdown);

            // Get current markdown, insert new content, then set the full markdown
            const currentMarkdown = editorRef.current?.getMarkdown() || '';
            const newMarkdown = currentMarkdown ? `${currentMarkdown}\n${markdown}` : markdown;
            console.log('[MDXEditor] New full markdown:', newMarkdown);

            editorRef.current?.setMarkdown(newMarkdown);
            // Also call onChange to sync with parent
            onChange(newMarkdown);

            toast.success('Image uploaded successfully');
        } catch (error) {
            console.error('[MDXEditor] Image upload failed:', error);
            toast.error(error instanceof Error ? error.message : 'Image upload failed');
        }

        // Reset input to allow selecting the same file again
        if (imageInputRef.current) {
            imageInputRef.current.value = '';
        }
    }, [handleImageUpload, toast, onChange, value]);

    /**
     * Handle file input change (from file picker)
     */
    const handleFileInputChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || files.length === 0) {
            console.log('No files selected');
            return;
        }

        const file = files[0];
        console.log('Selected file:', file.name, file.type);

        if (!isFileAllowed(file.name, file.type)) {
            console.error('File type not allowed:', file.name, file.type);
            toast.error(`"${file.name}" is not a supported file type.`);
            // Reset input to allow selecting the same file again
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
            return;
        }

        try {
            const markdown = await handleFileUpload(file);
            editorRef.current?.insertMarkdown(markdown + '\n');
            toast.success('File attached successfully');
        } catch (error) {
            console.error('File upload failed:', error);
            toast.error(error instanceof Error ? error.message : 'File upload failed');
        }

        // Reset input to allow selecting the same file again
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    }, [handleFileUpload, toast]);

    /**
     * Custom toolbar with upload buttons and source/diff toggle
     */
    const CustomToolbarWithUpload: React.FC = () => {
            return (
                <DiffSourceToggleWrapper>
                    {/* Text Formatting */}
                    <BoldItalicUnderlineToggles />
                    <ListsToggle options={['check']} />

                    <Separator />

                    {/* Custom upload buttons */}
                    <ButtonWithTooltip
                        title="Upload image"
                        onClick={() => imageInputRef.current?.click()}
                    >
                        <Icons.ImageUpload />
                    </ButtonWithTooltip>

                    <ButtonWithTooltip
                        title="Attach file"
                        onClick={() => fileInputRef.current?.click()}
                    >
                        <Icons.FileAttach />
                    </ButtonWithTooltip>

                    {/* Block Type */}
                    <BlockTypeSelect />
                    <CodeToggle />

                    <Separator />

                    {/* Insert Elements */}
                    <InsertCodeBlock />
                    <InsertTable />
                    <InsertThematicBreak />

                    <Separator />

                    {/* Link */}
                    <CreateLink />

                    <Separator />


                    {/* Hidden file inputs */}
                    <input
                        ref={imageInputRef}
                        type="file"
                        accept={getImageInputAcceptAttribute()}
                        onChange={handleImageInputChange}
                        className="hidden"
                    />
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept={getFileInputAcceptAttribute()}
                        onChange={handleFileInputChange}
                        className="hidden"
                    />
                </DiffSourceToggleWrapper>
            );
        };

    // Auto-focus the editor after mount
    useEffect(() => {
        if (autoFocus && editorRef.current) {
            // Use setTimeout to ensure the editor is fully initialized
            setTimeout(() => {
                editorRef.current?.focus();
            }, 100);
        }
    }, [autoFocus]);

    // Sync editor content when value prop changes externally (e.g., after save)
    useEffect(() => {
        if (editorRef.current && editorRef.current.getMarkdown() !== value) {
            editorRef.current.setMarkdown(value || '');
        }
    }, [value]);

    return (
        <div className="w-full bg-white rounded-lg border border-neutral-200 shadow-sm overflow-hidden">
            <MDXEditor
                ref={editorRef}
                markdown={value}
                onChange={onChange}
                placeholder={placeholder}
                className="mdx-editor-custom"
                plugins={[
                    toolbarPlugin({
                        toolbarContents: () => <CustomToolbarWithUpload />,
                    }),
                    diffSourcePlugin({ viewMode: 'rich-text' }),
                    headingsPlugin({ allowedHeadingLevels: [1, 2, 3, 4, 5, 6] }),
                    listsPlugin(),
                    quotePlugin(),
                    thematicBreakPlugin(),
                    markdownShortcutPlugin(),
                    codeBlockPlugin(),
                    codeMirrorPlugin({
                        codeBlockLanguages: {
                            '': 'Plain',
                            js: 'JavaScript',
                            ts: 'TypeScript',
                            jsx: 'JSX',
                            tsx: 'TSX',
                            css: 'CSS',
                            html: 'HTML',
                            json: 'JSON',
                            txt: 'Plain Text',
                            sql: 'SQL',
                            python: 'Python',
                            java: 'Java',
                            cpp: 'C++',
                            c: 'C',
                            go: 'Go',
                            rs: 'Rust',
                            php: 'PHP',
                            ruby: 'Ruby',
                            swift: 'Swift',
                            kt: 'Kotlin',
                            scala: 'Scala',
                            haskell: 'Haskell',
                            lua: 'Lua',
                            bash: 'Bash',
                            sh: 'Shell',
                            md: 'Markdown',
                            mdx: 'MDX',
                            xml: 'XML',
                            yaml: 'YAML',
                            dockerfile: 'Dockerfile',
                        },
                    }),
                    tablePlugin(),
                    imagePlugin({
                        imageUploadHandler: handleImageUpload,
                        disableImageResize: true,
                    }),
                    linkPlugin(),
                    linkDialogPlugin(),
                ]}
            />
        </div>
    );
};
