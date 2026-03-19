import React from 'react';
import { LoginButton } from './LoginButton';

export interface HeaderProps {
  /** App title/logo text */
  title?: string;
  
  /** Navigation links */
  navLinks?: Array<{ label: string; href: string; active?: boolean }>;
  
  /** Right-side actions (e.g., user menu, login button) */
  actions?: React.ReactNode;
  
  /** Show authentication button instead of custom actions */
  showAuth?: boolean;
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Navigation header component with responsive design
 */
export const Header: React.FC<HeaderProps> = ({
  title = 'BubblesNotes',
  navLinks = [],
  actions,
  showAuth = false,
  className = '',
}) => {
  return (
    <header className={`bg-white border-b border-neutral-200 shadow-sm ${className}`}>
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between h-16">
          {/* Logo/Title */}
          <a href="/" className="flex items-center space-x-3 group">
            <div className="w-8 h-8 bg-primary-500 rounded-lg flex items-center justify-center">
              <svg
                className="w-5 h-5 text-white"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M8 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5H9z"
                />
              </svg>
            </div>
            <span className="text-xl font-bold text-neutral-800 group-hover:text-primary-600 transition-colors duration-fast">
              {title}
            </span>
          </a>

          {/* Navigation Links */}
          {navLinks.length > 0 && (
            <nav className="hidden md:flex items-center space-x-1">
              {navLinks.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className={`
                    px-4 py-2 rounded-md text-sm font-medium transition-all duration-fast
                    ${link.active
                      ? 'bg-primary-100 text-primary-700'
                      : 'text-neutral-600 hover:bg-neutral-100 hover:text-neutral-800'
                    }
                  `}
                >
                  {link.label}
                </a>
              ))}
            </nav>
          )}

          {/* Right-side Actions */}
          <div className="flex items-center space-x-3">
            {showAuth ? (
              <LoginButton />
            ) : (
              actions
            )}
            
            {/* Mobile Menu Button (visible on small screens) */}
            <button
              className="md:hidden p-2 rounded-md text-neutral-600 hover:bg-neutral-100 transition-colors duration-fast"
              aria-label="Open menu"
            >
              <svg
                className="w-6 h-6"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 6h16M4 12h16M4 18h16"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
