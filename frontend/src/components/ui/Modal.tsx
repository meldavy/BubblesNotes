import React, { useEffect, useRef } from 'react';

export interface ModalProps {
  /** Whether modal is open */
  isOpen: boolean;
  
  /** Close handler */
  onClose: () => void;
  
  /** Modal title */
  title?: string;
  
  /** Modal content */
  children: React.ReactNode;
  
  /** Modal size */
  size?: 'sm' | 'md' | 'lg';
  
  /** Show close button (X) */
  showCloseButton?: boolean;
  
  /** Callback when backdrop is clicked */
  onBackdropClick?: () => void;
}

/**
 * Modal/Dialog component with animations and focus management
 */
export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
  size = 'md',
  showCloseButton = true,
  onBackdropClick,
}) => {
  const modalRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  // Size mappings
  const sizeClasses = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
  };

  // Handle escape key press
  useEffect(() => {
    if (!isOpen) return;

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscape);
    
    // Store current focus and move to modal
    previousActiveElement.current = document.activeElement as HTMLElement;
    const firstFocusable = modalRef.current?.querySelector(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    ) as HTMLElement;
    firstFocusable?.focus();

    return () => {
      document.removeEventListener('keydown', handleEscape);
      // Restore focus to previous element
      previousActiveElement.current?.focus();
    };
  }, [isOpen, onClose]);

  // Prevent body scroll when modal is open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }

    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  // Don't render if not open
  if (!isOpen) return null;

  const handleBackdropClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) {
      onBackdropClick?.();
      onClose();
    }
  };

  return (
    <div
      className="fixed inset-0 z-modal flex items-center justify-center overflow-y-auto overflow-x-hidden"
      role="dialog"
      aria-modal="true"
      onClick={handleBackdropClick}
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-neutral-900 opacity-50 transition-opacity duration-normal animate-fade-in" />

      {/* Modal Content */}
      <div
        ref={modalRef}
        className={`
          relative 
          w-full 
          ${sizeClasses[size]} 
          mx-4 
          bg-white 
          rounded-lg 
          shadow-xl 
          transform 
          transition-all 
          duration-normal 
          ease-out
          animate-slide-up
        `}
      >
        {/* Header */}
        {(title || showCloseButton) && (
          <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-200">
            {title && (
              <h2 className="text-lg font-semibold text-neutral-800">
                {title}
              </h2>
            )}
            
            {showCloseButton && (
              <button
                onClick={onClose}
                className="p-1 rounded-md text-neutral-500 hover:text-neutral-700 hover:bg-neutral-100 transition-colors duration-fast"
                aria-label="Close modal"
              >
                <svg
                  className="w-5 h-5"
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
          </div>
        )}

        {/* Body */}
        <div className="px-6 py-4">
          {children}
        </div>
      </div>
    </div>
  );
};

export default Modal;
