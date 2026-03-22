import React, { useState, useEffect } from 'react';
import { LoginButton } from './LoginButton';
import { UserProfile } from './UserProfile';
import { useAuth } from '../contexts/AuthContext';

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
  // Use AuthContext for authentication state
  const { isAuthenticated, isLoading: authLoading } = useAuth();

  // No need to check auth status manually - use AuthContext
  return (
    <header className={`bg-white border-b border-neutral-200 shadow-sm ${className}`}>
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex items-center justify-between h-16">
          {/* Logo/Title */}
          <a href="/" className="flex items-center space-x-3 group">
            <img
              src="/bubblesnotes.png"
              alt="BubblesNotes Logo"
              className="w-8 h-8 rounded-full object-cover"
            />
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
              {authLoading ? (
                // Loading state
                <div className="w-8 h-8 flex items-center justify-center">
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600"></div>
                </div>
              ) : showAuth ? (
                // Show login button for landing page
                <LoginButton />
              ) : isAuthenticated ? (
                // Show user profile dropdown for authenticated users
                <UserProfile />
              ) : (
                // Fallback to custom actions if provided
                actions
              )}
           </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
