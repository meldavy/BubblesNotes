import React, { useState, useRef, useEffect, KeyboardEvent } from 'react';

export interface SearchBarProps {
  /** Placeholder text */
  placeholder?: string;
  
  /** Current search value */
  value: string;
  
  /** Callback when search value changes */
  onChange: (value: string) => void;
  
  /** Callback when search is submitted (Enter key) */
  onSearch?: (value: string) => void;
  
  /** Suggested search terms for autocomplete */
  suggestions?: string[];
  
  /** Whether to show clear button */
  showClear?: boolean;
  
  /** Additional CSS classes for the container */
  className?: string;
  
  /** Additional CSS classes for the input element */
  inputClassName?: string;
}

/**
 * Search bar component with autocomplete dropdown and clear button
 * Supports keyboard navigation (ArrowUp/ArrowDown, Enter, Escape)
 */
export const SearchBar: React.FC<SearchBarProps> = ({
  placeholder = 'Search notes...',
  value,
  onChange,
  onSearch,
  suggestions = [],
  showClear = true,
  className = '',
  inputClassName = '',
}) => {
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close suggestions when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setShowSuggestions(false);
        setSelectedIndex(-1);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
    setShowSuggestions(true);
    setSelectedIndex(-1);
  };

  const handleClear = () => {
    onChange('');
    inputRef.current?.focus();
    setShowSuggestions(false);
    setSelectedIndex(-1);
  };

  const handleSubmit = (e?: React.FormEvent, submitValue?: string) => {
    e?.preventDefault();
    setShowSuggestions(false);
    const searchValue = (submitValue ?? value).trim();
    if (searchValue && onSearch) {
      onSearch(searchValue);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!showSuggestions || suggestions.length === 0) return;

    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setSelectedIndex((prev) => 
          prev < suggestions.length - 1 ? prev + 1 : prev
        );
        break;
      case 'ArrowUp':
        e.preventDefault();
        setSelectedIndex((prev) => (prev > 0 ? prev - 1 : -1));
        break;
      case 'Enter':
        if (selectedIndex >= 0) {
          e.preventDefault();
          onChange(suggestions[selectedIndex]);
          setShowSuggestions(false);
          handleSubmit();
        } else {
          handleSubmit(e);
        }
        break;
      case 'Escape':
        setShowSuggestions(false);
        setSelectedIndex(-1);
        break;
    }
  };

  const handleSuggestionClick = (suggestion: string) => {
    onChange(suggestion);
    setShowSuggestions(false);
    handleSubmit(undefined, suggestion);
  };

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Search Input */}
      <form onSubmit={handleSubmit} className="flex items-center">
        <div className="relative flex-1">
          {/* Search Icon */}
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-neutral-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>

          {/* Input Field */}
          <input
            ref={inputRef}
            type="text"
            value={value}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => {
              if (value && suggestions.length > 0) {
                setShowSuggestions(true);
              }
            }}
            placeholder={placeholder}
            aria-label="Search notes"
            aria-autocomplete="list"
            aria-expanded={showSuggestions}
            role="combobox"
            className={`
              w-full pl-10 pr-10
              rounded-lg border border-neutral-300 bg-white
              text-sm text-neutral-700 placeholder-neutral-400
              focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500
              transition-all duration-[150ms] ease-out
              h-10 leading-none
              ${inputClassName}
            `}
          />

          {/* Clear Button */}
          {showClear && value && (
            <button
              type="button"
              onClick={handleClear}
              aria-label="Clear search"
              className={`
                absolute right-2 top-1/2 -translate-y-1/2
                flex items-center justify-center
                text-neutral-400 hover:text-neutral-600
                transition-all duration-[150ms] ease-out
                focus:outline-none
              `}
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          )}

          {/* Search Button (visible on mobile) */}
          <button
            type="submit"
            aria-label="Submit search"
            className={`
              absolute right-2 top-1/2 -translate-y-1/2
              md:hidden p-1 rounded-md text-primary-600 hover:bg-primary-50
              transition-all duration-[150ms] ease-out
              focus:outline-none focus:ring-2 focus:ring-primary-500
            `}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </form>

      {/* Autocomplete Suggestions Dropdown */}
      {showSuggestions && suggestions.length > 0 && (
        <div
          role="listbox"
          className={`
            absolute z-50 top-full left-0 right-0 mt-1
            bg-white rounded-lg shadow-lg border border-neutral-200
            overflow-hidden animate-fadeIn
          `}
        >
          {suggestions.map((suggestion, index) => (
            <button
              key={index}
              role="option"
              aria-selected={selectedIndex === index}
              onClick={() => handleSuggestionClick(suggestion)}
              onMouseEnter={() => setSelectedIndex(index)}
              className={`
                w-full px-4 py-2 text-left text-sm
                transition-all duration-[150ms] ease-out
                focus:outline-none focus:bg-neutral-100
                ${selectedIndex === index ? 'bg-neutral-100' : 'bg-white'}
                ${index === suggestions.length - 1 ? 'rounded-b-lg' : ''}
              `}
            >
              <span className="text-neutral-700">{suggestion}</span>
            </button>
          ))}

          {/* Footer hint */}
          <div className="px-4 py-2 bg-neutral-50 text-xs text-neutral-500 border-t border-neutral-100">
            Use arrow keys to navigate, Enter to select
          </div>
        </div>
      )}

      {/* No suggestions message */}
      {showSuggestions && value && suggestions.length === 0 && (
        <div className="absolute z-50 top-full left-0 right-0 mt-1 bg-white rounded-lg shadow-lg border border-neutral-200 p-4 text-center animate-fadeIn">
          <p className="text-sm text-neutral-500">No suggestions found</p>
        </div>
      )}
    </div>
  );
};

export default SearchBar;
