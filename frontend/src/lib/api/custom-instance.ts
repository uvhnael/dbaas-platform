/**
 * Custom fetch instance for API calls.
 * Used by Orval generated hooks.
 * Handles ApiResponse format from backend.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
  message?: string;
  timestamp: string;
}

export const customInstance = async <T>({
  url,
  method,
  params,
  data,
  headers,
  signal,
}: {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  params?: Record<string, unknown>;
  data?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}): Promise<T> => {
  // Get auth token from localStorage
  const token = typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null;

  const authHeaders: Record<string, string> = {};
  if (token) {
    authHeaders['Authorization'] = `Bearer ${token}`;
  }

  // Build URL with query params
  const queryString = params
    ? '?' + new URLSearchParams(params as Record<string, string>).toString()
    : '';

  // Debug log
  console.log(`[API] ${method} ${url}`, { data, headers: { ...authHeaders, ...headers } });

  const response = await fetch(`${API_BASE_URL}${url}${queryString}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders,
      ...headers,
    },
    body: data ? JSON.stringify(data) : undefined,
    signal,
  });

  // Parse response body
  const text = await response.text();
  const body: ApiResponse<T> = text ? JSON.parse(text) : { success: true };

  // Debug log response
  console.log(`[API] ${method} ${url} => ${response.status}`, body);

  // Handle error responses
  if (!response.ok || !body.success) {
    const errorMessage = body.error?.message || body.message || `HTTP ${response.status}`;
    const error = new Error(errorMessage);
    (error as any).code = body.error?.code || 'UNKNOWN_ERROR';
    (error as any).status = response.status;
    throw error;
  }

  // Return the full ApiResponse (Orval types expect this structure)
  return body as unknown as T;
};

export default customInstance;
