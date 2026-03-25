import { useAuth } from '../contexts/AuthContext';

/**
 * API client with automatic token refresh on 401 Unauthorized responses.
 * 
 * This function wraps fetch calls and automatically attempts to refresh the access token
 * when a 401 response is received, then retries the original request with the new token.
 * 
 * @param url - The URL to fetch
 * @param options - Fetch options (will include credentials: 'include')
 * @returns Promise resolving to the fetch Response
 */
export function useApiClient() {
    const { getAccessToken } = useAuth();

    const fetchWithAuth = async (
        url: string,
        options: RequestInit = {}
    ): Promise<Response> => {
        // Prepare request with credentials and authorization header
        const requestOptions: RequestInit = {
            ...options,
            credentials: 'include' as const,
        };

        // Add access token to Authorization header if available
        const token = getAccessToken();
        if (token) {
            requestOptions.headers = {
                ...requestOptions.headers,
                'Authorization': `Bearer ${token}`,
            };
        }

        let response = await fetch(url, requestOptions);

        // If we get a 401 Unauthorized and we have a token, try to refresh
        if (response.status === 401 && token) {
            console.log('Access token expired, attempting refresh...');
            
            try {
                // Attempt to refresh the token using the refresh token cookie
                const refreshResponse = await fetch('/auth/refresh', {
                    method: 'POST',
                    credentials: 'include' as const,
                });

                if (refreshResponse.ok) {
                    const refreshData = await refreshResponse.json();
                    if (refreshData.accessToken) {
                        // The AuthContext stores the new token in sessionStorage
                        // We need to update our local reference
                        const newToken = refreshData.accessToken;
                        
                        // Retry the original request with the new token
                        const retryOptions: RequestInit = {
                            ...options,
                            credentials: 'include' as const,
                            headers: {
                                ...requestOptions.headers,
                                'Authorization': `Bearer ${newToken}`,
                            },
                        };
                        
                        console.log('Token refreshed successfully, retrying request...');
                        response = await fetch(url, retryOptions);
                    }
                } else {
                    console.error('Token refresh failed:', refreshResponse.status);
                    // Refresh failed - the session may be completely expired
                    // Return the original 401 response so the caller can handle it
                }
            } catch (refreshError) {
                console.error('Error during token refresh:', refreshError);
                // Return the original 401 response
            }
        }

        return response;
    };

    return { fetchWithAuth };
}

/**
 * Standalone fetch function with automatic token refresh.
 * This can be used outside of React components.
 * 
 * @param url - The URL to fetch
 * @param options - Fetch options
 * @param getAccessTokenFn - Function to get the current access token
 * @returns Promise resolving to the fetch Response
 */
export async function fetchWithAuth(
    url: string,
    options: RequestInit = {},
    getAccessTokenFn: () => string | null
): Promise<Response> {
    // Prepare request with credentials and authorization header
    const requestOptions: RequestInit = {
        ...options,
        credentials: 'include' as const,
    };

    // Add access token to Authorization header if available
    const token = getAccessTokenFn();
    if (token) {
        requestOptions.headers = {
            ...requestOptions.headers,
            'Authorization': `Bearer ${token}`,
        };
    }

    let response = await fetch(url, requestOptions);

    // If we get a 401 Unauthorized and we have a token, try to refresh
    if (response.status === 401 && token) {
        console.log('Access token expired, attempting refresh...');
        
        try {
            // Attempt to refresh the token using the refresh token cookie
            const refreshResponse = await fetch('/auth/refresh', {
                method: 'POST',
                credentials: 'include' as const,
            });

            if (refreshResponse.ok) {
                const refreshData = await refreshResponse.json();
                if (refreshData.accessToken) {
                    const newToken = refreshData.accessToken;
                    
                    // Retry the original request with the new token
                    const retryOptions: RequestInit = {
                        ...options,
                        credentials: 'include' as const,
                        headers: {
                            ...requestOptions.headers,
                            'Authorization': `Bearer ${newToken}`,
                        },
                    };
                    
                    console.log('Token refreshed successfully, retrying request...');
                    response = await fetch(url, retryOptions);
                }
            } else {
                console.error('Token refresh failed:', refreshResponse.status);
            }
        } catch (refreshError) {
            console.error('Error during token refresh:', refreshError);
        }
    }

    return response;
}
