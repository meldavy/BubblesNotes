import React, { useState } from 'react';

export interface InputProps {
  /** Label text */
  label?: string;
  
  /** Placeholder text */
  placeholder?: string;
  
  /** Current value */
  value: string;
  
  /** Change handler */
  onChange: (value: string) => void;
  
  /** Error message to display */
  error?: string;
  
  /** Disable input */
  disabled?: boolean;
  
  /** Input type */
  type?: 'text' | 'email' | 'password';
  
  /** Unique ID for the input */
  id?: string;
  
  /** Additional CSS classes */
  className?: string;
  
  /** Callback on blur */
  onBlur?: (event: React.FocusEvent<HTMLInputElement>) => void;
  
  /** Callback on focus */
  onFocus?: (event: React.FocusEvent<HTMLInputElement>) => void;
}

/**
 * Input component with validation states and accessibility support
 */
export const Input: React.FC<InputProps> = ({
  label,
  placeholder,
  value,
  onChange,
  error,
  disabled = false,
  type = 'text',
  id,
  className = '',
  onBlur,
  onFocus,
}) => {
  const [isFocused, setIsFocused] = useState(false);

  // Generate unique ID if not provided
  const inputId = id || `input-${Math.random().toString(36).substr(2, 9)}`;

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange(event.target.value);
  };

  return (
    <div className={`w-full ${className}`}>
      {/* Label */}
      {label && (
        <label
          htmlFor={inputId}
          className="block text-sm font-medium text-neutral-700 mb-1"
        >
          {label}
        </label>
      )}

      {/* Input wrapper */}
      <div className="relative">
        <input
          id={inputId}
          type={type}
          value={value}
          placeholder={placeholder}
          disabled={disabled}
          onChange={handleChange}
          onBlur={(e) => {
            setIsFocused(false);
            onBlur?.(e);
          }}
          onFocus={(e) => {
            setIsFocused(true);
            onFocus?.(e);
          }}
          aria-invalid={!!error}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className={`
            w-full 
            px-4 
            py-2 
            text-base
            rounded-md 
            border 
            transition-all 
            duration-fast 
            ease-out
            bg-white 
            disabled:bg-neutral-100 
            disabled:cursor-not-allowed
            ${error
              ? 'border-error-500 focus:border-error-600 focus:ring-2 focus:ring-error-200'
              : isFocused
                ? 'border-primary-500 ring-2 ring-primary-100'
                : 'border-neutral-300 hover:border-neutral-400'
            }
          `}
        />

        {/* Error message */}
        {error && (
          <div
            id={`${inputId}-error`}
            className="mt-1 flex items-center text-sm text-error-600"
            role="alert"
          >
            <svg
              className="w-4 h-4 mr-1"
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                fillRule="evenodd"
                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
                clipRule="evenodd"
              />
            </svg>
            {error}
          </div>
        )}
      </div>
    </div>
  );
};

export default Input;
