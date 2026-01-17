'use client';

import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreVertical, Clock } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Node as ClusterNode, NodeRole, NodeStatus, NodeStatsResponse } from '@/lib/api/model';
import { useMemo } from 'react';

interface NodesTableProps {
  nodes: ClusterNode[];
  nodeStats?: NodeStatsResponse[];
  onNodeClick: (node: ClusterNode) => void;
}

export function NodesTable({ nodes, nodeStats, onNodeClick }: NodesTableProps) {
  // Create a map of nodeId -> stats
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

  // Helper to format bytes
  const formatPercent = (value?: number) => {
    if (value === undefined || value === null) return '-';
    return `${value.toFixed(1)}%`;
  };

  return (
    <div className="glass-card rounded-xl p-5">
      <h3 className="text-sm font-medium text-white mb-4">Nodes</h3>
      <Table>
        <TableHeader>
          <TableRow className="border-white/5 hover:bg-transparent">
            <TableHead className="text-zinc-500 text-xs font-medium">Hostname</TableHead>
            <TableHead className="text-zinc-500 text-xs font-medium">Role</TableHead>
            <TableHead className="text-zinc-500 text-xs font-medium">Status</TableHead>
            <TableHead className="text-zinc-500 text-xs font-medium">Repl. Lag</TableHead>
            <TableHead className="text-zinc-500 text-xs font-medium">CPU</TableHead>
            <TableHead className="text-zinc-500 text-xs font-medium">Memory</TableHead>
            <TableHead className="w-10"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {nodes?.map((node) => {
            const stats = statsMap[node.id || ''];
            const cpuPercent = stats?.cpuUsagePercent;
            const memoryPercent = stats?.memoryUsagePercent;
            const replicationLag = stats?.replicationLagSeconds;
            const isReplica = node.role === NodeRole.REPLICA;

            return (
              <TableRow 
                key={node.id} 
                className="border-white/5 cursor-pointer hover:bg-white/[0.02]"
                onClick={() => onNodeClick(node)}
              >
                <TableCell className="font-medium text-white text-sm">{node.containerName}</TableCell>
                <TableCell>
                  <span className={cn(
                    'text-xs font-medium uppercase',
                    node.role === NodeRole.MASTER && 'text-emerald-400',
                    node.role === NodeRole.REPLICA && 'text-blue-400',
                    node.role === NodeRole.PROXY && 'text-violet-400'
                  )}>
                    {node.role}
                  </span>
                </TableCell>
                <TableCell>
                  <span className="flex items-center gap-2">
                    <span className={cn(
                      node.status === NodeStatus.RUNNING && 'status-dot-online',
                      node.status === NodeStatus.SYNCING && 'status-dot-syncing',
                      (node.status === NodeStatus.STOPPED || node.status === NodeStatus.FAILED) && 'status-dot-offline'
                    )} />
                    <span className="text-zinc-400 text-sm capitalize">{node.status?.toLowerCase()}</span>
                  </span>
                </TableCell>
                <TableCell>
                  {isReplica ? (
                    <span className={cn(
                      'text-sm font-medium flex items-center gap-1.5',
                      replicationLag === undefined && 'text-zinc-500',
                      replicationLag !== undefined && replicationLag === 0 && 'text-emerald-400',
                      replicationLag !== undefined && replicationLag > 0 && replicationLag <= 5 && 'text-emerald-400',
                      replicationLag !== undefined && replicationLag > 5 && replicationLag <= 10 && 'text-amber-400',
                      replicationLag !== undefined && replicationLag > 10 && 'text-red-400'
                    )}>
                      <Clock className="w-3 h-3" />
                      {replicationLag !== undefined ? `${replicationLag}s` : '-'}
                    </span>
                  ) : (
                    <span className="text-zinc-500 text-sm">-</span>
                  )}
                </TableCell>
                <TableCell>
                  <span className={cn(
                    'text-sm font-medium',
                    cpuPercent === undefined && 'text-zinc-500',
                    cpuPercent !== undefined && cpuPercent <= 60 && 'text-zinc-300',
                    cpuPercent !== undefined && cpuPercent > 60 && cpuPercent <= 80 && 'text-amber-400',
                    cpuPercent !== undefined && cpuPercent > 80 && 'text-red-400'
                  )}>
                    {formatPercent(cpuPercent)}
                  </span>
                </TableCell>
                <TableCell>
                  <span className={cn(
                    'text-sm font-medium',
                    memoryPercent === undefined && 'text-zinc-500',
                    memoryPercent !== undefined && memoryPercent <= 60 && 'text-zinc-300',
                    memoryPercent !== undefined && memoryPercent > 60 && memoryPercent <= 80 && 'text-amber-400',
                    memoryPercent !== undefined && memoryPercent > 80 && 'text-red-400'
                  )}>
                    {formatPercent(memoryPercent)}
                  </span>
                </TableCell>
                <TableCell>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="sm" onClick={(e) => e.stopPropagation()} aria-label="More options" className="text-zinc-500 hover:text-white">
                        <MoreVertical className="w-4 h-4" aria-hidden="true" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="bg-zinc-900 border-white/10">
                      <DropdownMenuItem onClick={() => onNodeClick(node)}>View Details</DropdownMenuItem>
                      <DropdownMenuItem>Restart</DropdownMenuItem>
                      <DropdownMenuItem className="text-red-400">Remove</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
