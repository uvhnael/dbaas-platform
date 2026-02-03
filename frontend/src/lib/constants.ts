/**
 * Application Constants
 * Centralized configuration for the frontend application
 */

export const POLLING_INTERVALS = {
  // Fast polling for active states (provisioning, deleting, scaling)
  FAST: 5000, // 5 seconds
  
  // Moderate polling for metrics and stats
  MODERATE: 8000, // 8 seconds,
  
  // Standard polling for lists and stable states
  STANDARD: 10000, // 10 seconds
  
  // Slow polling for stable resources that rarely change
  SLOW: 30000, // 30 seconds
};

export const AUTH = {
  TOKEN_KEY: 'auth_token',
  PUBLIC_ROUTES: ['/login', '/register'],
};

export const CLUSTER_STATUS = {
  RUNNING: 'RUNNING',
  PROVISIONING: 'PROVISIONING',
  DELETING: 'DELETING',
  DEGRADED: 'DEGRADED',
  STOPPED: 'STOPPED',
  FAILED: 'FAILED',
} as const;
