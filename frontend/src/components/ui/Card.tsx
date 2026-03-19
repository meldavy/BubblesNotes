import React from 'react';

export interface CardProps {
  /** Card title */
  title?: string;
  
  /** Card subtitle/description */
  subtitle?: string;
  
  /** Action buttons to display in header */
  actions?: React.ReactNode;
  
  /** Card content */
  children: React.ReactNode;
  
  /** Enable hover effect with shadow */
  hoverable?: boolean;
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Card component with optional title, subtitle, and actions
 * Supports hover effects for interactive cards
 */
export const Card: React.FC<CardProps> = ({
  title,
  subtitle,
  actions,
  children,
  hoverable = false,
  className = '',
}) => {
  return (
    <div
      className={`
        bg-neutral-100 
        rounded-lg 
        border 
        border-neutral-200 
        shadow-sm
        transition-all 
        duration-normal 
        ease-out
        ${hoverable
          ? 'hover:shadow-lg hover:-translate-y-1 cursor-pointer'
          : ''
        }
        ${className}
      `}
    >
      {/* Card Header */}
      {(title || subtitle || actions) && (
        <div className="px-5 py-4 border-b border-neutral-200">
          <div className="flex items-start justify-between">
            <div className="flex-1 min-w-0">
              {title && (
                <h3 className="text-lg font-semibold text-neutral-800 truncate">
                  {title}
                </h3>
              )}
              {subtitle && (
                <p className="mt-1 text-sm text-neutral-500 line-clamp-2">
                  {subtitle}
                </p>
              )}
            </div>
            
            {/* Actions */}
            {actions && (
              <div className="flex items-center ml-4 space-x-2">
                {actions}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Card Body */}
      <div className="px-5 py-4">
        {children}
      </div>
    </div>
  );
};

export default Card;
