'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useGetCurrentUser, useLogin, useRegister } from '@/lib/api';
import type { UserDto, LoginRequest, RegisterRequest } from '@/lib/api/model';

interface AuthContextType {
  user: UserDto | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  token: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [token, setToken] = useState<string | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);

  // Load token from localStorage on mount
  useEffect(() => {
    const storedToken = localStorage.getItem('auth_token');
    setToken(storedToken);
    setIsInitialized(true);
  }, []);

  // Fetch current user when token exists
  const { data: userResponse, isLoading: isLoadingUser, refetch } = useGetCurrentUser({
    query: {
      enabled: !!token && isInitialized,
      retry: false,
    },
  });

  const user = userResponse?.data || null;

  const loginMutation = useLogin();
  const registerMutation = useRegister();

  const login = useCallback(async (data: LoginRequest) => {
    return new Promise<void>((resolve, reject) => {
      loginMutation.mutate(
        { data },
        {
          onSuccess: (response) => {
            if (response.data?.token) {
              localStorage.setItem('auth_token', response.data.token);
              setToken(response.data.token);
              refetch();
              resolve();
            } else {
              reject(new Error('No token received'));
            }
          },
          onError: (error) => {
            reject(error);
          },
        }
      );
    });
  }, [loginMutation, refetch]);

  const register = useCallback(async (data: RegisterRequest) => {
    return new Promise<void>((resolve, reject) => {
      registerMutation.mutate(
        { data },
        {
          onSuccess: (response) => {
            if (response.data?.token) {
              localStorage.setItem('auth_token', response.data.token);
              setToken(response.data.token);
              refetch();
              resolve();
            } else {
              reject(new Error('No token received'));
            }
          },
          onError: (error) => {
            reject(error);
          },
        }
      );
    });
  }, [registerMutation, refetch]);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    setToken(null);
    router.push('/login');
  }, [router]);

  const value: AuthContextType = {
    user,
    isLoading: !isInitialized || (!!token && isLoadingUser),
    isAuthenticated: !!user,
    login,
    register,
    logout,
    token,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
