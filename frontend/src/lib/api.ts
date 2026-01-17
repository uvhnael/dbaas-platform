/**
 * API Hooks - Uses Orval-generated hooks with real backend
 * 
 * Re-exports the generated hooks for easy consumption.
 * Run `npm run generate-api` to regenerate after backend changes.
 */

// Re-export all generated hooks
export * from './api/generated/clusters/clusters';
export * from './api/generated/authentication/authentication';
export * from './api/generated/nodes/nodes';
export * from './api/generated/tasks/tasks';
export * from './api/generated/backups/backups';
export * from './api/generated/dashboard/dashboard';

// Re-export models
export * from './api/model';

// Legacy wrapper hooks for backward compatibility
// These map the old hook names to new generated ones

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  useListClusters, 
  useGetCluster as useGetClusterGenerated,
  useCreateCluster as useCreateClusterGenerated,
  useDeleteCluster as useDeleteClusterGenerated,
  useStartCluster,
  useStopCluster,
  useScaleCluster,
  useGetClusterMetrics,
  useGetClusterHealth,
} from './api/generated/clusters/clusters';
import { 
  useLogin as useLoginGenerated,
  useRegister as useRegisterGenerated,
  useGetCurrentUser,
} from './api/generated/authentication/authentication';
import { 
  useGetNode, 
  useGetNodeLogs,
  useGetClusterLogs,
  useListNodes,
  useGetNodeStats,
  useGetAllNodesStats,
} from './api/generated/nodes/nodes';
import {
  useGetDashboardSummary,
} from './api/generated/dashboard/dashboard';
import {
  useListClusterTasks,
  useListAllTasks,
  useGetTask,
} from './api/generated/tasks/tasks';
import {
  useListBackups,
  useCreateBackup as useCreateBackupGenerated,
  useDeleteBackup as useDeleteBackupGenerated,
  useRestoreBackup,
} from './api/generated/backups/backups';
import { toast } from 'sonner';
import { customInstance } from './api/custom-instance';

// ============================================
// Cluster Hooks (backward compatible)
// ============================================

/**
 * Fetch all clusters for the current user
 * Smart polling: 5s when clusters are in active states, 30s otherwise
 */
export function useClusters() {
  const query = useListClusters({
    query: {
      refetchInterval: (query) => {
        const clusters = query.state.data?.data || [];
        // Fast polling if any cluster is in an active/transitional state
        const hasActiveCluster = clusters.some(c => 
          c.status && ['PROVISIONING', 'DELETING', 'DEGRADED'].includes(c.status)
        );
        return hasActiveCluster ? 5000 : 30000; // 5s or 30s
      },
      refetchOnWindowFocus: true,
    },
  });
  
  // Transform response to extract data
  return {
    ...query,
    data: query.data?.data || [],
  };
}

/**
 * Fetch a single cluster by ID
 */
export function useCluster(id: string) {
  const query = useGetClusterGenerated(id, {
    query: {
      enabled: !!id,
      refetchInterval: (query) => {
        const status = query.state.data?.data?.status;
        // Fast polling for active states
        if (status && ['PROVISIONING', 'DELETING', 'DEGRADED'].includes(status)) {
          return 5000;
        }
        // Slow polling for stable states
        return 15000;
      },
    },
  });

  return {
    ...query,
    data: query.data?.data,
  };
}

/**
 * Fetch cluster nodes
 */
export function useClusterNodes(clusterId: string) {
  const query = useListNodes(clusterId, {
    query: {
      enabled: !!clusterId,
    },
  });

  return {
    ...query,
    data: query.data?.data || [],
  };
}

/**
 * Fetch cluster metrics
 */
export function useClusterMetricsHook(clusterId: string) {
  const query = useGetClusterMetrics(clusterId, {
    query: {
      enabled: !!clusterId,
      refetchInterval: 5000,
    },
  });

  return {
    ...query,
    data: query.data?.data,
  };
}

/**
 * Fetch cluster metrics (alias for backward compatibility)
 */
export const useClusterMetrics = useClusterMetricsHook;

