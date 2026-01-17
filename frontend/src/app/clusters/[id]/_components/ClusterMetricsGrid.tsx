'use client';

import { cn } from '@/lib/utils';
import { ClusterMetricsResponse, NodeStatsResponse } from '@/lib/api/model';
import { 
  Cpu, 
  HardDrive, 
  Activity, 
  Server, 
  ArrowDownToLine, 
  ArrowUpFromLine,
  Database,
  Clock
} from 'lucide-react';

interface ClusterMetricsGridProps {
  metrics: ClusterMetricsResponse;
}

// Helper to format bytes
function formatBytes(bytes: number) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function MetricCard({ 
  label, 
  value, 
  icon,
  status,
  subtitle 
}: { 
  label: string; 
  value: string; 
  icon?: React.ReactNode;
  status?: 'warning' | 'danger'; 
  subtitle?: string;
}) {
  return (
    <div className="glass-card rounded-xl p-4">
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs text-zinc-500">{label}</p>
        {icon && <span className="text-zinc-500">{icon}</span>}
      </div>
      <p className={cn(
        'text-xl font-semibold',
        status === 'warning' && 'text-amber-400',
        status === 'danger' && 'text-red-400',
        !status && 'text-white'
      )}>{value}</p>
      {subtitle && <p className="text-xs text-zinc-500 mt-1">{subtitle}</p>}
    </div>
  );
}

export function ClusterMetricsGrid({ metrics }: ClusterMetricsGridProps) {
  const avgCpu = metrics.avgCpuPercent ?? 0;
  const avgMemory = metrics.avgMemoryPercent ?? 0;
  const runningNodes = metrics.runningNodes ?? 0;
  const totalNodes = metrics.totalNodes ?? 0;
  const networkRx = metrics.totalNetworkRx ?? 0;
  const networkTx = metrics.totalNetworkTx ?? 0;
  const qps = metrics.queriesPerSecond ?? 0;
  const connections = metrics.activeConnections ?? 0;
  const replicationLag = metrics.replicationLagSeconds ?? 0;

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
      <MetricCard 
        label="Nodes" 
        value={`${runningNodes}/${totalNodes}`}
        icon={<Server className="w-4 h-4" />}
        subtitle="Running / Total"
      />
      <MetricCard 
        label="Avg. CPU" 
        value={`${avgCpu.toFixed(1)}%`}
        icon={<Cpu className="w-4 h-4" />}
        status={avgCpu > 80 ? 'danger' : avgCpu > 60 ? 'warning' : undefined}
      />
      <MetricCard 
        label="Avg. Memory" 
        value={`${avgMemory.toFixed(1)}%`}
        icon={<HardDrive className="w-4 h-4" />}
        status={avgMemory > 80 ? 'danger' : avgMemory > 60 ? 'warning' : undefined}
      />
      <MetricCard 
        label="Queries/sec" 
        value={qps.toLocaleString()}
        icon={<Activity className="w-4 h-4" />}
      />
      <MetricCard 
        label="Connections" 
        value={connections.toLocaleString()}
        icon={<Database className="w-4 h-4" />}
      />
      <MetricCard 
        label="Repl. Lag" 
        value={`${replicationLag}s`}
        icon={<Clock className="w-4 h-4" />}
        status={replicationLag > 10 ? 'warning' : undefined}
      />
    </div>
  );
}
