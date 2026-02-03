'use client';

import { useEffect, useRef } from 'react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Node as ClusterNode, NodeRole, NodeStatus } from '@/lib/api/model';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Database,
  Server,
  Layers,
  Clock,
  Cpu,
  HardDrive,
  RotateCcw,
  Trash2,
  ArrowUpCircle,
  Terminal,
  Network,
  Activity,
  CircleDot,
  Wifi,
  WifiOff,
  Copy,
  ExternalLink,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { useGetNodeLogs, useGetNodeStats } from '@/lib/api';

interface NodeDetailDrawerProps {
  node: ClusterNode | null;
  open: boolean;
  onClose: () => void;
}

export function NodeDetailDrawer({ node, open, onClose }: NodeDetailDrawerProps) {
  const logContainerRef = useRef<HTMLDivElement>(null);

  // Fetch real logs from API with polling for live updates
  // Only poll when drawer is open to save resources
  const { data: logsResponse } = useGetNodeLogs(
    node?.id || '',
    { lines: 100, timestamps: true },
    {
      query: {
        enabled: !!node?.id && open,
        refetchInterval: open ? 5000 : false, // 5s when open, stop when closed
      },
    }
  );

  // Fetch real-time container stats
  // Only poll when drawer is open to save resources
  const { data: statsResponse, isLoading: statsLoading } = useGetNodeStats(
    node?.id || '',
    {
      query: {
        enabled: !!node?.id && open,
        refetchInterval: open ? 8000 : false, // 8s when open, stop when closed
      },
    }
  );

  const logs = logsResponse?.data?.logs || '';
  const logLines = logs ? logs.split('\n').filter(Boolean) : [];
  const stats = statsResponse?.data;
  const isRunning = stats?.running ?? node?.status === NodeStatus.RUNNING;

  // Auto-scroll to bottom
  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [logLines]);

  // Helper to format bytes
  const formatBytes = (bytes: number) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('Copied to clipboard');
  };

  if (!node) return null;

  const roleIcons = {
    [NodeRole.MASTER]: Database,
    [NodeRole.REPLICA]: Server,
    [NodeRole.PROXY]: Layers,
    [NodeRole.ORCHESTRATOR]: Layers,
  };

  const roleConfigs = {
    [NodeRole.MASTER]: {
      text: 'text-emerald-400',
      bg: 'bg-emerald-500/10',
      border: 'border-emerald-500/30',
      glow: 'shadow-emerald-500/20',
      label: 'Master'
    },
    [NodeRole.REPLICA]: {
      text: 'text-blue-400',
      bg: 'bg-blue-500/10',
      border: 'border-blue-500/30',
      glow: 'shadow-blue-500/20',
      label: 'Replica'
    },
    [NodeRole.PROXY]: {
      text: 'text-violet-400',
      bg: 'bg-violet-500/10',
      border: 'border-violet-500/30',
      glow: 'shadow-violet-500/20',
      label: 'ProxySQL'
    },
    [NodeRole.ORCHESTRATOR]: {
      text: 'text-orange-400',
      bg: 'bg-orange-500/10',
      border: 'border-orange-500/30',
      glow: 'shadow-orange-500/20',
      label: 'Orchestrator'
    },
  };

  const Icon = node.role ? roleIcons[node.role] : Database;
  const config = node.role ? roleConfigs[node.role] : roleConfigs[NodeRole.REPLICA];

  const handleRestart = () => {
    toast.loading(`Restarting ${node.containerName}...`, { id: `restart-${node.id}` });
    setTimeout(() => {
      toast.success(`${node.containerName} restarted successfully`, { id: `restart-${node.id}` });
    }, 2000);
  };

  const handlePromote = () => {
    if (node.role === NodeRole.REPLICA) {
      toast.loading(`Promoting ${node.containerName} to master...`, { id: `promote-${node.id}` });
      setTimeout(() => {
        toast.success(`${node.containerName} promoted to master!`, { id: `promote-${node.id}` });
      }, 3000);
    }
  };

  // Metric values
  const cpuPercent = stats?.cpuUsagePercent ?? 0;
  const memoryPercent = stats?.memoryUsagePercent ?? 0;
  const memoryUsed = stats?.memoryUsage ?? 0;
  const memoryLimit = stats?.memoryLimit ?? 0;
  const networkRx = stats?.networkRxBytes ?? 0;
  const networkTx = stats?.networkTxBytes ?? 0;
  const blockRead = stats?.blockRead ?? 0;
  const blockWrite = stats?.blockWrite ?? 0;
  const replicationLag = stats?.replicationLagSeconds;

  return (
    <Sheet open={open} onOpenChange={(v) => !v && onClose()}>
      <SheetContent className="w-[520px] sm:max-w-[520px] bg-zinc-950 border-l border-white/5 overflow-y-auto p-0">
        {/* Header with gradient background */}
        <div className={cn(
          'relative px-6 pt-6 pb-5 border-b border-white/5',
          'bg-gradient-to-br from-zinc-900/80 to-zinc-950'
        )}>
          {/* Status indicator */}
          <div className="absolute top-4 right-4 flex items-center gap-2">
            {isRunning ? (
              <Badge className="bg-emerald-500/10 text-emerald-400 border-emerald-500/30 gap-1.5 px-2.5 py-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                Running
              </Badge>
            ) : (
              <Badge className="bg-red-500/10 text-red-400 border-red-500/30 gap-1.5 px-2.5 py-1">
                <WifiOff className="w-3 h-3" />
                Offline
              </Badge>
            )}
          </div>

          {/* Icon + Name */}
          <div className="flex items-start gap-4">
            <div className={cn(
              'p-3.5 rounded-2xl border shadow-lg',
              config.bg, config.border, config.glow
            )}>
              <Icon className={cn('w-7 h-7', config.text)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <Badge variant="outline" className={cn(
                  'text-[10px] font-bold tracking-wider px-2 py-0.5',
                  config.bg, config.border, config.text
                )}>
                  {config.label}
                </Badge>
              </div>
              <SheetTitle className="text-xl font-bold text-white truncate">
                {node.containerName}
              </SheetTitle>
              <div className="flex items-center gap-2 mt-1.5">
                <code className="text-[11px] text-zinc-500 font-mono truncate max-w-[280px]">
                  {node.id}
                </code>
                <button
                  onClick={() => copyToClipboard(node.id || '')}
                  aria-label="Copy node ID"
                  className="text-zinc-500 hover:text-white transition-colors"
                >
                  <Copy className="w-3 h-3" aria-hidden="true" />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Main Content */}
        <div className="p-6 space-y-6">
          {/* Quick Actions */}
          <section>
            <h3 className="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider mb-3 flex items-center gap-2">
              <Activity className="w-3.5 h-3.5" />
              Quick Actions
            </h3>
            <div className="grid grid-cols-3 gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handleRestart}
                className="gap-2 bg-zinc-900/50 border-white/10 text-zinc-300 hover:text-white hover:bg-zinc-800 hover:border-white/20 h-10"
              >
                <RotateCcw className="w-4 h-4" />
                Restart
              </Button>
              {node.role === NodeRole.REPLICA && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handlePromote}
                  className="gap-2 bg-emerald-500/5 border-emerald-500/20 text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10 hover:border-emerald-500/30 h-10"
                >
                  <ArrowUpCircle className="w-4 h-4" />
                  Promote
                </Button>
              )}
              <Button
                variant="outline"
                size="sm"
                className="gap-2 bg-red-500/5 border-red-500/20 text-red-400 hover:text-red-300 hover:bg-red-500/10 hover:border-red-500/30 h-10"
              >
                <Trash2 className="w-4 h-4" />
                Remove
              </Button>
            </div>
          </section>

          {/* Resource Metrics */}
          <section>
            <h3 className="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider mb-3 flex items-center gap-2">
              <Cpu className="w-3.5 h-3.5" />
              Resource Usage
            </h3>

            {/* CPU & Memory with progress bars */}
            <div className="grid grid-cols-2 gap-3 mb-3">
              <MetricWithProgress
                icon={<Cpu className="w-4 h-4" />}
                label="CPU"
                value={`${cpuPercent.toFixed(1)}%`}
                progress={cpuPercent}
                color={cpuPercent > 80 ? 'red' : cpuPercent > 60 ? 'amber' : 'emerald'}
              />
              <MetricWithProgress
                icon={<HardDrive className="w-4 h-4" />}
                label="Memory"
                value={`${memoryPercent.toFixed(1)}%`}
                subtitle={`${formatBytes(memoryUsed)} / ${formatBytes(memoryLimit)}`}
                progress={memoryPercent}
                color={memoryPercent > 80 ? 'red' : memoryPercent > 60 ? 'amber' : 'blue'}
              />
            </div>

            {/* Network & Block I/O */}
            <div className="grid grid-cols-2 gap-3">
              <MetricCard
                icon={<Network className="w-4 h-4 text-violet-400" />}
                label="Network I/O"
                value={
                  <span className="flex items-center gap-2 text-sm">
                    <span className="text-emerald-400">↓{formatBytes(networkRx)}</span>
                    <span className="text-zinc-600">/</span>
                    <span className="text-blue-400">↑{formatBytes(networkTx)}</span>
                  </span>
                }
              />
              <MetricCard
                icon={<HardDrive className="w-4 h-4 text-orange-400" />}
                label="Block I/O"
                value={
                  <span className="flex items-center gap-2 text-sm">
                    <span className="text-emerald-400">R:{formatBytes(blockRead)}</span>
                    <span className="text-zinc-600">/</span>
                    <span className="text-blue-400">W:{formatBytes(blockWrite)}</span>
                  </span>
                }
              />
            </div>

            {/* Replication Lag - Only for replicas */}
            {node.role === NodeRole.REPLICA && (
              <div className="mt-3">
                <MetricWithProgress
                  icon={<Clock className="w-4 h-4" />}
                  label="Replication Lag"
                  value={replicationLag !== undefined ? `${replicationLag}s` : '-'}
                  subtitle={replicationLag !== undefined
                    ? replicationLag === 0 ? 'In sync with master' : 'Behind master'
                    : 'Checking...'
                  }
                  progress={replicationLag !== undefined ? Math.min(replicationLag * 5, 100) : 0}
                  color={
                    replicationLag === undefined ? 'blue' :
                      replicationLag > 10 ? 'red' :
                        replicationLag > 5 ? 'amber' : 'emerald'
                  }
                />
              </div>
            )}

            {/* Node Configuration */}
            <div className="mt-3 pt-3 border-t border-white/5">
              <h4 className="text-[10px] font-medium text-zinc-500 uppercase tracking-wider mb-2">Configuration</h4>
              <div className="grid grid-cols-2 gap-2">
                <div className="px-3 py-2 rounded-lg bg-zinc-800/50 border border-white/5">
                  <span className="text-[10px] text-zinc-500 uppercase tracking-wider">CPU Cores</span>
                  <p className="text-sm font-medium text-white">{node.cpuCores || 2} vCPU</p>
                </div>
                <div className="px-3 py-2 rounded-lg bg-zinc-800/50 border border-white/5">
                  <span className="text-[10px] text-zinc-500 uppercase tracking-wider">Memory</span>
                  <p className="text-sm font-medium text-white">{node.memory || '4G'}</p>
                </div>
              </div>
            </div>
          </section>

          {/* Live Console */}
          <section>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider flex items-center gap-2">
                <Terminal className="w-3.5 h-3.5" />
                Live Console
              </h3>
              {logLines.length > 0 && (
                <span className="flex items-center gap-1.5 text-xs text-emerald-400">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                  Streaming
                </span>
              )}
            </div>

            <div
              ref={logContainerRef}
              className={cn(
                "h-[280px] rounded-xl p-4 font-mono text-xs overflow-y-auto",
                "bg-black/80 border border-zinc-800/80",
                "shadow-inner shadow-black/50"
              )}
            >
              {logLines.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-zinc-600">
                  <Terminal className="w-8 h-8 mb-2 opacity-50" />
                  <p className="italic">Waiting for logs...</p>
                </div>
              ) : (
                logLines.map((log, index) => (
                  <div
                    key={index}
                    className={cn(
                      'py-0.5 leading-relaxed tracking-tight',
                      log.includes('ERROR') && 'text-red-400',
                      log.includes('WARN') && 'text-amber-400',
                      log.includes('Note') && 'text-emerald-400',
                      log.includes('ready for connections') && 'text-emerald-400',
                      !log.includes('ERROR') && !log.includes('WARN') && !log.includes('Note') && !log.includes('ready') && 'text-zinc-400'
                    )}
                  >
                    {log}
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      </SheetContent>
    </Sheet>
  );
}

// Metric card with progress bar
function MetricWithProgress({
  icon,
  label,
  value,
  subtitle,
  progress,
  color = 'emerald'
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  subtitle?: string;
  progress: number;
  color?: 'emerald' | 'blue' | 'amber' | 'red';
}) {
  const colorClasses = {
    emerald: 'bg-emerald-500',
    blue: 'bg-blue-500',
    amber: 'bg-amber-500',
    red: 'bg-red-500',
  };

  return (
    <div className="p-3.5 rounded-xl bg-zinc-900/60 border border-white/5 hover:border-white/10 transition-colors">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2 text-zinc-500">
          {icon}
          <span className="text-[10px] uppercase tracking-wider font-medium">{label}</span>
        </div>
        <span className="text-lg font-bold text-white font-mono">{value}</span>
      </div>
      {subtitle && (
        <p className="text-[10px] text-zinc-500 mb-2">{subtitle}</p>
      )}
      <div className="h-1.5 bg-zinc-800 rounded-full overflow-hidden">
        <div
          className={cn('h-full rounded-full transition-all duration-500', colorClasses[color])}
          style={{ width: `${Math.min(100, progress)}%` }}
        />
      </div>
    </div>
  );
}

// Simple metric card
function MetricCard({
  icon,
  label,
  value
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
}) {
  return (
    <div className="p-3.5 rounded-xl bg-zinc-900/60 border border-white/5 hover:border-white/10 transition-colors">
      <div className="flex items-center gap-2 text-zinc-500 mb-2">
        {icon}
        <span className="text-[10px] uppercase tracking-wider font-medium">{label}</span>
      </div>
      <div className="font-medium text-white">{value}</div>
    </div>
  );
}

export default NodeDetailDrawer;
