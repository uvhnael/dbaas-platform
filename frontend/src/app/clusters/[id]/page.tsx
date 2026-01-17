'use client';

import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { useCluster, useClusterNodes, useClusterMetrics, useClusterNodeStats, useDeleteCluster, useRestartCluster } from '@/lib/api';
import { ClusterDetailSkeleton } from '@/components/Skeletons';
import { Button } from '@/components/ui/button';
import { Node as ClusterNode } from '@/lib/api/model';
import { toast } from 'sonner';
import { motion, AnimatePresence } from 'framer-motion';

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
      type: 'spring',
      stiffness: 300,
      damping: 24,
    },
  },
};

export default function ClusterDetailPage() {
  const params = useParams();
  const router = useRouter();
  const clusterId = params.id as string;
  
  // Centralized API calls
  const { data: cluster, isLoading: clusterLoading } = useCluster(clusterId);
  const { data: nodes, isLoading: nodesLoading } = useClusterNodes(clusterId);
  const { data: metrics, isLoading: metricsLoading } = useClusterMetrics(clusterId);
  const { data: nodeStats, isLoading: nodeStatsLoading } = useClusterNodeStats(clusterId);
  const deleteCluster = useDeleteCluster();
  const restartCluster = useRestartCluster();

  const [selectedNode, setSelectedNode] = useState<ClusterNode | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const handleNodeClick = (node: ClusterNode) => {
    setSelectedNode(node);
    setDrawerOpen(true);
  };

  const handleDelete = async () => {
    try {
      await deleteCluster.mutateAsync({ id: clusterId });
      toast.success('Cluster deleted successfully');
      router.push('/clusters');
    } catch (error) {
      toast.error('Failed to delete cluster');
    } finally {
      setDeleteDialogOpen(false);
    }
  };

  const handleRestart = async () => {
    try {
      await restartCluster.mutateAsync(clusterId);
      toast.success('Cluster restart initiated');
    } catch (error) {
      toast.error('Failed to restart cluster');
    }
  };

  // Show skeleton until all APIs are loaded
  const isLoading = clusterLoading || nodesLoading || metricsLoading || nodeStatsLoading;

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
        >
          <motion.div variants={itemVariants}>
            <ClusterHeader
              cluster={cluster}
              onRestart={handleRestart}
              onDelete={() => setDeleteDialogOpen(true)}
              isRestarting={restartCluster.isPending}
            />
          </motion.div>

          <motion.div variants={itemVariants} className="mt-6">
            {metrics && <ClusterMetricsGrid metrics={metrics} />}
          </motion.div>

          {/* Main Content */}
          <motion.div variants={itemVariants} className="grid grid-cols-3 gap-6 mt-6">
            {/* Topology */}
            <div className="col-span-2 glass-card rounded-xl p-5 flex flex-col h-full min-h-[500px]">
              <h3 className="text-sm font-medium text-white mb-4">Cluster Topology</h3>
              {nodes && (
                <TopologyDiagram 
                  nodes={nodes} 
                  clusterId={clusterId}
                  nodeStats={nodeStats}
                  onNodeClick={handleNodeClick}
                  className="flex-1 h-full min-h-0"
                />
              )}
            </div>

            {/* Sidebar */}
            <div className="space-y-4">
              <ConnectionDetails clusterId={clusterId} />
              <QuickActions />
            </div>
          </motion.div>

          <motion.div variants={itemVariants} className="mt-6">
            <RealtimeChart />
          </motion.div>

          <motion.div variants={itemVariants} className="mt-6">
            <NodesTable nodes={nodes || []} nodeStats={nodeStats} onNodeClick={handleNodeClick} />
          </motion.div>

          <NodeDetailDrawer node={selectedNode} open={drawerOpen} onClose={() => setDrawerOpen(false)} />

          <DeleteClusterDialog
            open={deleteDialogOpen}
            onOpenChange={setDeleteDialogOpen}
            clusterName={cluster.name || ''}
            onConfirm={handleDelete}
            isDeleting={deleteCluster.isPending}
          />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
