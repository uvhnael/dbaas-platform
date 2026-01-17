'use client';

import { Button } from '@/components/ui/button';
import { Copy, Eye, ExternalLink } from 'lucide-react';
import { toast } from 'sonner';

interface ConnectionDetailsProps {
  clusterId: string;
}

function ConnField({ label, value, onCopy, secret }: { label: string; value: string; onCopy?: () => void; secret?: boolean }) {
  return (
    <div>
      <p className="text-[10px] text-zinc-500 uppercase tracking-wide mb-1">{label}</p>
      <div className="flex items-center gap-2">
        <code className="flex-1 px-3 py-2 bg-zinc-900/50 rounded-lg text-xs text-zinc-300 font-mono truncate">
          {value}
        </code>
        {onCopy && (
          <button onClick={onCopy} aria-label={`Copy ${label}`} className="p-2 hover:bg-white/5 rounded-lg transition-colors text-zinc-500 hover:text-white">
            <Copy className="w-3.5 h-3.5" aria-hidden="true" />
          </button>
        )}
        {secret && (
          <button aria-label={`Show ${label}`} className="p-2 hover:bg-white/5 rounded-lg transition-colors text-zinc-500 hover:text-white">
            <Eye className="w-3.5 h-3.5" aria-hidden="true" />
          </button>
        )}
      </div>
    </div>
  );
}

export function ConnectionDetails({ clusterId }: ConnectionDetailsProps) {
  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('Copied to clipboard');
  };

  return (
    <div className="glass-card rounded-xl p-5">
      <h3 className="text-sm font-medium text-white mb-4">Connection</h3>
      <div className="space-y-3">
        <ConnField label="Host" value={`proxysql-${clusterId}`} onCopy={() => handleCopy(`proxysql-${clusterId}`)} />
        <ConnField label="Write Port" value="6033" />
        <ConnField label="Read Port" value="6034" />
        <ConnField label="Username" value="app_user" onCopy={() => handleCopy('app_user')} />
        <ConnField label="Password" value="••••••••" secret />
      </div>
      <Button variant="outline" size="sm" className="w-full mt-4 bg-transparent border-white/10 text-zinc-400 hover:text-white">
        <ExternalLink className="w-4 h-4 mr-2" aria-hidden="true" />
        Connection String
      </Button>
    </div>
  );
}
