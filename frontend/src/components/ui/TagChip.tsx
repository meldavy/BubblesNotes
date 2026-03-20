import React from 'react';

export interface TagChipProps {
  /** Tag name to display */
  label: string;
  
  /** Whether this tag is removable */
  removable?: boolean;
  
  /** Callback when remove button is clicked */
  onRemove?: () => void;
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Compact, professional tag chip component
 * Production-ready design with proper spacing and visual hierarchy
 * 
 * Design specs:
 * - Height: ~24px (compact)
 * - Padding: 4px 8px
 * - Font: 12px (text-xs)
 * - Border radius: 4px
 * - Subtle border for visual separation
 */
export const TagChip: React.FC<TagChipProps> = ({
  label,
  removable = false,
  onRemove,
  className = '',
}) => {
  const handleRemove = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation();
    e.preventDefault();
    onRemove?.();
  };

  return (
    <span
      className={`
        inline-flex items-center gap-1
        rounded-md
        bg-neutral-400/10
        px-2 py-1
        text-xs font-medium
        text-neutral-400
        inset-ring inset-ring-neutral-400/20
        ${className}
      `}
    >
      <span className="truncate max-w-[100px]">{label}</span>
      
      {removable && (
        <button
          type="button"
          onClick={handleRemove}
          aria-label={`Remove tag ${label}`}
          className="
            flex items-center justify-center
            flex-shrink-0
            min-h-0 min-w-0
            w-4 h-4
            text-neutral-500
            hover:text-neutral-600
            hover:bg-neutral-400/20
            rounded
            transition-colors
            focus:outline-none
          "
        >
          <svg
            className="w-3 h-3"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            strokeWidth={2}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      )}
    </span>
  );
};

export default TagChip;
