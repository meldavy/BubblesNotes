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
  getAccessToken: () => string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Store access token in memory (not in localStorage/sessionStorage for better security)
let accessToken: string | null = null;

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [user, setUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Check authentication status on mount
  useEffect(() => {
    // Check if we already have a stored access token
    const storedToken = sessionStorage.getItem('access_token');
    if (storedToken) {
      accessToken = storedToken;
      console.log('Access token loaded from sessionStorage');
    }
    
    // Extract access token from URL fragment after OAuth callback
    const hash = window.location.hash;
    if (hash && hash.includes('accessToken=')) {
      const match = hash.match(/accessToken=([^&]+)/);
      if (match && match[1]) {
        accessToken = match[1];
        // Store in sessionStorage for persistence across page reloads
        sessionStorage.setItem('access_token', accessToken);
        console.log('Access token extracted from URL fragment and stored');
        // Clear the hash from the URL without reloading
        window.history.replaceState({}, document.title, window.location.pathname);
      }
    }
    
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      setIsLoading(true);
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      
      // Include access token in Authorization header if available
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }
      
      const response = await fetch('/api/v1/auth/me', {
        headers,
        credentials: 'include' // Include refresh token cookie
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
        // Token expired or missing - try to refresh using the refresh token cookie
        // Note: We attempt refresh regardless of whether accessToken exists,
        // because in a new tab, accessToken will be null but the refresh token cookie is still available
        try {
          const refreshResponse = await fetch('/auth/refresh', {
            method: 'POST',
            credentials: 'include'
          });
          
          if (refreshResponse.ok) {
            const refreshData = await refreshResponse.json();
            if (refreshData.accessToken) {
              accessToken = refreshData.accessToken;
              // Store new access token in sessionStorage
              if (accessToken) {
                sessionStorage.setItem('access_token', accessToken);
              }
              // Retry the auth check with new token
              const retryHeaders: Record<string, string> = {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
              };
              const retryResponse = await fetch('/api/v1/auth/me', {
                headers: retryHeaders,
                credentials: 'include'
              });
              
              if (retryResponse.ok) {
                const data = await retryResponse.json();
                if (data.authenticated) {
                  setIsAuthenticated(true);
                  setUser({
                    userId: data.userId,
                    email: data.email || 'user@example.com',
                    name: data.name,
                    pictureUrl: data.pictureUrl
                  });
                  return;
                }
              }
            }
          }
        } catch (refreshError) {
          console.error('Token refresh failed:', refreshError);
        }
        
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
        credentials: 'include' // Include refresh token cookie
      });

      if (response.ok) {
        // Clear access token from memory and sessionStorage
        accessToken = null;
        sessionStorage.removeItem('access_token');
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

  const getAccessToken = () => accessToken;

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, isLoading, login, logout, error, getAccessToken }}>
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
