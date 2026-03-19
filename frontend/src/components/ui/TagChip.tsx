import React from 'react';

export interface TagChipProps {
  /** Tag name to display */
  label: string;
  
  /** Whether this tag is removable */
  removable?: boolean;
  
  /** Callback when remove button is clicked */
  onRemove?: () => void;
  
  /** Whether the tag is selected/active */
  selected?: boolean;
  
  /** Click handler for the chip itself */
  onClick?: () => void;
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Tag chip component with remove functionality and consistent colors
 * Used for displaying tags in note cards, search filters, etc.
 */
export const TagChip: React.FC<TagChipProps> = ({
  label,
  removable = false,
  onRemove,
  selected = false,
  onClick,
  className = '',
}) => {
  const handleRemove = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    if (onRemove) {
      onRemove();
    }
  };

  return (
    <span
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick?.();
        }
      }}
      className={`
        inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium
        transition-all duration-fast cursor-pointer select-none
        focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-1
        ${selected
          ? 'bg-primary-600 text-white hover:bg-primary-700'
          : 'bg-primary-100 text-primary-700 hover:bg-primary-200'
        }
        ${className}
      `}
    >
      {label}
      
      {removable && (
        <button
          type="button"
          onClick={handleRemove}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              onRemove?.();
            }
          }}
          aria-label={`Remove tag ${label}`}
          className={`
            ml-1.5 p-0.5 rounded-full 
            transition-all duration-fast
            focus:outline-none focus:ring-2 focus:ring-primary-500
            ${selected
              ? 'hover:bg-primary-500 text-white'
              : 'hover:bg-primary-300 text-primary-600'
            }
          `}
        >
          <svg
            className="w-3 h-3"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      )}
    </span>
  );
};

export default TagChip;
