import React from 'react';

export interface LayoutProps {
  /** Main content to render */
  children: React.ReactNode;
  
  /** Optional header component */
  header?: React.ReactNode;
  
  /** Additional CSS classes for container */
  className?: string;
}

/**
 * Dashboard layout wrapper with responsive grid
 * Provides consistent spacing and max-width constraints
 */
export const Layout: React.FC<LayoutProps> = ({
  children,
  header,
  className = '',
}) => {
  return (
    <div className="min-h-screen bg-neutral-50">
      {/* Header/Navigation */}
      {header && (
        <header className="bg-white border-b border-neutral-200 shadow-sm sticky top-0 z-sticky">
          {header}
        </header>
      )}

      {/* Main Content Container */}
      <main className={`container mx-auto px-4 py-6 ${className}`}>
        <div className="max-w-7xl mx-auto">
          {children}
        </div>
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-neutral-200 mt-auto">
        <div className="container mx-auto px-4 py-4">
          <div className="max-w-7xl mx-auto text-center text-sm text-neutral-500">
            BubblesNotes &copy; {new Date().getFullYear()}
          </div>
        </div>
      </footer>
    </div>
  );
};

/**
 * Responsive container component for consistent max-width layouts
 */
export const Container: React.FC<{
  children: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  className?: string;
}> = ({ children, size = 'lg', className = '' }) => {
  const sizeClasses = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
  };

  return (
    <div className={`w-full ${sizeClasses[size]} mx-auto ${className}`}>
      {children}
    </div>
  );
};

/**
 * Grid container for responsive layouts
 */
export const Grid: React.FC<{
  children: React.ReactNode;
  cols?: 1 | 2 | 3 | 4;
  gap?: 'sm' | 'md' | 'lg';
  className?: string;
}> = ({ children, cols = 2, gap = 'md', className = '' }) => {
  const colClasses = {
    1: 'grid-cols-1',
    2: 'grid-cols-1 md:grid-cols-2',
    3: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
    4: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
  };

  const gapClasses = {
    sm: 'gap-4',
    md: 'gap-6',
    lg: 'gap-8',
  };

  return (
    <div className={`grid ${colClasses[cols]} ${gapClasses[gap]} ${className}`}>
      {children}
    </div>
  );
};

export default Layout;
