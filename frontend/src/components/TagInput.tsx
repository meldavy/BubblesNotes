import React, { useState, KeyboardEvent, useRef, useEffect } from 'react';
import { TagChip } from './ui/TagChip';

export interface TagInputProps {
  /** Current list of tags */
  tags: string[];
  /** Callback when tags change */
  onChange: (tags: string[]) => void;
  /** Placeholder for the input field */
  placeholder?: string;
  /** Whether the input is disabled */
  disabled?: boolean;
  /** Maximum number of tags allowed */
  maxTags?: number;
}

/**
 * Professional tag input component
 * Production-ready design with clean, minimal UI
 * 
 * Design specs:
 * - Compact, inline layout
 * - Tags displayed as small chips
 * - Input field seamlessly integrated
 * - Clean border with focus state
 * - No unnecessary padding or spacing
 */
export const TagInput: React.FC<TagInputProps> = ({
  tags,
  onChange,
  placeholder = 'Add tags...',
  disabled = false,
  maxTags,
}) => {
  const [input, setInput] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const addTag = (tag: string) => {
    const trimmed = tag.trim();
    if (!trimmed) {
      setInput('');
      setTimeout(() => inputRef.current?.focus(), 0);
      return;
    }
    if (tags.includes(trimmed)) {
      // Clear input but keep focus for duplicate tag
      setInput('');
      setTimeout(() => inputRef.current?.focus(), 0);
      return;
    }
    if (maxTags && tags.length >= maxTags) return;
    onChange([...tags, trimmed]);
    setInput('');
    // Keep focus on input for adding more tags
    setTimeout(() => inputRef.current?.focus(), 0);
  };

  const removeTag = (tag: string) => {
    onChange(tags.filter(t => t !== tag));
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (input.trim()) {
        addTag(input);
      }
    } else if (e.key === 'Backspace' && !input && tags.length > 0) {
      e.preventDefault();
      removeTag(tags[tags.length - 1]);
    }
  };

  const handleContainerClick = () => {
    if (!disabled) {
      inputRef.current?.focus();
    }
  };

  const isMaxReached = maxTags ? tags.length >= maxTags : false;

  return (
    <div className="w-full">
      <div
        ref={containerRef}
        onClick={handleContainerClick}
        className={`
          flex flex-wrap items-center gap-1.5
          p-1.5
          min-h-[32px]
          rounded
          border
          transition-all
          cursor-text
          ${disabled
            ? 'bg-neutral-50 border-neutral-200 cursor-not-allowed'
            : 'bg-white border-neutral-300 hover:border-neutral-400 focus-within:border-primary-500 focus-within:ring-1 focus-within:ring-primary-500'
          }
        `}
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
            flex-1 min-w-[80px]
            px-1 py-0.5
            text-sm
            bg-transparent
            outline-none
            ${disabled ? 'text-neutral-400' : 'text-neutral-700'}
          `}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={tags.length === 0 ? placeholder : ''}
        />
      </div>
      
      {maxTags && tags.length > 0 && (
        <p className="mt-1 text-xs text-neutral-400">
          {tags.length} / {maxTags} tags
        </p>
      )}
    </div>
  );
};

export default TagInput;
