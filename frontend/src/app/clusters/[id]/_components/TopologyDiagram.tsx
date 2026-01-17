'use client';

import { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Node,
  Edge,
  Background,
  Controls,
  Handle,
  Position,
  NodeProps,
  useNodesState,
  useEdgesState,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Node as ClusterNode, NodeRole, NodeStatus, NodeStatsResponse } from '@/lib/api/model';
import { Database, Server, Layers, RotateCcw, FileText, ArrowUpCircle, Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from '@/components/ui/context-menu';
import { toast } from 'sonner';

interface TopologyDiagramProps {
  nodes: ClusterNode[];
  clusterId: string;
  nodeStats?: NodeStatsResponse[];
  onNodeClick?: (node: ClusterNode) => void;
}

// Extended node with stats
interface NodeWithStats extends ClusterNode {
  cpuPercent?: number;
  memoryPercent?: number;
  replicationLag?: number;
}

// Role configuration - defined outside component to prevent recreation
const ROLE_CONFIG = {
  [NodeRole.MASTER]: { 
    icon: Database, 
    bg: 'bg-emerald-500/15', 
    border: 'border-emerald-500/40', 
    text: 'text-emerald-400',
    label: 'MASTER'
  },
  [NodeRole.REPLICA]: { 
    icon: Server, 
    bg: 'bg-blue-500/15', 
    border: 'border-blue-500/40', 
    text: 'text-blue-400',
    label: 'REPLICA'
  },
  [NodeRole.PROXY]: { 
    icon: Layers, 
    bg: 'bg-violet-500/15', 
    border: 'border-violet-500/40', 
    text: 'text-violet-400',
    label: 'PROXY'
  },
  [NodeRole.ORCHESTRATOR]: { 
    icon: Layers, 
    bg: 'bg-orange-500/15', 
    border: 'border-orange-500/40', 
    text: 'text-orange-400',
    label: 'ORCHESTRATOR'
  },
} as const;

// Custom Node Component - Draggable with left click
function ClusterNodeComponent({ data }: NodeProps) {
  const node = data.node as NodeWithStats;
  const onNodeClick = data.onNodeClick as ((node: ClusterNode) => void) | undefined;
  
  const config = node.role ? ROLE_CONFIG[node.role] : ROLE_CONFIG[NodeRole.REPLICA];
  const Icon = config.icon;

  const handleRestart = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    toast.loading(`Restarting ${node.containerName}...`, { id: `restart-${node.id}` });
    setTimeout(() => {
      toast.success(`${node.containerName} restarted`, { id: `restart-${node.id}` });
    }, 2000);
  }, [node.containerName, node.id]);

  const handleViewLogs = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    onNodeClick?.(node);
  }, [node, onNodeClick]);

  const handlePromote = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    if (node.role === NodeRole.REPLICA) {
      toast.loading(`Promoting ${node.containerName}...`, { id: `promote-${node.id}` });
      setTimeout(() => {
        toast.success(`${node.containerName} promoted to master`, { id: `promote-${node.id}` });
      }, 3000);
    }
  }, [node.containerName, node.id, node.role]);

  const handleDoubleClick = useCallback(() => {
    onNodeClick?.(node);
  }, [node, onNodeClick]);

  return (
    <ContextMenu>
      <ContextMenuTrigger>
        <div
          onDoubleClick={handleDoubleClick}
          className={cn(
            'relative px-4 py-3 rounded-xl min-w-[160px] cursor-grab active:cursor-grabbing',
            'bg-zinc-900 border-2 border-zinc-700/50',
            'hover:border-zinc-600 transition-all duration-150',
            'shadow-xl shadow-black/20'
          )}
        >
          {/* Connection Handles - Multiple positions for flexible routing */}
          {/* Top handle */}
          <Handle 
            type="target" 
            position={Position.Top}
            id="top"
            className="!w-2.5 !h-2.5 !bg-zinc-600 !border-2 !border-zinc-800 !-top-1" 
          />
          {/* Bottom handle */}
          <Handle 
            type="source" 
            position={Position.Bottom}
            id="bottom"
            className="!w-2.5 !h-2.5 !bg-zinc-600 !border-2 !border-zinc-800 !-bottom-1" 
          />
          {/* Left handle */}
          <Handle 
            type="target" 
            position={Position.Left}
            id="left"
            className="!w-2.5 !h-2.5 !bg-zinc-600 !border-2 !border-zinc-800 !-left-1" 
          />
          {/* Right handle */}
          <Handle 
            type="source" 
            position={Position.Right}
            id="right"
            className="!w-2.5 !h-2.5 !bg-zinc-600 !border-2 !border-zinc-800 !-right-1" 
          />

          {/* Status Indicator with Glow */}
          <div className={cn(
            'absolute -top-1.5 -right-1.5 w-3.5 h-3.5 rounded-full border-2 border-zinc-900',
            node.status === NodeStatus.RUNNING && 'bg-emerald-500 shadow-[0_0_8px_2px_rgba(34,197,94,0.4)]',
            node.status === NodeStatus.SYNCING && 'bg-amber-500 shadow-[0_0_8px_2px_rgba(245,158,11,0.4)] animate-pulse',
            (node.status === NodeStatus.STOPPED || node.status === NodeStatus.FAILED) && 'bg-red-500 shadow-[0_0_8px_2px_rgba(239,68,68,0.4)]'
          )} />

          {/* Role Badge */}
          <div className={cn(
            'inline-flex items-center gap-1.5 px-2 py-1 rounded-md mb-2',
            config.bg, config.border, 'border'
          )}>
            <Icon className={cn('w-3.5 h-3.5', config.text)} aria-hidden="true" />
            <span className={cn('text-[10px] font-bold tracking-wider', config.text)}>
              {config.label}
            </span>
          </div>

          {/* Hostname */}
          <p className="text-sm font-semibold text-white mb-3 truncate" title={node.containerName}>
            {node.containerName}
          </p>

          {/* Metrics Grid - Always show */}
          <div className="space-y-1.5 text-xs">
            <div className="flex justify-between items-center">
              <span className="text-zinc-500">CPU</span>
              <span className={cn(
                'font-medium',
                node.cpuPercent === undefined && 'text-zinc-600',
                node.cpuPercent !== undefined && node.cpuPercent > 80 ? 'text-red-400' : 'text-zinc-300'
              )}>
                {node.cpuPercent !== undefined ? `${node.cpuPercent.toFixed(1)}%` : '--'}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-zinc-500">Mem</span>
              <span className={cn(
                'font-medium',
                node.memoryPercent === undefined && 'text-zinc-600',
                node.memoryPercent !== undefined && node.memoryPercent > 80 ? 'text-red-400' : 'text-zinc-300'
              )}>
                {node.memoryPercent !== undefined ? `${node.memoryPercent.toFixed(1)}%` : '--'}
              </span>
            </div>
            {node.replicationLag !== undefined && node.role === NodeRole.REPLICA && (
              <div className="flex justify-between items-center">
                <span className="text-zinc-500">Lag</span>
                <span className={cn(
                  'font-medium',
                  node.replicationLag > 10 ? 'text-amber-400' : 'text-zinc-300'
                )}>
                  {node.replicationLag}s
                </span>
              </div>
            )}
          </div>
        </div>
      </ContextMenuTrigger>
      
      <ContextMenuContent className="w-48 bg-zinc-900 border-zinc-700">
        <ContextMenuItem onClick={handleViewLogs} className="gap-2 text-zinc-300 focus:text-white focus:bg-zinc-800">
          <FileText className="w-4 h-4" aria-hidden="true" />
          View Logs
        </ContextMenuItem>
        <ContextMenuItem onClick={handleRestart} className="gap-2 text-zinc-300 focus:text-white focus:bg-zinc-800">
          <RotateCcw className="w-4 h-4" aria-hidden="true" />
          Restart Node
        </ContextMenuItem>
        {node.role === NodeRole.REPLICA && (
          <ContextMenuItem onClick={handlePromote} className="gap-2 text-zinc-300 focus:text-white focus:bg-zinc-800">
            <ArrowUpCircle className="w-4 h-4" aria-hidden="true" />
            Promote to Master
          </ContextMenuItem>
        )}
        <ContextMenuSeparator className="bg-zinc-700" />
        <ContextMenuItem className="gap-2 text-red-400 focus:text-red-300 focus:bg-red-500/10">
          <Trash2 className="w-4 h-4" aria-hidden="true" />
          Remove Node
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  );
}

