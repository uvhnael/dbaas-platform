'use client';

import { Scale, CloudUpload, FileText, Terminal } from 'lucide-react';

interface QuickActionsProps {
  onScale?: () => void;
  onBackup?: () => void;
  onViewLogs?: () => void;
  onOpenConsole?: () => void;
}

function ActionBtn({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-zinc-400 hover:text-white hover:bg-white/5 transition-all"
    >
      <span aria-hidden="true">{icon}</span>
      {label}
    </button>
  );
}

export function QuickActions({ onScale, onBackup, onViewLogs, onOpenConsole }: QuickActionsProps) {
  return (
    <div className="glass-card rounded-xl p-5">
      <h3 className="text-sm font-medium text-white mb-4">Quick Actions</h3>
      <div className="space-y-1">
        <ActionBtn icon={<Scale className="w-4 h-4" />} label="Scale Cluster" onClick={onScale} />
        <ActionBtn icon={<CloudUpload className="w-4 h-4" />} label="Create Backup" onClick={onBackup} />
        <ActionBtn icon={<FileText className="w-4 h-4" />} label="View Logs" onClick={onViewLogs} />
        <ActionBtn icon={<Terminal className="w-4 h-4" />} label="Open Console" onClick={onOpenConsole} />
      </div>
    </div>
  );
}
