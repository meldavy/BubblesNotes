import React, { useState, useEffect } from 'react';
import { UserIcon, ArrowRightOnRectangleIcon } from '@heroicons/react/24/outline';

interface UserInfo {
  email: string;
  name?: string;
  pictureUrl?: string;
}

interface LoginButtonProps {
  onLoginSuccess?: (user: UserInfo) => void;
  onLogoutSuccess?: () => void;
}

export const LoginButton: React.FC<LoginButtonProps> = ({ 
  onLoginSuccess, 
  onLogoutSuccess 
}) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [user, setUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Check authentication status on mount
  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const response = await fetch('/api/v1/auth/me');
      
      if (response.ok) {
        const data = await response.json();
        
        // If authenticated, we need to get user info from the session
        // For now, we'll just mark as authenticated
        setIsAuthenticated(true);
        
        // Try to fetch additional user info if available
        try {
          const userInfoResponse = await fetch('/api/v1/auth/userinfo');
          if (userInfoResponse.ok) {
            const userInfo = await userInfoResponse.json();
            setUser(userInfo);
          }
        } catch (e) {
          // User info endpoint may not be available, that's ok
        }
        
        onLoginSuccess?.(user || { email: 'user@example.com' });
      } else {
        setIsAuthenticated(false);
        setUser(null);
      }
    } catch (error) {
      console.error('Error checking auth status:', error);
      setIsAuthenticated(false);
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogin = () => {
    // Redirect to Google OAuth login
    const redirectUrl = encodeURIComponent(window.location.origin + window.location.pathname);
    window.location.href = `/auth/google?redirect=${redirectUrl}`;
  };

  const handleLogout = async () => {
    try {
      const response = await fetch('/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        setIsAuthenticated(false);
        setUser(null);
        onLogoutSuccess?.();
        
        // Reload page to clear any cached state
        window.location.reload();
      } else {
        console.error('Logout failed');
      }
    } catch (error) {
      console.error('Error during logout:', error);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center w-10 h-10">
        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (isAuthenticated && user) {
    return (
      <div className="flex items-center gap-3">
        {/* User Avatar */}
        {user.pictureUrl ? (
          <img 
            src={user.pictureUrl} 
            alt={user.name || 'User'}
            className="w-8 h-8 rounded-full object-cover border-2 border-blue-500"
          />
        ) : (
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white text-sm font-medium">
            {user.name?.charAt(0) || user.email.charAt(0).toUpperCase()}
          </div>
        )}
        
        {/* User Info */}
        <div className="hidden md:block">
          <p className="text-sm font-medium text-gray-900">{user.name || 'User'}</p>
          <p className="text-xs text-gray-500">{user.email}</p>
        </div>
        
        {/* Logout Button */}
        <button
          onClick={handleLogout}
          className="p-2 rounded-lg hover:bg-red-50 transition-colors group"
          title="Sign out"
        >
          <ArrowRightOnRectangleIcon className="w-5 h-5 text-gray-600 group-hover:text-red-600" />
        </button>
      </div>
    );
  }

  // Login Button
  return (
    <button
      onClick={handleLogin}
      className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-all duration-200 shadow-sm hover:shadow-md"
    >
      {/* Google Icon */}
      <svg className="w-5 h-5" viewBox="0 0 24 24">
        <path
          fill="currentColor"
          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        />
        <path
          fill="currentColor"
          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        />
        <path
          fill="currentColor"
          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
        />
        <path
          fill="currentColor"
          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        />
      </svg>
      
      <span className="font-medium">Sign in with Google</span>
    </button>
  );
};

export default LoginButton;
