import Link from 'next/link';
import { Cluster } from '@/types';
import { StatusBadge } from './StatusBadge';
import { ArrowRight, Database, Server } from 'lucide-react';

interface ClusterCardProps {
  cluster: Cluster;
}

export function ClusterCard({ cluster }: ClusterCardProps) {
  const totalNodes = 2 + cluster.replicaCount;
  
  return (
    <Link href={`/clusters/${cluster.id}`} className="block group">
      <div className="glass-card rounded-xl p-5 hover:border-white/20 transition-all duration-200 hover:translate-y-[-2px]">
        {/* Header */}
        <div className="flex items-start justify-between mb-4">
          <div className="space-y-1">
            <h3 className="text-base font-semibold text-white group-hover:text-emerald-400 transition-colors">
              {cluster.name}
            </h3>
            <p className="text-xs text-zinc-500 font-mono">
              {cluster.id}
            </p>
          </div>
          <StatusBadge status={cluster.status} size="sm" />
        </div>

        {/* Metrics Grid */}
        <div className="grid grid-cols-3 gap-3 mb-4">
          <MetricItem label="Nodes" value={totalNodes} />
          <MetricItem label="Replicas" value={cluster.replicaCount} />
          <MetricItem label="MySQL" value={cluster.mysqlVersion} />
        </div>

        {/* Topology Preview */}
        <div className="flex items-center justify-center gap-2 py-3 bg-zinc-900/50 rounded-lg mb-4">
          {/* Master */}
          <div className="flex flex-col items-center gap-1">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
              <Database className="w-3.5 h-3.5 text-emerald-400" />
            </div>
            <span className="text-[10px] text-zinc-500">M</span>
          </div>
          
          {/* Arrow */}
          <ArrowRight className="w-4 h-4 text-zinc-600" />
          
          {/* Replicas */}
          <div className="flex gap-1.5">
            {Array.from({ length: Math.min(cluster.replicaCount, 3) }).map((_, i) => (
              <div key={i} className="flex flex-col items-center gap-1">
                <div className="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center">
                  <Server className="w-3.5 h-3.5 text-blue-400" />
                </div>
                <span className="text-[10px] text-zinc-500">R{i + 1}</span>
              </div>
            ))}
            {cluster.replicaCount > 3 && (
              <span className="text-xs text-zinc-500 self-center ml-1">
                +{cluster.replicaCount - 3}
              </span>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between">
          <span className="text-xs text-zinc-500">
            {formatDate(cluster.createdAt)}
          </span>
          <span className="text-xs text-zinc-400 group-hover:text-emerald-400 transition-colors flex items-center gap-1">
            View <ArrowRight className="w-3 h-3" />
          </span>
        </div>
      </div>
    </Link>
  );
}

function MetricItem({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="text-center py-2 px-1 rounded-lg bg-zinc-900/30">
      <p className="text-lg font-semibold text-white">{value}</p>
      <p className="text-[10px] text-zinc-500 uppercase tracking-wide">{label}</p>
    </div>
  );
}

function formatDate(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  
  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return 'Yesterday';
  if (diffDays < 7) return `${diffDays}d ago`;
  
  return new Intl.DateTimeFormat('en-US', { 
    month: 'short', 
    day: 'numeric',
  }).format(date);
}

export default ClusterCard;
