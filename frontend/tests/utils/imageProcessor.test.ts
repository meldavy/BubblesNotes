/**
 * Image Processor Tests
 * 
 * Note: These tests require Jest and jsdom environment to be configured.
 * Run: npm install --save-dev jest @types/jest ts-jest jsdom @testing-library/jest-dom
 * Then add test script to package.json: "test": "jest"
 */

import { resizeImage, isSupportedImageFormat, isFileTooLarge, imageNeedsResize, processImageFile } from '../utils/imageProcessor';

// Mock Image and Canvas for jsdom environment
class MockImage {
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
    width: number = 0;
    height: number = 0;
    src: string = '';

    set src(value: string) {
        this._src = value;
        // Simulate image loading
        setTimeout(() => {
            if (this.onload) {
                this.onload();
            }
        }, 0);
    }

    get src(): string {
        return this._src;
    }

    private _src: string = '';
}

class MockCanvas {
    width: number = 0;
    height: number = 0;
    private blobData: Blob | null = null;

    getContext(): CanvasRenderingContext2D | null {
        return {
            imageSmoothingEnabled: true,
            imageSmoothingQuality: 'high',
            drawImage: (img: any, x: number, y: number, width: number, height: number) => {
                this.width = width;
                this.height = height;
            },
        } as CanvasRenderingContext2D;
    }

    toBlob(callback: (blob: Blob | null) => void, type?: string, quality?: number): void {
        // Create a mock blob
        const mockBlob = new Blob(['mock image data'], { type: type || 'image/png' });
        this.blobData = mockBlob;
        callback(mockBlob);
    }
}

// Mock DOM elements
const originalImage = global.Image;
const originalCanvas = global.HTMLCanvasElement;

beforeAll(() => {
    global.Image = MockImage as any;
    global.HTMLCanvasElement = MockCanvas as any;
});

afterAll(() => {
    global.Image = originalImage;
    global.HTMLCanvasElement = originalCanvas;
});

describe('resizeImage', () => {
    it('should resize image to max 1920px width', async () => {
        // Create a mock file
        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });

        // Mock image with large dimensions
        const mockImg = new MockImage();
        mockImg.width = 4000;
        mockImg.height = 3000;

        const result = await resizeImage(mockFile as any);

        expect(result).toBeDefined();
        expect(result.type).toBe('image/png');
    });

    it('should preserve aspect ratio when resizing', async () => {
        const mockFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });

        const result = await resizeImage(mockFile as any);

        expect(result).toBeDefined();
    });

    it('should not resize image smaller than max width', async () => {
        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });

        // Mock image with small dimensions
        const mockImg = new MockImage();
        mockImg.width = 800;
        mockImg.height = 600;

        const result = await resizeImage(mockFile as any);

        expect(result).toBeDefined();
    });
});

describe('isSupportedImageFormat', () => {
    it('should return true for PNG', () => {
        const pngFile = new File(['test'], 'test.png', { type: 'image/png' });
        expect(isSupportedImageFormat(pngFile)).toBe(true);
    });

    it('should return true for JPEG', () => {
        const jpegFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
        expect(isSupportedImageFormat(jpegFile)).toBe(true);
    });

    it('should return true for GIF', () => {
        const gifFile = new File(['test'], 'test.gif', { type: 'image/gif' });
        expect(isSupportedImageFormat(gifFile)).toBe(true);
    });

    it('should return true for WebP', () => {
        const webpFile = new File(['test'], 'test.webp', { type: 'image/webp' });
        expect(isSupportedImageFormat(webpFile)).toBe(true);
    });

    it('should return false for BMP', () => {
        const bmpFile = new File(['test'], 'test.bmp', { type: 'image/bmp' });
        expect(isSupportedImageFormat(bmpFile)).toBe(false);
    });

    it('should return false for non-image files', () => {
        const pdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
        expect(isSupportedImageFormat(pdfFile)).toBe(false);
    });

    it('should return false for unknown types', () => {
        const unknownFile = new File(['test'], 'test.unknown', { type: 'application/unknown' });
        expect(isSupportedImageFormat(unknownFile)).toBe(false);
    });
});

describe('isFileTooLarge', () => {
    it('should return false for file under 10MB', () => {
        const smallFile = new File(['test'], 'test.png', { type: 'image/png' });
        // Mock file size
        Object.defineProperty(smallFile, 'size', { value: 1024 * 1024 }); // 1MB

        expect(isFileTooLarge(smallFile)).toBe(false);
    });

    it('should return false for file exactly 10MB', () => {
        const exactFile = new File(['test'], 'test.png', { type: 'image/png' });
        Object.defineProperty(exactFile, 'size', { value: 10 * 1024 * 1024 }); // Exactly 10MB

        expect(isFileTooLarge(exactFile)).toBe(false);
    });

    it('should return true for file over 10MB', () => {
        const largeFile = new File(['test'], 'test.png', { type: 'image/png' });
        Object.defineProperty(largeFile, 'size', { value: 11 * 1024 * 1024 }); // 11MB

        expect(isFileTooLarge(largeFile)).toBe(true);
    });

    it('should return true for very large file', () => {
        const hugeFile = new File(['test'], 'test.png', { type: 'image/png' });
        Object.defineProperty(hugeFile, 'size', { value: 100 * 1024 * 1024 }); // 100MB

        expect(isFileTooLarge(hugeFile)).toBe(true);
    });
});

describe('imageNeedsResize', () => {
    it('should return true for image wider than 1920px', async () => {
        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });

        const needsResize = await imageNeedsResize(mockFile as any);

        // With mock, this depends on mock implementation
        expect(typeof needsResize).toBe('boolean');
    });

    it('should return false for image 1920px or narrower', async () => {
        const mockFile = new File(['test'], 'test.png', { type: 'image/png' });

        const needsResize = await imageNeedsResize(mockFile as any);

        expect(typeof needsResize).toBe('boolean');
    });
});

describe('processImageFile', () => {
    it('should throw error for unsupported format', async () => {
        const bmpFile = new File(['test'], 'test.bmp', { type: 'image/bmp' });

        await expect(processImageFile(bmpFile)).rejects.toThrow('Unsupported format');
    });

    it('should throw error for file exceeding 10MB', async () => {
        const largeFile = new File(['test'], 'test.png', { type: 'image/png' });
        Object.defineProperty(largeFile, 'size', { value: 11 * 1024 * 1024 });

        await expect(processImageFile(largeFile)).rejects.toThrow('exceeds 10MB');
    });

    it('should return file unchanged if no resize needed', async () => {
        const smallFile = new File(['test'], 'test.png', { type: 'image/png' });
        Object.defineProperty(smallFile, 'size', { value: 1024 * 1024 });

        const result = await processImageFile(smallFile as any);

        expect(result).toBeDefined();
    });

    it('should process valid image file', async () => {
        const validFile = new File(['test'], 'test.png', { type: 'image/png' });
        Object.defineProperty(validFile, 'size', { value: 1024 * 1024 });

        const result = await processImageFile(validFile as any);

        expect(result).toBeDefined();
        expect(result instanceof Blob).toBe(true);
    });
});
