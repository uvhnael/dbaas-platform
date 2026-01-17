'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { StatusBadge } from '@/components/StatusBadge';
import { Loader2, RefreshCw, Trash2, ChevronRight, AlertTriangle } from 'lucide-react';
import { Cluster } from '@/lib/api/model';

interface ClusterHeaderProps {
  cluster: Cluster;
  onRestart: () => void;
  onDelete: () => void;
  isRestarting: boolean;
}

export function ClusterHeader({ cluster, onRestart, onDelete, isRestarting }: ClusterHeaderProps) {
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
            <StatusBadge status={cluster.status} />
          </div>
          <p className="text-sm text-zinc-500 mt-1 font-mono">{cluster.id}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button 
            variant="outline" 
            size="sm" 
            onClick={onRestart}
            disabled={isRestarting}
            className="bg-transparent border-white/10 text-zinc-300 hover:text-white hover:bg-white/5"
          >
            {isRestarting ? (
              <Loader2 className="w-4 h-4 animate-spin mr-2" />
            ) : (
              <RefreshCw className="w-4 h-4 mr-2" />
            )}
            Restart
          </Button>
          <Button 
            variant="outline" 
            size="sm" 
            onClick={onDelete}
            className="bg-transparent border-red-500/20 text-red-400 hover:text-red-300 hover:bg-red-500/10"
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