/**
 * Create a new cluster
 */
export function useCreateClusterMutation() {
  const queryClient = useQueryClient();
  const mutation = useCreateClusterGenerated();

  return {
    ...mutation,
    mutate: (data: { name: string; mysqlVersion?: string; replicaCount?: number }) => {
      toast.loading('Creating cluster...', { id: 'create-cluster' });
      
      mutation.mutate(
        { data: { name: data.name, mysqlVersion: data.mysqlVersion, replicaCount: data.replicaCount } },
        {
          onSuccess: () => {
            toast.success('Cluster created successfully!', { id: 'create-cluster' });
            queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
          },
          onError: (error) => {
            toast.error(`Failed to create cluster: ${error.message}`, { id: 'create-cluster' });
          },
        }
      );
    },
  };
}

/**
 * Delete a cluster
 */
export function useDeleteClusterMutation() {
  const queryClient = useQueryClient();
  const mutation = useDeleteClusterGenerated();

  return {
    ...mutation,
    mutate: (id: string) => {
      toast.loading('Deleting cluster...', { id: `delete-${id}` });
      
      mutation.mutate(
        { id },
        {
          onSuccess: () => {
            toast.success('Cluster deleted!', { id: `delete-${id}` });
            queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
          },
          onError: (error) => {
            toast.error(`Failed to delete: ${error.message}`, { id: `delete-${id}` });
          },
        }
      );
    },
  };
}

/**
 * Delete a cluster (alias for backward compatibility)
 */
export const useDeleteCluster = useDeleteClusterMutation;

/**
 * Start a cluster
 */
export function useStartClusterMutation() {
  const queryClient = useQueryClient();
  const mutation = useStartCluster();

  return {
    ...mutation,
    mutate: (id: string) => {
      toast.loading('Starting cluster...', { id: `start-${id}` });
      
      mutation.mutate(
        { id },
        {
          onSuccess: () => {
            toast.success('Cluster started!', { id: `start-${id}` });
            queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
          },
          onError: (error) => {
            toast.error(`Failed to start: ${error.message}`, { id: `start-${id}` });
          },
        }
      );
    },
  };
}

/**
 * Stop a cluster
 */
export function useStopClusterMutation() {
  const queryClient = useQueryClient();
  const mutation = useStopCluster();

  return {
    ...mutation,
    mutate: (id: string) => {
      toast.loading('Stopping cluster...', { id: `stop-${id}` });
      
      mutation.mutate(
        { id },
        {
          onSuccess: () => {
            toast.success('Cluster stopped!', { id: `stop-${id}` });
            queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
          },
          onError: (error) => {
            toast.error(`Failed to stop: ${error.message}`, { id: `stop-${id}` });
          },
        }
      );
    },
  };
}

/**
 * Restart a cluster (stop then start)
 */
export function useRestartCluster() {
  const queryClient = useQueryClient();
  const stopMutation = useStopCluster();
  const startMutation = useStartCluster();

  return {
    isPending: stopMutation.isPending || startMutation.isPending,
    mutateAsync: async (id: string) => {
      toast.loading('Restarting cluster...', { id: `restart-${id}` });
      
      try {
        // Stop the cluster
        await stopMutation.mutateAsync({ id });
        
        // Wait a moment before starting
        await new Promise(resolve => setTimeout(resolve, 2000));
        
        // Start the cluster
        await startMutation.mutateAsync({ id });
        
        toast.success('Cluster restarted!', { id: `restart-${id}` });
        queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
      } catch (error: any) {
        toast.error(`Failed to restart: ${error.message}`, { id: `restart-${id}` });
        throw error;
      }
    },
  };
}

// ============================================
// Auth Hooks
// ============================================

/**
 * Login mutation
 */
export function useLoginMutation() {
  const mutation = useLoginGenerated();

  return {
    ...mutation,
    mutate: (data: { username: string; password: string }, options?: { onSuccess?: (data: any) => void }) => {
      mutation.mutate(
        { data },
        {
          onSuccess: (response) => {
            if (response.data?.token) {
              localStorage.setItem('auth_token', response.data.token);
              toast.success('Logged in successfully!');
            }
            options?.onSuccess?.(response);
          },
          onError: (error) => {
            toast.error(`Login failed: ${error.message}`);
          },
        }
      );
    },
  };
}

