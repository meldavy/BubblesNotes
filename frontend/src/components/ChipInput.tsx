import React, { useState, KeyboardEvent, useRef, useEffect } from 'react';
import { TagChip } from './ui/TagChip';

export interface ChipInputProps {
  /** Current list of tags */
  tags: string[];
  /** Callback when tags change */
  onChange: (tags: string[]) => void;
  /** Placeholder for the input field */
  placeholder?: string;
  /** Label for the input */
  label?: string;
  /** Whether the input is disabled */
  disabled?: boolean;
  /** Error message to display */
  error?: string;
  /** Maximum number of tags allowed */
  maxTags?: number;
  /** Callback when a tag is added */
  onTagAdd?: (tag: string) => void;
  /** Callback when a tag is removed */
  onTagRemove?: (tag: string) => void;
}

/**
 * Professional chip input component for tag management
 * Follows the design system from ui-design-phase.md
 *
 * Features:
 * - Clean, modern UI with proper spacing
 * - Smooth animations for adding/removing chips
 * - Keyboard navigation (Enter to add, Backspace to remove last)
 * - Duplicate prevention
 * - Optional max tags limit
 * - Error state support
 * - Accessible with ARIA labels
 */
export const ChipInput: React.FC<ChipInputProps> = ({
  tags,
  onChange,
  placeholder = 'Type and press Enter to add a tag',
  label,
  disabled = false,
  error,
  maxTags,
  onTagAdd,
  onTagRemove,
}) => {
  const [input, setInput] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const addTag = (tag: string) => {
    const trimmed = tag.trim();
    if (!trimmed) return;
    if (tags.includes(trimmed)) {
      // Tag already exists - could show a toast or visual feedback
      return;
    }
    if (maxTags && tags.length >= maxTags) {
      // Max tags reached - could show error
      return;
    }
    const newTags = [...tags, trimmed];
    onChange(newTags);
    onTagAdd?.(trimmed);
  };

  const removeTag = (tag: string) => {
    const newTags = tags.filter(t => t !== tag);
    onChange(newTags);
    onTagRemove?.(tag);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (input.trim()) {
        addTag(input);
        setInput('');
      }
    } else if (e.key === 'Backspace' && !input && tags.length > 0) {
      // Remove last tag when backspace is pressed on empty input
      e.preventDefault();
      removeTag(tags[tags.length - 1]);
    }
  };

  const handleInputFocus = () => {
    // Auto-focus the input when clicking on the container
    inputRef.current?.focus();
  };

  const isMaxTagsReached = maxTags ? tags.length >= maxTags : false;

  return (
    <div className="w-full">
      {label && (
        <label className="block text-sm font-medium text-neutral-700 mb-2">
          {label}
        </label>
      )}
      
      <div
        ref={containerRef}
        onClick={handleInputFocus}
        className={`
          flex flex-wrap items-center gap-2 p-2 min-h-[44px]
          rounded-lg border transition-all duration-150 ease-out
          cursor-text
          ${isMaxTagsReached
            ? 'border-neutral-300 bg-neutral-50 cursor-not-allowed'
            : error
              ? 'border-error-500 bg-error-bg'
              : 'border-neutral-300 bg-white hover:border-neutral-400'
          }
          ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
          focus-within:ring-2 focus-within:ring-primary-500 focus-within:border-primary-500
        `}
        role="textbox"
        aria-label={label || 'Tag input'}
        aria-multiline="false"
      >
        {tags.map(tag => (
          <TagChip
            key={tag}
            label={tag}
            removable={!disabled}
            onRemove={() => removeTag(tag)}
          />
        ))}
        
        <input
          ref={inputRef}
          type="text"
          disabled={disabled}
          className={`
            flex-1 min-w-[120px] max-w-full
            px-2 py-1.5 text-sm
            focus:outline-none border-none bg-transparent
            ${isMaxTagsReached ? 'text-neutral-400' : 'text-neutral-700'}
            ${disabled ? 'cursor-not-allowed' : ''}
          `}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={tags.length === 0 ? placeholder : ''}
          aria-label="Tag input field"
        />
      </div>

      {/* Error message */}
      {error && (
        <p className="mt-1.5 text-sm text-error-600 flex items-center gap-1.5">
          <svg className="w-4 h-4 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
              clipRule="evenodd"
            />
          </svg>
          {error}
        </p>
      )}

      {/* Max tags hint */}
      {isMaxTagsReached && !error && (
        <p className="mt-1.5 text-sm text-neutral-500">
          Maximum of {maxTags} tags reached
        </p>
      )}

      {/* Tags count hint when not at max */}
      {maxTags && !isMaxTagsReached && !error && tags.length > 0 && (
        <p className="mt-1.5 text-sm text-neutral-400">
          {tags.length} of {maxTags} tags added
        </p>
      )}
    </div>
  );
};

export default ChipInput;
