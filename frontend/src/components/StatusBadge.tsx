import { ClusterStatus } from '@/types';
import { cn } from '@/lib/utils';

interface StatusBadgeProps {
  status: ClusterStatus;
  size?: 'sm' | 'md';
}

const statusConfig: Record<ClusterStatus, { label: string; dotClass: string; textClass: string }> = {
  PROVISIONING: { 
    label: 'Provisioning', 
    dotClass: 'bg-blue-400 animate-pulse',
    textClass: 'text-blue-400'
  },
  HEALTHY: { 
    label: 'Healthy', 
    dotClass: 'status-dot-online',
    textClass: 'text-emerald-400'
  },
  RUNNING: { 
    label: 'Running', 
    dotClass: 'status-dot-online',
    textClass: 'text-emerald-400'
  },
  DEGRADED: { 
    label: 'Degraded', 
    dotClass: 'status-dot-syncing',
    textClass: 'text-amber-400'
  },
  STOPPED: { 
    label: 'Stopped', 
    dotClass: 'bg-zinc-500',
    textClass: 'text-zinc-400'
  },
  DELETING: { 
    label: 'Deleting', 
    dotClass: 'bg-orange-400 animate-pulse',
    textClass: 'text-orange-400'
  },
  FAILED: { 
    label: 'Failed', 
    dotClass: 'status-dot-offline',
    textClass: 'text-red-400'
  },
};

export function StatusBadge({ status, size = 'md' }: StatusBadgeProps) {
  const config = statusConfig[status] || {
    label: status,
    dotClass: 'bg-zinc-500',
    textClass: 'text-zinc-400'
  };
  
  return (
    <div className={cn(
      'inline-flex items-center gap-2',
      size === 'sm' ? 'text-xs' : 'text-sm'
    )}>
      <span className={cn(
        'rounded-full',
        size === 'sm' ? 'w-1.5 h-1.5' : 'w-2 h-2',
        config.dotClass
      )} />
      <span className={cn('font-medium', config.textClass)}>
        {config.label}
      </span>
    </div>
  );
}

// Minimal inline status dot (for tables)
export function StatusDot({ status }: { status: 'online' | 'offline' | 'syncing' }) {
  return (
    <span className={cn(
      'inline-block',
      status === 'online' && 'status-dot-online',
      status === 'syncing' && 'status-dot-syncing',
      status === 'offline' && 'status-dot-offline'
    )} />
  );
}

export default StatusBadge;
