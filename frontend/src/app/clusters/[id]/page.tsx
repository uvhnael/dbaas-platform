'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { useCluster, useClusterNodes, useClusterMetrics, useDeleteCluster, useStartClusterMutation, useStopClusterMutation, useScaleCluster, useCreateBackup } from '@/lib/api';
import { ClusterDetailSkeleton } from '@/components/Skeletons';
import { Button } from '@/components/ui/button';
import { Node as ClusterNode } from '@/lib/api/model';
import { toast } from 'sonner';
import { ClusterStatus } from '@/lib/api/model/clusterStatus';
import { motion, AnimatePresence } from 'framer-motion';
import { useQueryClient } from '@tanstack/react-query';

import {
  ClusterHeader,
  ClusterMetricsGrid,
  ConnectionDetails,
  QuickActions,
  RealtimeChart,
  NodesTable,
  DeleteClusterDialog,
  TopologyDiagram,
  NodeDetailDrawer,
  ScaleClusterDialog,
  CreateBackupDialog,
  ViewLogsDialog,
} from './_components';

// Animation variants
const pageVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1,
      delayChildren: 0.05,
    },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      type: 'spring' as const,
      stiffness: 300,
      damping: 24,
    },
  },
};

export default function ClusterDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const clusterId = params.id as string;

  // Centralized API calls - metrics already includes nodeMetrics (node stats)
  const { data: cluster, isLoading: clusterLoading } = useCluster(clusterId);
  
  // Check if cluster is stopped - don't fetch metrics for stopped clusters (nodes still needed to show offline status)
  const isStopped = cluster?.status === ClusterStatus.STOPPED;
  
  const { data: nodes, isLoading: nodesLoading } = useClusterNodes(clusterId);
  const { data: metrics, isLoading: metricsLoading } = useClusterMetrics(clusterId, { enabled: !isStopped });
  // Note: useClusterNodeStats removed - metrics.nodeMetrics already contains the same data
  const deleteCluster = useDeleteCluster();
  const startCluster = useStartClusterMutation();
  const stopCluster = useStopClusterMutation();
  const scaleCluster = useScaleCluster();
  const createBackup = useCreateBackup();

  const [selectedNode, setSelectedNode] = useState<ClusterNode | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [scaleDialogOpen, setScaleDialogOpen] = useState(false);
  const [backupDialogOpen, setBackupDialogOpen] = useState(false);
  const [logsDialogOpen, setLogsDialogOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleNodeClick = (node: ClusterNode) => {
    setSelectedNode(node);
    setDrawerOpen(true);
  };

  const handleDelete = async () => {
    try {
      // Stop all data fetching immediately
      setIsDeleting(true);
      setDeleteDialogOpen(false);

      // Remove all queries for this cluster to stop refetching completely
      queryClient.removeQueries({ queryKey: [`/api/v1/clusters/${clusterId}`] });
      queryClient.removeQueries({ queryKey: [`/api/v1/clusters/${clusterId}/nodes`] });
      queryClient.removeQueries({ queryKey: [`/api/v1/clusters/${clusterId}/metrics`] });


      // Navigate immediately - don't wait for delete API to complete
      // This prevents the page from fetching data for a cluster being deleted
      toast.info('Deleting cluster...');
      queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
      router.push('/clusters');

      // Fire and forget - delete happens in background
      deleteCluster.mutateAsync({ id: clusterId })
        .then(() => {
          toast.success('Cluster deleted successfully');
          queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
        })
        .catch(() => {
          toast.error('Failed to delete cluster');
          queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });
        });
    } catch (error) {
      toast.error('Failed to delete cluster');
      setIsDeleting(false);
    }
  };

  const handleStart = () => {
    startCluster.mutate(clusterId, {
      onSuccess: () => {
        // After starting, invalidate all queries to refetch with new status
        // This enables metrics/nodes fetching since cluster is now running
        setTimeout(() => {
          queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}`] });
          queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}/nodes`] });
          queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}/metrics`] });
        }, 500); // Small delay to allow backend state to update
      },
    });
  };

  const handleStop = () => {
    stopCluster.mutate(clusterId, {
      onSuccess: () => {
        // After stopping, invalidate cluster and nodes queries to show offline status
        // Metrics queries will be disabled due to isStopped check
        setTimeout(() => {
          queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}`] });
          queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}/nodes`] });
          // Remove stale metrics data since they won't be refetched while stopped
          queryClient.removeQueries({ queryKey: [`/api/v1/clusters/${clusterId}/metrics`] });
        }, 500); // Small delay to allow backend state to update
      },
    });
  };

  const handleScale = async (replicaCount: number, resources?: { cpuCores: number; memory: string; storage?: string }) => {
    try {
      const scaleData: any = { replicaCount };
      if (resources) {
        scaleData.resources = {
          cpuCores: resources.cpuCores,
          memory: resources.memory,
        };
      }
      await scaleCluster.mutateAsync({ id: clusterId, data: scaleData });
      toast.success(`Cluster scaled to ${replicaCount} replicas`);

      // Immediately refetch to show new nodes in provisioning state
      queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}`] });
      queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}/nodes`] });

      queryClient.invalidateQueries({ queryKey: ['/api/v1/clusters'] });

      setScaleDialogOpen(false);
    } catch (error) {
      toast.error('Failed to scale cluster');
    }
  };

  const handleCreateBackup = async (backupName?: string) => {
    try {
      await createBackup.mutateAsync({ clusterId, data: { name: backupName } });
      toast.success('Backup created successfully');
      // Refresh backups list
      queryClient.invalidateQueries({ queryKey: [`/api/v1/clusters/${clusterId}/backups`] });
      setBackupDialogOpen(false);
    } catch (error) {
      toast.error('Failed to create backup');
    }
  };

  const handleOpenConsole = () => {
    // Open a new window with MySQL CLI connection info
    toast.info('Console feature coming soon');
  };

  // Show skeleton until required APIs are loaded (nodes always needed, metrics only for running clusters)
  const isLoading = clusterLoading || nodesLoading || (!isStopped && metricsLoading);

  return (
    <AnimatePresence mode="wait">
      {isLoading ? (
        <motion.div
          key="skeleton"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <ClusterDetailSkeleton />
        </motion.div>
      ) : !cluster ? (
        <motion.div
          key="not-found"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="flex items-center justify-center min-h-[60vh]"
        >
          <div className="text-center">
            <h2 className="text-xl font-semibold text-white mb-2">Cluster Not Found</h2>
            <p className="text-sm text-zinc-500 mb-4">The requested cluster does not exist.</p>
            <Link href="/clusters">
              <Button>Back to Clusters</Button>
            </Link>
          </div>
        </motion.div>
      ) : (
        <motion.div
          key="content"
          initial="hidden"
          animate="visible"
          variants={pageVariants}
          className={isStopped ? 'opacity-60' : ''}
        >
          <motion.div variants={itemVariants}>
            <ClusterHeader
              cluster={cluster}
              onStart={handleStart}
              onStop={handleStop}
              onDelete={() => setDeleteDialogOpen(true)}
              isStarting={startCluster.isPending}
              isStopping={stopCluster.isPending}
            />
          </motion.div>

          {/* Only show metrics grid if not stopped */}
          {!isStopped && (
            <motion.div variants={itemVariants} className="mt-6">
              {metrics && <ClusterMetricsGrid metrics={metrics} />}
            </motion.div>
          )}

          {/* Main Content */}
          <motion.div variants={itemVariants} className="grid grid-cols-3 gap-6 mt-6">
            {/* Topology */}
            <div className="col-span-2 glass-card rounded-xl p-5 flex flex-col h-full min-h-[500px]">
              <h3 className="text-sm font-medium text-white mb-4">Cluster Topology</h3>
              {nodes && (
                <TopologyDiagram
                  nodes={nodes}
                  clusterId={clusterId}
                  nodeStats={metrics?.nodeMetrics}
                  onNodeClick={handleNodeClick}
                  className="flex-1 h-full min-h-0"
                />
              )}
            </div>

            {/* Sidebar */}
            <div className="space-y-4">
              <ConnectionDetails clusterId={clusterId} />
              <QuickActions
                onScale={() => setScaleDialogOpen(true)}
                onBackup={() => setBackupDialogOpen(true)}
                onViewLogs={() => setLogsDialogOpen(true)}
                onOpenConsole={handleOpenConsole}
              />
            </div>
          </motion.div>

          {/* Only show realtime chart if not stopped */}
          {!isStopped && (
            <motion.div variants={itemVariants} className="mt-6">
              <RealtimeChart />
            </motion.div>
          )}

          <motion.div variants={itemVariants} className="mt-6">
            <NodesTable nodes={nodes || []} nodeStats={metrics?.nodeMetrics} onNodeClick={handleNodeClick} />
          </motion.div>

          <NodeDetailDrawer node={selectedNode} open={drawerOpen} onClose={() => setDrawerOpen(false)} />

          <DeleteClusterDialog
            open={deleteDialogOpen}
            onOpenChange={setDeleteDialogOpen}
            clusterName={cluster.name || ''}
            onConfirm={handleDelete}
            isDeleting={deleteCluster.isPending}
          />

          <ScaleClusterDialog
            open={scaleDialogOpen}
            onOpenChange={setScaleDialogOpen}
            currentReplicaCount={cluster.replicaCount || 2}
            clusterName={cluster.name || ''}
            onConfirm={handleScale}
            isScaling={scaleCluster.isPending}
          />

          <CreateBackupDialog
            open={backupDialogOpen}
            onOpenChange={setBackupDialogOpen}
            clusterName={cluster.name || ''}
            onConfirm={handleCreateBackup}
            isCreating={createBackup.isPending}
          />

          <ViewLogsDialog
            open={logsDialogOpen}
            onOpenChange={setLogsDialogOpen}
            clusterId={clusterId}
            clusterName={cluster.name || ''}
            nodes={nodes || []}
          />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