const nodeTypes = {
  clusterNode: ClusterNodeComponent,
};

export function TopologyDiagram({ nodes: clusterNodes, clusterId, nodeStats, onNodeClick, className }: TopologyDiagramProps & { className?: string }) {
  // Create a map of nodeId -> stats for quick lookup
  const statsMap = useMemo(() => {
    const map: Record<string, NodeStatsResponse> = {};
    if (nodeStats) {
      nodeStats.forEach(stat => {
        if (stat.nodeId) {
          map[stat.nodeId] = stat;
        }
      });
    }
    return map;
  }, [nodeStats]);

  // Merge nodes with their real-time stats
  const nodesWithStats: NodeWithStats[] = useMemo(() => {
    return clusterNodes.map(node => {
      const stats = statsMap[node.id || ''];
      return {
        ...node,
        cpuPercent: stats?.cpuUsagePercent,
        memoryPercent: stats?.memoryUsagePercent,
        replicationLag: stats?.replicationLagSeconds,
      };
    });
  }, [clusterNodes, statsMap]);

  const proxysql = nodesWithStats.find(n => n.role === NodeRole.PROXY);
  const master = nodesWithStats.find(n => n.role === NodeRole.MASTER);
  const replicas = nodesWithStats.filter(n => n.role === NodeRole.REPLICA);

  // Memoize node IDs for stable dependencies
  const proxyId = proxysql?.id;
  const masterId = master?.id;
  const replicaIds = useMemo(() => replicas.map(r => r.id).join(','), [replicas]);

  // Build initial nodes
  const initialNodes: Node[] = useMemo(() => {
    const result: Node[] = [];
    const centerX = 300;

    if (proxysql) {
      result.push({
        id: proxysql.id,
        type: 'clusterNode',
        position: { x: centerX - 80, y: 20 },
        data: { node: proxysql, onNodeClick },
        draggable: true,
      });
    }

    if (master) {
      result.push({
        id: master.id,
        type: 'clusterNode',
        position: { x: centerX - 80, y: 180 },
        data: { node: master, onNodeClick },
        draggable: true,
      });
    }

    const replicaSpacing = 200;
    const totalWidth = (replicas.length - 1) * replicaSpacing;
    const startX = centerX - totalWidth / 2 - 80;

    replicas.forEach((replica, index) => {
      result.push({
        id: replica.id,
        type: 'clusterNode',
        position: { x: startX + index * replicaSpacing, y: 340 },
        data: { node: replica, onNodeClick },
        draggable: true,
      });
    });

    return result;
  }, [proxyId, masterId, replicaIds, proxysql, master, replicas, onNodeClick]);

  // Build edges
  const initialEdges: Edge[] = useMemo(() => {
    const result: Edge[] = [];

    // ProxySQL → Master (Write traffic - solid violet line)
    if (proxysql && master) {
      result.push({
        id: `${proxysql.id}-${master.id}`,
        source: proxysql.id,
        target: master.id,
        animated: true,
        label: 'write',
        labelStyle: { fill: '#8b5cf6', fontSize: 10, fontWeight: 500 },
        labelBgStyle: { fill: '#18181b', fillOpacity: 0.8 },
        labelBgPadding: [4, 2] as [number, number],
        style: { stroke: '#8b5cf6', strokeWidth: 2 },
        type: 'smoothstep',
      });
    }

    // ProxySQL → Replicas (Read traffic - dashed violet line)
    replicas.forEach((replica) => {
      if (proxysql) {
        const isOffline = replica.status === NodeStatus.STOPPED || replica.status === NodeStatus.FAILED;
        
        result.push({
          id: `${proxysql.id}-${replica.id}`,
          source: proxysql.id,
          target: replica.id,
          animated: true,
          label: 'read',
          labelStyle: { fill: '#8b5cf6', fontSize: 10, fontWeight: 500 },
          labelBgStyle: { fill: '#18181b', fillOpacity: 0.8 },
          labelBgPadding: [4, 2] as [number, number],
          style: { 
            stroke: isOffline ? '#ef4444' : '#8b5cf6', 
            strokeWidth: 2,
            strokeDasharray: '6,4',
          },
          type: 'smoothstep',
        });
      }
    });

    // Master → Replicas (Replication flow - blue line)
    replicas.forEach((replica) => {
      if (master) {
        const isOffline = replica.status === NodeStatus.STOPPED || replica.status === NodeStatus.FAILED;
        const isSyncing = replica.status === NodeStatus.SYNCING;
        
        result.push({
          id: `${master.id}-${replica.id}`,
          source: master.id,
          target: replica.id,
          animated: !isOffline && isSyncing,
          label: 'replication',
          labelStyle: { fill: '#3b82f6', fontSize: 10, fontWeight: 500 },
          labelBgStyle: { fill: '#18181b', fillOpacity: 0.8 },
          labelBgPadding: [4, 2] as [number, number],
          style: { 
            stroke: isOffline ? '#ef4444' : isSyncing ? '#f59e0b' : '#3b82f6', 
            strokeWidth: 2,
            strokeDasharray: isOffline ? '6,6' : undefined,
          },
          type: 'smoothstep',
        });
      }
    });

    return result;
  }, [proxyId, masterId, replicaIds, proxysql, master, replicas]);

  // Use React Flow state hooks for draggable nodes
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  return (
    <div className={cn(
      "w-full bg-zinc-950 rounded-xl border border-zinc-800 overflow-hidden relative",
      className || "h-[480px]"
    )}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1 }}
        proOptions={{ hideAttribution: true }}
        className="bg-zinc-950"
        minZoom={0.5}
        maxZoom={1.5}
        defaultEdgeOptions={{
          type: 'smoothstep',
        }}
      >
        <Background color="#27272a" gap={32} size={1} />
        <Controls 
          className="!bg-zinc-900 !border-zinc-700 !rounded-lg [&>button]:!bg-zinc-900 [&>button]:!border-zinc-700 [&>button]:!text-zinc-400 [&>button:hover]:!text-white [&>button:hover]:!bg-zinc-800" 
          showInteractive={false}
        />
      </ReactFlow>
      
      {/* Legend - moved to top left */}
      <div className="absolute top-4 left-4 flex items-center gap-5 bg-zinc-900/95 backdrop-blur-sm px-4 py-2.5 rounded-lg border border-zinc-700">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-[0_0_6px_rgba(34,197,94,0.5)]" />
          <span className="text-xs text-zinc-400">Online</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-amber-500 shadow-[0_0_6px_rgba(245,158,11,0.5)] animate-pulse" />
          <span className="text-xs text-zinc-400">Syncing</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-red-500 shadow-[0_0_6px_rgba(239,68,68,0.5)]" />
          <span className="text-xs text-zinc-400">Offline</span>
        </div>
      </div>

      {/* Tip */}
      <div className="absolute top-4 right-4 text-[11px] text-zinc-500 bg-zinc-900/90 px-3 py-1.5 rounded-md border border-zinc-700">
        Drag nodes • Right-click for actions • Double-click for details
      </div>
    </div>
  );
}

export default TopologyDiagram;
