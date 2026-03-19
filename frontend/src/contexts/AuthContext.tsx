import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface UserInfo {
  userId?: string;
  email: string;
  name?: string;
  pictureUrl?: string;
}

interface AuthContextType {
  isAuthenticated: boolean;
  user: UserInfo | null;
  isLoading: boolean;
  login: () => void;
  logout: () => void;
  error: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [user, setUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Check authentication status on mount
  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      setIsLoading(true);
      const response = await fetch('/api/v1/auth/me', {
        credentials: 'include' // Include session cookie for authentication
      });
      
      if (response.ok) {
        const data = await response.json();
        
        if (data.authenticated) {
          setIsAuthenticated(true);
          setUser({
            userId: data.userId,
            email: data.email || 'user@example.com',
            name: data.name,
            pictureUrl: data.pictureUrl
          });
        } else {
          setIsAuthenticated(false);
          setUser(null);
        }
      } else if (response.status === 401) {
        // Session expired - clear auth state
        setIsAuthenticated(false);
        setUser(null);
        setError('Session expired. Please sign in again.');
      }
    } catch (error) {
      console.error('Error checking auth status:', error);
      setIsAuthenticated(false);
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  };

  const login = () => {
    // Redirect to Google OAuth login
    const redirectUrl = encodeURIComponent(window.location.origin + window.location.pathname);
    window.location.href = `/auth/google?redirect=${redirectUrl}`;
  };

  const logout = async () => {
    try {
      const response = await fetch('/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include' // Include session cookie for authentication
      });

      if (response.ok) {
        setIsAuthenticated(false);
        setUser(null);
        setError(null);
        // Reload page to clear any cached state
        window.location.reload();
      } else {
        console.error('Logout failed');
      }
    } catch (error) {
      console.error('Error during logout:', error);
    }
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, isLoading, login, logout, error }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
