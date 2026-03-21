import React from 'react';

export interface ButtonProps {
  /** Button variant style */
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  
  /** Button size */
  size?: 'sm' | 'md' | 'lg';
  
  /** Show loading spinner */
  loading?: boolean;
  
  /** Disable button */
  disabled?: boolean;
  
  /** Icon to display before text */
  icon?: React.ReactNode;
  
  /** Button content */
  children: React.ReactNode;
  
  /** Click handler */
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  
  /** Type attribute for form submission */
  type?: 'button' | 'submit' | 'reset';
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Button component with multiple variants and sizes
 * Supports loading state, icons, and accessibility features
 */
export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  icon,
  children,
  onClick,
  type = 'button',
  className = '',
}) => {
  // Size mappings
  const sizeClasses = {
    sm: 'h-10 px-3 py-0 text-sm',
    md: 'h-11 px-4 text-base',
    lg: 'h-12 px-6 text-lg',
  };

  // Variant mappings
  const variantClasses = {
    primary: `
      bg-primary-500 
      hover:bg-primary-600 
      active:bg-primary-700
      text-white 
      shadow-md 
      hover:shadow-lg
      focus:ring-2 
      focus:ring-primary-500 
      focus:ring-offset-2
    `,
    secondary: `
      bg-neutral-100 
      hover:bg-neutral-200 
      active:bg-neutral-300
      text-neutral-700 
      border 
      border-neutral-300
      shadow-sm 
      hover:shadow-md
      focus:ring-2 
      focus:ring-neutral-400 
      focus:ring-offset-2
    `,
    ghost: `
      bg-transparent 
      hover:bg-neutral-100 
      active:bg-neutral-200
      text-neutral-700
      focus:ring-2 
      focus:ring-neutral-400 
      focus:ring-offset-2
    `,
    danger: `
      bg-error-500 
      hover:bg-error-600 
      active:bg-error-700
      text-white 
      shadow-md 
      hover:shadow-lg
      focus:ring-2 
      focus:ring-error-500 
      focus:ring-offset-2
    `,
  };

  // Base classes
  const baseClasses = `
    inline-flex 
    items-center 
    justify-center 
    font-medium 
    leading-none
    rounded-lg 
    transition-all 
    duration-normal 
    ease-out
    disabled:opacity-50 
    disabled:cursor-not-allowed
    disabled:hover:bg-inherit 
    disabled:hover:shadow-inherit
  `;

  return (
    <button
      type={type}
      className={`${baseClasses} ${sizeClasses[size]} ${variantClasses[variant]} ${className}`}
      disabled={disabled || loading}
      onClick={onClick}
    >
      {loading ? (
        <>
          <svg
            className="animate-spin -ml-1 mr-2 h-4 w-4"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
          Loading...
        </>
      ) : (
        <>
          {icon && <span className="mr-2">{icon}</span>}
          {children}
        </>
      )}
    </button>
  );
};

export default Button;
