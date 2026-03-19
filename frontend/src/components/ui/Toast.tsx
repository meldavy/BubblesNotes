import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

// ========================================
// Toast Types
// ========================================

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}

interface ToastContextType {
  addToast: (message: string, type?: ToastType, duration?: number) => void;
  removeToast: (id: string) => void;
}

// ========================================
// Toast Context
// ========================================

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};

// ========================================
// Toast Provider Component
// ========================================

export interface ToastProviderProps {
  children: React.ReactNode;
}

export const ToastProvider: React.FC<ToastProviderProps> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((message: string, type: ToastType = 'info', duration = 5000) => {
    const id = Math.random().toString(36).substr(2, 9);
    const newToast: Toast = { id, message, type, duration };
    
    setToasts((prev) => [...prev, newToast]);

    // Auto-remove after duration
    if (duration > 0) {
      setTimeout(() => removeToast(id), duration);
    }
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ addToast, removeToast }}>
      {children}
      
      {/* Toast Container */}
      <div className="fixed top-4 right-4 z-tooltip space-y-3">
        {toasts.map((toast) => (
          <ToastItem
            key={toast.id}
            toast={toast}
            onClose={() => removeToast(toast.id)}
          />
        ))}
      </div>
    </ToastContext.Provider>
  );
};

// ========================================
// Toast Item Component
// ========================================

interface ToastItemProps {
  toast: Toast;
  onClose: () => void;
}

const ToastItem: React.FC<ToastItemProps> = ({ toast, onClose }) => {
  const [isVisible, setIsVisible] = useState(true);

  useEffect(() => {
    if (!isVisible) {
      const timer = setTimeout(() => {
        onClose();
      }, 300); // Wait for animation to complete
      return () => clearTimeout(timer);
    }
  }, [isVisible, onClose]);

  // Type styles
  const typeStyles: Record<ToastType, string> = {
    success: 'bg-success-bg border-success-500 text-success-600',
    error: 'bg-error-bg border-error-500 text-error-600',
    warning: 'bg-warning-bg border-warning-500 text-warning-600',
    info: 'bg-info-bg border-primary-500 text-primary-600',
  };

  // Type icons
  const typeIcons: Record<ToastType, React.ReactNode> = {
    success: (
      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
        <path
          fillRule="evenodd"
          d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
          clipRule="evenodd"
        />
      </svg>
    ),
    error: (
      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
        <path
          fillRule="evenodd"
          d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
          clipRule="evenodd"
        />
      </svg>
    ),
    warning: (
      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
        <path
          fillRule="evenodd"
          d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
          clipRule="evenodd"
        />
      </svg>
    ),
    info: (
      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
        <path
          fillRule="evenodd"
          d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
          clipRule="evenodd"
        />
      </svg>
    ),
  };

  return (
    <div
      className={`
        flex items-center p-4 rounded-lg shadow-lg border-l-4 min-w-[320px] max-w-md
        transform transition-all duration-normal ease-out
        ${isVisible ? 'animate-slide-in opacity-100 translate-x-0' : 'opacity-0 translate-x-full'}
        ${typeStyles[toast.type]}
      `}
      role="alert"
    >
      {/* Icon */}
      <div className="flex-shrink-0 mr-3">
        {typeIcons[toast.type]}
      </div>

      {/* Message */}
      <div className="flex-1 text-sm font-medium">{toast.message}</div>

      {/* Close button */}
      <button
        onClick={() => setIsVisible(false)}
        className="ml-3 p-1 rounded hover:bg-neutral-200 transition-colors duration-fast"
        aria-label="Close notification"
      >
        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
          <path
            fillRule="evenodd"
            d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
            clipRule="evenodd"
          />
        </svg>
      </button>
    </div>
  );
};

// ========================================
// Convenience Hooks
// ========================================

export const useToastNotifications = () => {
  const context = useContext(ToastContext);
  
  if (!context) {
    throw new Error('useToastNotifications must be used within a ToastProvider');
  }

  return {
    success: (message: string, duration?: number) => 
      context.addToast(message, 'success', duration),
    error: (message: string, duration?: number) => 
      context.addToast(message, 'error', duration),
    warning: (message: string, duration?: number) => 
      context.addToast(message, 'warning', duration),
    info: (message: string, duration?: number) => 
      context.addToast(message, 'info', duration),
  };
};

export default ToastProvider;
