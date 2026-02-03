'use client';

import { Button } from '@/components/ui/button';
import { useClusterConnection } from '@/lib/api';
import { Copy, Eye, EyeOff, ExternalLink, Loader2 } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

interface ConnectionDetailsProps {
  clusterId: string;
}

function ConnField({
  label,
  value,
  onCopy,
  secret,
  showSecret,
  onToggleSecret
}: {
  label: string;
  value: string;
  onCopy?: () => void;
  secret?: boolean;
  showSecret?: boolean;
  onToggleSecret?: () => void;
}) {
  const displayValue = secret && !showSecret ? '••••••••' : value;

  return (
    <div>
      <p className="text-[10px] text-zinc-500 uppercase tracking-wide mb-1">{label}</p>
      <div className="flex items-center gap-2">
        <code className="flex-1 px-3 py-2 bg-zinc-900/50 rounded-lg text-xs text-zinc-300 font-mono truncate">
          {displayValue}
        </code>
        {onCopy && (
          <button
            onClick={() => onCopy()}
            aria-label={`Copy ${label}`}
            className="p-2 hover:bg-white/5 rounded-lg transition-colors text-zinc-500 hover:text-white"
          >
            <Copy className="w-3.5 h-3.5" aria-hidden="true" />
          </button>
        )}
        {secret && onToggleSecret && (
          <button
            onClick={onToggleSecret}
            aria-label={showSecret ? `Hide ${label}` : `Show ${label}`}
            className="p-2 hover:bg-white/5 rounded-lg transition-colors text-zinc-500 hover:text-white"
          >
            {showSecret ? (
              <EyeOff className="w-3.5 h-3.5" aria-hidden="true" />
            ) : (
              <Eye className="w-3.5 h-3.5" aria-hidden="true" />
            )}
          </button>
        )}
      </div>
    </div>
  );
}

export function ConnectionDetails({ clusterId }: ConnectionDetailsProps) {
  const { data: connection, isLoading, error } = useClusterConnection(clusterId);
  const [showPassword, setShowPassword] = useState(false);
  const [showRootPassword, setShowRootPassword] = useState(false);
  const [showConnectionString, setShowConnectionString] = useState(false);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('Copied to clipboard');
  };

  if (isLoading) {
    return (
      <div className="glass-card rounded-xl p-5">
        <h3 className="text-sm font-medium text-white mb-4">Connection</h3>
        <div className="flex items-center justify-center py-8">
          <Loader2 className="w-5 h-5 animate-spin text-zinc-500" />
        </div>
      </div>
    );
  }

  if (error || !connection) {
    return (
      <div className="glass-card rounded-xl p-5">
        <h3 className="text-sm font-medium text-white mb-4">Connection</h3>
        <p className="text-xs text-zinc-500">Connection info unavailable</p>
      </div>
    );
  }

  return (
    <div className="glass-card rounded-xl p-5">
      <h3 className="text-sm font-medium text-white mb-4">Connection</h3>
      <div className="space-y-3">
        <ConnField
          label="Host"
          value={connection.host ?? ''}
          onCopy={() => handleCopy(connection.host ?? '')}
        />
        <ConnField
          label="Port"
          value={String(connection.port ?? 6033)}
          onCopy={() => handleCopy(String(connection.port ?? 6033))}
        />
        <ConnField
          label="Username"
          value={connection.username ?? ''}
          onCopy={() => handleCopy(connection.username ?? '')}
        />
        <ConnField
          label="Password"
          value={connection.password ?? ''}
          secret
          showSecret={showPassword}
          onToggleSecret={() => setShowPassword(!showPassword)}
          onCopy={() => handleCopy(connection.password ?? '')}
        />
        <ConnField
          label="Root Password"
          value={connection.rootPassword ?? ''}
          secret
          showSecret={showRootPassword}
          onToggleSecret={() => setShowRootPassword(!showRootPassword)}
          onCopy={() => handleCopy(connection.rootPassword ?? '')}
        />
      </div>

      {/* Connection String Section */}
      <div className="mt-4 pt-4 border-t border-white/5">
        <Button
          variant="outline"
          size="sm"
          className="w-full bg-transparent border-white/10 text-zinc-400 hover:text-white"
          onClick={() => setShowConnectionString(!showConnectionString)}
        >
          <ExternalLink className="w-4 h-4 mr-2" aria-hidden="true" />
          {showConnectionString ? 'Hide Connection String' : 'Show Connection String'}
        </Button>

        {showConnectionString && (
          <div className="mt-3">
            <div>
              <p className="text-[10px] text-zinc-500 uppercase tracking-wide mb-1">Connection String</p>
              <div className="flex items-center gap-2">
                <code className="flex-1 px-3 py-2 bg-zinc-900/50 rounded-lg text-[10px] text-zinc-300 font-mono break-all">
                  {connection.connectionString}
                </code>
                <button
                  onClick={() => handleCopy(connection.connectionString ?? '')}
                  className="p-2 hover:bg-white/5 rounded-lg transition-colors text-zinc-500 hover:text-white shrink-0"
                >
                  <Copy className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
