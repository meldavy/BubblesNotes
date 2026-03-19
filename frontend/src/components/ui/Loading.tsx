import React from 'react';

export interface SpinnerProps {
  /** Size of spinner */
  size?: 'sm' | 'md' | 'lg';
  
  /** Color variant */
  color?: 'primary' | 'white';
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Spinner component for loading states
 */
export const Spinner: React.FC<SpinnerProps> = ({
  size = 'md',
  color = 'primary',
  className = '',
}) => {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  };

  const colorClasses = {
    primary: 'text-primary-500',
    white: 'text-white',
  };

  return (
    <div className={`flex items-center justify-center ${className}`}>
      <svg
        className={`${sizeClasses[size]} animate-spin`}
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
          className={`opacity-75 ${colorClasses[color]}`}
          fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
        />
      </svg>
    </div>
  );
};

// ========================================
// Skeleton Loader for Cards
// ========================================

export interface SkeletonCardProps {
  /** Number of skeleton cards to render */
  count?: number;
  
  /** Additional CSS classes */
  className?: string;
}

export const SkeletonCard: React.FC<SkeletonCardProps> = ({
  count = 1,
  className = '',
}) => {
  return (
    <div className={`bg-neutral-100 rounded-lg border border-neutral-200 shadow-sm animate-pulse ${className}`}>
      {/* Header skeleton */}
      <div className="px-5 py-4 border-b border-neutral-200">
        <div className="h-5 bg-neutral-300 rounded w-3/4 mb-2"></div>
        <div className="h-4 bg-neutral-300 rounded w-1/2"></div>
      </div>
      
      {/* Body skeleton */}
      <div className="px-5 py-4 space-y-3">
        <div className="h-4 bg-neutral-300 rounded w-full"></div>
        <div className="h-4 bg-neutral-300 rounded w-5/6"></div>
        <div className="h-4 bg-neutral-300 rounded w-2/3"></div>
      </div>
    </div>
  );
};

export const SkeletonCards: React.FC<SkeletonCardProps> = ({
  count = 3,
  className = '',
}) => {
  return (
    <div className={`space-y-4 ${className}`}>
      {Array.from({ length: count }).map((_, index) => (
        <SkeletonCard key={index} />
      ))}
    </div>
  );
};

// ========================================
// Inline Loading Indicator
// ========================================

export interface InlineLoadingProps {
  /** Message to display */
  message?: string;
  
  /** Size of spinner */
  size?: 'sm' | 'md' | 'lg';
}

export const InlineLoading: React.FC<InlineLoadingProps> = ({
  message,
  size = 'sm',
}) => {
  return (
    <div className="flex items-center justify-center py-8">
      <Spinner size={size} />
      {message && (
        <span className="ml-3 text-neutral-600">{message}</span>
      )}
    </div>
  );
};

export default { Spinner, SkeletonCard, SkeletonCards, InlineLoading };