/**
 * Register mutation
 */
export function useRegisterMutation() {
  const mutation = useRegisterGenerated();

  return {
    ...mutation,
    mutate: (data: { username: string; password: string; email?: string }, options?: { onSuccess?: (data: any) => void }) => {
      mutation.mutate(
        { data },
        {
          onSuccess: (response) => {
            if (response.data?.token) {
              localStorage.setItem('auth_token', response.data.token);
              toast.success('Registered successfully!');
            }
            options?.onSuccess?.(response);
          },
          onError: (error) => {
            toast.error(`Registration failed: ${error.message}`);
          },
        }
      );
    },
  };
}

/**
 * Get current authenticated user
 */
export function useCurrentUser() {
  const query = useGetCurrentUser({
    query: {
      retry: false,
      enabled: typeof window !== 'undefined' && !!localStorage.getItem('auth_token'),
    },
  });

  return {
    ...query,
    data: query.data?.data,
    isAuthenticated: !!query.data?.data,
  };
}

/**
 * Logout helper
 */
export function logout() {
  localStorage.removeItem('auth_token');
  window.location.href = '/login';
}

// ============================================
// Task Hooks
// ============================================

/**
 * Fetch cluster tasks
 */
export function useClusterTasks(clusterId: string) {
  const query = useListClusterTasks(clusterId, {
    query: {
      enabled: !!clusterId,
      refetchInterval: 10000,
    },
  });

  return {
    ...query,
    data: query.data?.data || [],
  };
}

/**
 * Fetch all tasks
 */
export function useTasks() {
  const query = useListAllTasks();

  return {
    ...query,
    data: query.data?.data || [],
  };
}

// ============================================
// Backup Hooks
// ============================================

/**
 * Fetch cluster backups
 */
export function useClusterBackups(clusterId: string) {
  const query = useListBackups(clusterId, {
    query: {
      enabled: !!clusterId,
    },
  });

  return {
    ...query,
    data: query.data?.data || [],
  };
}

/**
 * Create backup mutation
 */
export function useCreateBackupMutation(clusterId: string) {
  const queryClient = useQueryClient();
  const mutation = useCreateBackupGenerated();

  return {
    ...mutation,
    mutate: (name?: string) => {
      toast.loading('Creating backup...', { id: 'create-backup' });
      
      mutation.mutate(
        { clusterId, data: { name } },
        {
          onSuccess: () => {
            toast.success('Backup created!', { id: 'create-backup' });
            queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}/backups`] });
          },
          onError: (error) => {
            toast.error(`Backup failed: ${error.message}`, { id: 'create-backup' });
          },
        }
      );
    },
  };
}

// ============================================
// Dashboard Hooks
// ============================================

/**
 * Fetch dashboard summary (all clusters overview)
 */
export function useDashboardSummary() {
  const query = useGetDashboardSummary({
    query: {
      refetchInterval: 30000, // Refresh every 30 seconds
      refetchOnWindowFocus: true,
    },
  });

  return {
    ...query,
    data: query.data?.data,
  };
}

// ============================================
// Node Stats Hooks
// ============================================

/**
 * Fetch stats for a single node
 */
export function useNodeStats(nodeId: string) {
  const query = useGetNodeStats(nodeId, {
    query: {
      enabled: !!nodeId,
      refetchInterval: 5000, // Refresh every 5 seconds
    },
  });

  return {
    ...query,
    data: query.data?.data,
  };
}

/**
 * Fetch stats for all nodes in a cluster
 */
export function useClusterNodeStats(clusterId: string) {
  const query = useGetAllNodesStats(clusterId, {
    query: {
      enabled: !!clusterId,
      refetchInterval: 5000, // Refresh every 5 seconds
    },
  });

  return {
    ...query,
    data: query.data?.data || [],
  };
}
