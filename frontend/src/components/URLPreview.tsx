import React from 'react';

interface URLPreviewData {
  url: string;
  title?: string;
  description?: string;
  favicon?: string;
  image?: string;
  siteName?: string;
}

interface URLPreviewProps {
  preview: URLPreviewData;
  onRemove?: () => void;
}

/**
 * URLPreview component - displays a preview card for a URL
 * Shows title, description, favicon, and optional image
 */
export const URLPreview: React.FC<URLPreviewProps> = ({ preview, onRemove }) => {
  const { url, title, description, favicon, image } = preview;

  // Fallback favicon if none provided
  const fallbackFavicon = favicon || `${url}/favicon.ico`;

  // Display raw URL if no preview data available
  if (!title && !description && !image) {
    return (
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="url-preview-fallback inline-block px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded-lg text-blue-600 hover:text-blue-700 text-sm transition-colors duration-200 break-all"
      >
        {url}
      </a>
    );
  }

  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="url-preview-card block bg-white border border-gray-200 rounded-lg overflow-hidden hover:shadow-md transition-shadow duration-200 max-w-lg"
    >
      <div className="flex">
        {/* Preview image (if available) */}
        {image && (
          <div className="w-32 h-32 flex-shrink-0">
            <img
              src={image}
              alt={title || 'Preview'}
              className="w-full h-full object-cover"
              onError={(e) => {
                // Hide image element if it fails to load
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          </div>
        )}
        
        <div className="flex-1 p-3">
          {/* Favicon and site info */}
          <div className="flex items-center mb-2">
            {favicon && (
              <img
                src={favicon}
                alt=""
                className="w-5 h-5 mr-2 flex-shrink-0"
                onError={(e) => {
                  // Hide favicon if it fails to load
                  (e.target as HTMLImageElement).style.display = 'none';
                }}
              />
            )}
            <span className="text-xs text-gray-500 truncate">
              {new URL(url).hostname}
            </span>
          </div>
          
          {/* Title */}
          {title && (
            <h3 className="font-semibold text-gray-900 mb-1 line-clamp-2 text-sm">
              {title}
            </h3>
          )}
          
          {/* Description */}
          {description && (
            <p className="text-xs text-gray-600 line-clamp-2">
              {description}
            </p>
          )}
        </div>
      </div>
    </a>
  );
};

/**
 * URLPreviewList component - displays a list of URL previews
 */
interface URLPreviewListProps {
  previews: URLPreviewData[];
}

export const URLPreviewList: React.FC<URLPreviewListProps> = ({ previews }) => {
  if (!previews || previews.length === 0) {
    return null;
  }

  return (
    <div className="url-previews-container mt-4 space-y-3">
      {previews.map((preview, index) => (
        <URLPreview key={index} preview={preview} />
      ))}
    </div>
  );
};

export default URLPreview;
