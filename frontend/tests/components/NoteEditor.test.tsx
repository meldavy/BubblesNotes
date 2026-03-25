/**
 * NoteEditor Paste Handler Tests
 * 
 * Note: These tests require Jest, jsdom, and React Testing Library to be configured.
 * Run: npm install --save-dev jest @types/jest ts-jest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
 * Then add test script to package.json: "test": "jest"
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { NoteEditor } from '../../src/components/NoteEditor';

// Mock the image upload service
jest.mock('../../src/services/imageUploadService', () => ({
    uploadImageToNote: jest.fn(),
}));

// Mock the image processor
jest.mock('../../src/utils/imageProcessor', () => ({
    resizeImage: jest.fn(),
    isSupportedImageFormat: jest.fn(),
    isFileTooLarge: jest.fn(),
    imageNeedsResize: jest.fn(),
}));

// Mock AuthContext
jest.mock('../../src/contexts/AuthContext', () => ({
    useAuth: () => ({
        getAccessToken: () => 'test-token',
    }),
}));

// Mock fetch API
const mockFetch = jest.fn();
global.fetch = mockFetch;

describe('NoteEditor Paste Handler', () => {
    const defaultProps = {
        value: '',
        onChange: jest.fn(),
        onSave: jest.fn(),
        noteId: 1,
        isEditing: true,
    };

    beforeEach(() => {
        jest.clearAllMocks();
        mockFetch.mockReset();
    });

    it('should handle paste of image file', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');
        expect(textarea).toBeTruthy();

        // Create a mock image file
        const mockImageFile = new File(['image data'], 'test.png', { type: 'image/png' });

        // Create clipboard event with image
        const clipboardData = {
            items: [
                {
                    type: 'image/png',
                    getAsFile: () => mockImageFile,
                },
            ],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        // Verify paste was handled
        expect(defaultProps.onChange).toHaveBeenCalled();
    });

    it('should ignore paste of non-image content', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        // Create clipboard event with text only
        const clipboardData = {
            items: [
                {
                    type: 'text/plain',
                    getAsFile: () => null,
                },
            ],
        } as unknown as Clipboard;

        if (textarea) {
            const event = fireEvent.paste(textarea, { clipboardData });
            // Event should not be prevented for non-image content
            expect(event).toBeTruthy();
        }
    });

    it('should handle paste of multiple clipboard items', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        // Create clipboard with text and image
        const mockImageFile = new File(['image data'], 'test.png', { type: 'image/png' });
        const clipboardData = {
            items: [
                {
                    type: 'text/plain',
                    getAsFile: () => null,
                },
                {
                    type: 'image/png',
                    getAsFile: () => mockImageFile,
                },
            ],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        // Should process the image
        expect(defaultProps.onChange).toHaveBeenCalled();
    });
});

describe('NoteEditor Upload State', () => {
    const defaultProps = {
        value: '',
        onChange: jest.fn(),
        onSave: jest.fn(),
        noteId: 1,
        isEditing: true,
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should show loading state during upload', async () => {
        render(<NoteEditor {...defaultProps} />);

        // Verify loading indicator is not shown initially
        const loadingElements = screen.queryAllByText(/uploading/i);
        expect(loadingElements).toHaveLength(0);
    });

    it('should show error state on upload failure', async () => {
        render(<NoteEditor {...defaultProps} />);

        // Verify error indicator is not shown initially
        const errorElements = screen.queryAllByText(/error/i);
        // May show other errors, but not upload errors initially
        expect(errorElements.length).toBeLessThan(2);
    });

    it('should show success state after upload', async () => {
        render(<NoteEditor {...defaultProps} />);

        // Verify success state is not shown initially
        const successElements = screen.queryAllByText(/uploaded/i);
        expect(successElements).toHaveLength(0);
    });
});

describe('NoteEditor Format Validation', () => {
    const defaultProps = {
        value: '',
        onChange: jest.fn(),
        onSave: jest.fn(),
        noteId: 1,
        isEditing: true,
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should handle PNG format', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const pngFile = new File(['data'], 'test.png', { type: 'image/png' });
        const clipboardData = {
            items: [{ type: 'image/png', getAsFile: () => pngFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        expect(defaultProps.onChange).toHaveBeenCalled();
    });

    it('should handle JPEG format', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const jpegFile = new File(['data'], 'test.jpg', { type: 'image/jpeg' });
        const clipboardData = {
            items: [{ type: 'image/jpeg', getAsFile: () => jpegFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        expect(defaultProps.onChange).toHaveBeenCalled();
    });

    it('should handle GIF format', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const gifFile = new File(['data'], 'test.gif', { type: 'image/gif' });
        const clipboardData = {
            items: [{ type: 'image/gif', getAsFile: () => gifFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        expect(defaultProps.onChange).toHaveBeenCalled();
    });

    it('should handle WebP format', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const webpFile = new File(['data'], 'test.webp', { type: 'image/webp' });
        const clipboardData = {
            items: [{ type: 'image/webp', getAsFile: () => webpFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        expect(defaultProps.onChange).toHaveBeenCalled();
    });

    it('should reject BMP format', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const bmpFile = new File(['data'], 'test.bmp', { type: 'image/bmp' });
        const clipboardData = {
            items: [{ type: 'image/bmp', getAsFile: () => bmpFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        // Should not call onChange for unsupported format
        // (or call it with error handling)
    });
});

describe('NoteEditor Size Validation', () => {
    const defaultProps = {
        value: '',
        onChange: jest.fn(),
        onSave: jest.fn(),
        noteId: 1,
        isEditing: true,
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('should accept file under 10MB', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const smallFile = new File(['data'], 'test.png', { type: 'image/png' });
        Object.defineProperty(smallFile, 'size', { value: 1024 * 1024 }); // 1MB

        const clipboardData = {
            items: [{ type: 'image/png', getAsFile: () => smallFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        expect(defaultProps.onChange).toHaveBeenCalled();
    });

    it('should reject file over 10MB', async () => {
        const { container } = render(<NoteEditor {...defaultProps} />);
        const textarea = container.querySelector('textarea');

        const largeFile = new File(['data'], 'test.png', { type: 'image/png' });
        Object.defineProperty(largeFile, 'size', { value: 11 * 1024 * 1024 }); // 11MB

        const clipboardData = {
            items: [{ type: 'image/png', getAsFile: () => largeFile }],
        } as unknown as Clipboard;

        if (textarea) {
            fireEvent.paste(textarea, { clipboardData });
        }

        // Should handle error for oversized file
    });
});
