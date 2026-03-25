/**
 * Image Upload Service Tests
 * 
 * Note: These tests require Jest and jsdom environment to be configured.
 * Run: npm install --save-dev jest @types/jest ts-jest jsdom @testing-library/jest-dom
 * Then add test script to package.json: "test": "jest"
 */

import { uploadImageToNote, getNoteImages, deleteNoteImage, useImageUploadService } from '../../src/services/imageUploadService';

// Mock fetch API
const mockFetch = jest.fn();
global.fetch = mockFetch;

// Mock AuthContext
jest.mock('../../src/contexts/AuthContext', () => ({
    useAuth: () => ({
        getAccessToken: () => 'test-token',
    }),
}));

describe('uploadImageToNote', () => {
    beforeEach(() => {
        mockFetch.mockReset();
    });

    it('should upload image successfully', async () => {
        const mockResponse = {
            ok: true,
            json: async () => ({
                id: 1,
                fileName: 'test.png',
                filePath: '/images/test.png',
                fileSize: 1024,
                contentType: 'image/png',
                width: 800,
                height: 600,
            }),
        };
        mockFetch.mockResolvedValue(mockResponse);

        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });
        const getAccessToken = () => 'test-token';

        const result = await uploadImageToNote(1, mockFile, getAccessToken);

        expect(mockFetch).toHaveBeenCalledWith(
            '/api/v1/notes/1/images',
            expect.objectContaining({
                method: 'POST',
                body: expect.any(FormData),
            }),
            getAccessToken
        );
        expect(result.id).toBe(1);
        expect(result.fileName).toBe('test.png');
    });

    it('should throw error on upload failure', async () => {
        const mockResponse = {
            ok: false,
            status: 400,
            json: async () => ({ error: 'Invalid file type' }),
        };
        mockFetch.mockResolvedValue(mockResponse);

        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });
        const getAccessToken = () => 'test-token';

        await expect(uploadImageToNote(1, mockFile, getAccessToken)).rejects.toThrow('Invalid file type');
    });

    it('should throw error on network failure', async () => {
        mockFetch.mockRejectedValue(new Error('Network error'));

        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });
        const getAccessToken = () => 'test-token';

        await expect(uploadImageToNote(1, mockFile, getAccessToken)).rejects.toThrow('Network error');
    });

    it('should include file in FormData', async () => {
        const mockResponse = {
            ok: true,
            json: async () => ({ id: 1 }),
        };
        mockFetch.mockResolvedValue(mockResponse);

        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });
        const getAccessToken = () => 'test-token';

        await uploadImageToNote(1, mockFile, getAccessToken);

        expect(mockFetch).toHaveBeenCalledWith(
            '/api/v1/notes/1/images',
            expect.objectContaining({
                body: expect.any(FormData),
            }),
            getAccessToken
        );
    });
});

describe('getNoteImages', () => {
    beforeEach(() => {
        mockFetch.mockReset();
    });

    it('should fetch images for a note', async () => {
        const mockResponse = {
            ok: true,
            json: async () => [
                { id: 1, fileName: 'image1.png', filePath: '/images/image1.png' },
                { id: 2, fileName: 'image2.png', filePath: '/images/image2.png' },
            ],
        };
        mockFetch.mockResolvedValue(mockResponse);

        const getAccessToken = () => 'test-token';
        const result = await getNoteImages(1, getAccessToken);

        expect(mockFetch).toHaveBeenCalledWith(
            '/api/v1/notes/1/images',
            expect.objectContaining({ method: 'GET' }),
            getAccessToken
        );
        expect(result.length).toBe(2);
    });

    it('should throw error on fetch failure', async () => {
        const mockResponse = {
            ok: false,
            status: 404,
            json: async () => ({ error: 'Note not found' }),
        };
        mockFetch.mockResolvedValue(mockResponse);

        const getAccessToken = () => 'test-token';

        await expect(getNoteImages(999, getAccessToken)).rejects.toThrow('Note not found');
    });
});

describe('deleteNoteImage', () => {
    beforeEach(() => {
        mockFetch.mockReset();
    });

    it('should delete image successfully', async () => {
        const mockResponse = {
            ok: true,
            status: 204,
        };
        mockFetch.mockResolvedValue(mockResponse);

        const getAccessToken = () => 'test-token';
        await expect(deleteNoteImage(1, 10, getAccessToken)).resolves.not.toThrow();

        expect(mockFetch).toHaveBeenCalledWith(
            '/api/v1/notes/1/images/10',
            expect.objectContaining({ method: 'DELETE' }),
            getAccessToken
        );
    });

    it('should throw error on delete failure', async () => {
        const mockResponse = {
            ok: false,
            status: 404,
            json: async () => ({ error: 'Image not found' }),
        };
        mockFetch.mockResolvedValue(mockResponse);

        const getAccessToken = () => 'test-token';

        await expect(deleteNoteImage(1, 999, getAccessToken)).rejects.toThrow('Image not found');
    });
});

describe('useImageUploadService', () => {
    beforeEach(() => {
        mockFetch.mockReset();
    });

    it('should provide uploadImage function', () => {
        const { uploadImage } = useImageUploadService();

        expect(typeof uploadImage).toBe('function');
    });

    it('should provide getImages function', () => {
        const { getImages } = useImageUploadService();

        expect(typeof getImages).toBe('function');
    });

    it('should provide deleteImage function', () => {
        const { deleteImage } = useImageUploadService();

        expect(typeof deleteImage).toBe('function');
    });

    it('should use auth context for token', async () => {
        const mockResponse = {
            ok: true,
            json: async () => ({ id: 1 }),
        };
        mockFetch.mockResolvedValue(mockResponse);

        const { uploadImage } = useImageUploadService();
        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });

        await uploadImage(1, mockFile);

        expect(mockFetch).toHaveBeenCalledWith(
            expect.any(String),
            expect.any(Object),
            expect.any(Function)
        );
    });
});
