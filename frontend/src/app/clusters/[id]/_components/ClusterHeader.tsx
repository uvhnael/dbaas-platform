'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { StatusBadge } from '@/components/StatusBadge';
import { Loader2, Play, Square, Trash2, ChevronRight, AlertTriangle } from 'lucide-react';
import { Cluster } from '@/lib/api/model';

interface ClusterHeaderProps {
  cluster: Cluster;
  onStart: () => void;
  onStop: () => void;
  onDelete: () => void;
  isStarting: boolean;
  isStopping: boolean;
}

export function ClusterHeader({ cluster, onStart, onStop, onDelete, isStarting, isStopping }: ClusterHeaderProps) {
  // Determine button states based on cluster status
  const canStart = cluster.status === 'STOPPED';
  const canStop = cluster.status === 'RUNNING' || cluster.status === 'HEALTHY' || cluster.status === 'DEGRADED';
  const isTransitioning = cluster.status === 'PROVISIONING' || cluster.status === 'SCALING' || cluster.status === 'DELETING';

  return (
    <>
      {/* Breadcrumb */}
      <nav className="flex items-center gap-1.5 text-sm">
        <Link href="/clusters" className="text-zinc-500 hover:text-white transition-colors">
          Clusters
        </Link>
        <ChevronRight className="w-4 h-4 text-zinc-600" aria-hidden="true" />
        <span className="text-white">{cluster.name}</span>
      </nav>

      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-white tracking-tight">{cluster.name}</h1>
            <StatusBadge status={cluster.status ?? 'PROVISIONING'} />
          </div>
          <p className="text-sm text-zinc-500 mt-1 font-mono">{cluster.id}</p>
        </div>
        <div className="flex items-center gap-2">
          {/* Start Button - only enabled when STOPPED */}
          <Button 
            variant="outline" 
            size="sm" 
            onClick={onStart}
            disabled={!canStart || isStarting || isTransitioning}
            className="bg-transparent border-emerald-500/20 text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isStarting ? (
              <Loader2 className="w-4 h-4 animate-spin mr-2" />
            ) : (
              <Play className="w-4 h-4 mr-2" />
            )}
            Start
          </Button>
          
          {/* Stop Button - only enabled when RUNNING/HEALTHY/DEGRADED */}
          <Button 
            variant="outline" 
            size="sm" 
            onClick={onStop}
            disabled={!canStop || isStopping || isTransitioning}
            className="bg-transparent border-amber-500/20 text-amber-400 hover:text-amber-300 hover:bg-amber-500/10 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isStopping ? (
              <Loader2 className="w-4 h-4 animate-spin mr-2" />
            ) : (
              <Square className="w-4 h-4 mr-2" />
            )}
            Stop
          </Button>
          
          {/* Delete Button */}
          <Button 
            variant="outline" 
            size="sm" 
            onClick={onDelete}
            disabled={isTransitioning}
            className="bg-transparent border-red-500/20 text-red-400 hover:text-red-300 hover:bg-red-500/10 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Trash2 className="w-4 h-4 mr-2" />
            Delete
          </Button>
        </div>
      </div>

      {/* Degraded Alert */}
      {cluster.status === 'DEGRADED' && (
        <div className="flex items-center gap-3 p-4 bg-amber-500/5 border border-amber-500/20 rounded-xl">
          <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" aria-hidden="true" />
          <div className="flex-1">
            <p className="text-sm font-medium text-amber-400">Cluster is degraded</p>
            <p className="text-xs text-zinc-500 mt-0.5">One or more replicas experiencing issues</p>
          </div>
        </div>
      )}
    </>
  );
}

