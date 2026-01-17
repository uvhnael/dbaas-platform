'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import { useClusters, useClusterBackups } from '@/lib/api';
import { 
  Table, 
  TableBody, 
  TableCell, 
  TableHead, 
  TableHeader, 
  TableRow 
} from '@/components/ui/table';
import { 
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from '@/components/ui/select';
import { 
  CloudUpload, 
  Download, 
  RotateCcw, 
  Search, 
  Database, 
  CheckCircle, 
  XCircle, 
  Loader2,
  Clock,
  FileBox,
  MonitorCheck
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { Skeleton } from '@/components/ui/skeleton';

export default function BackupsPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [clusterFilter, setClusterFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  
  // Fetch real data from API
  const { data: clusters, isLoading: clustersLoading } = useClusters();
  
  // Fetch backups for all clusters
  const clusterBackups = clusters?.map(cluster => ({
    clusterId: cluster.id,
    clusterName: cluster.name,
    // eslint-disable-next-line react-hooks/rules-of-hooks
    backups: useClusterBackups(cluster.id)
  })) || [];
  
  // Combine all backups from all clusters
  const allBackups = useMemo(() => {
    return clusterBackups.flatMap(cb => 
      (cb.backups.data || []).map(backup => ({
        ...backup,
        clusterName: cb.clusterName,
      }))
    );
  }, [clusterBackups]);
  
  // Create Backup Modal
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedCluster, setSelectedCluster] = useState('production-db');
  
  // Restore Modal
  const [restoreOpen, setRestoreOpen] = useState(false);
  const [restoreBackup, setRestoreBackup] = useState<any>(null);

  const filteredBackups = allBackups.filter(backup => {
    const matchesSearch = backup.id?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesCluster = clusterFilter === 'all' || backup.clusterName === clusterFilter;
    return matchesSearch && matchesCluster;
  });

  const handleCreateBackup = async () => {
    setLoading(true);
    // Simulate API call
    setTimeout(() => {
      setLoading(false);
      setCreateOpen(false);
      toast.success('Backup started successfully', { description: 'You will be notified when it completes.' });
    }, 2000);
  };

  const handleRestore = async () => {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setRestoreOpen(false);
      toast.success('Restore initiated', { description: `Restoring ${restoreBackup?.cluster} from ${restoreBackup?.id}` });
    }, 2000);
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Backups</h1>
          <p className="text-sm text-zinc-500 mt-1">Manage snapshots and point-in-time recovery</p>
        </div>
        <Button 
          onClick={() => setCreateOpen(true)}
          className="bg-emerald-600 hover:bg-emerald-500 text-white gap-2"
        >
          <CloudUpload className="w-4 h-4" />
          Create Backup
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <StatsCard 
          label="Total Storage" 
          value="1.2 TB" 
          icon={<FileBox className="w-4 h-4" />}
          trend="+5.2% this week"
        />
        <StatsCard 
          label="Successful Backups" 
          value="98.5%" 
          icon={<CheckCircle className="w-4 h-4 text-emerald-400" />}
          status="success"
        />
        <StatsCard 
          label="Restoration Time" 
          value="~15 min" 
          icon={<Clock className="w-4 h-4 text-blue-400" />}
          subtitle="Avg. over 30 days"
        />
      </div>

      {/* Main Content */}
      <div className="glass-card rounded-xl p-6">
        {/* Filters */}
        <div className="flex items-center justify-between gap-4 mb-6">
          <div className="flex items-center gap-3 flex-1">
            <div className="relative w-full max-w-sm">
              <label htmlFor="backup-search" className="sr-only">Search backups</label>
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" aria-hidden="true" />
              <Input 
                id="backup-search"
                placeholder="Search backup ID…" 
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9 bg-zinc-900/50 border-white/10 text-zinc-300 placeholder:text-zinc-600 focus:border-white/20 focus:ring-0"
              />
            </div>
            <Select value={clusterFilter} onValueChange={setClusterFilter}>
              <SelectTrigger className="w-[180px] bg-zinc-900/50 border-white/10 text-zinc-300">
                <SelectValue placeholder="All Clusters" />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-white/10 text-zinc-300">
                <SelectItem value="all">All Clusters</SelectItem>
                {clusters?.map(cluster => (
                  <SelectItem key={cluster.id} value={cluster.name}>{cluster.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {/* Table */}
        <div className="rounded-lg border border-white/5 overflow-hidden">
          <Table>
            <TableHeader className="bg-zinc-900/50">
              <TableRow className="hover:bg-transparent border-white/5">
                <TableHead className="text-zinc-400">Backup ID</TableHead>
                <TableHead className="text-zinc-400">Cluster</TableHead>
                <TableHead className="text-zinc-400">Type</TableHead>
                <TableHead className="text-zinc-400">Size</TableHead>
                <TableHead className="text-zinc-400">Status</TableHead>
                <TableHead className="text-zinc-400">Duration</TableHead>
                <TableHead className="text-zinc-400">Created At</TableHead>
                <TableHead className="text-right text-zinc-400">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredBackups.length > 0 ? (
                filteredBackups.map((backup) => {
                  const sizeInGB = backup.sizeBytes ? (backup.sizeBytes / (1024 ** 3)).toFixed(2) : '0';
                  const createdDate = backup.createdAt ? new Date(backup.createdAt).toLocaleString() : '-';
                  const duration = backup.createdAt && backup.completedAt 
                    ? `${Math.round((new Date(backup.completedAt).getTime() - new Date(backup.createdAt).getTime()) / 60000)}m`
                    : '-';
                  
                  return (
                    <TableRow key={backup.id} className="hover:bg-white/[0.02] border-white/5">
                      <TableCell className="font-mono text-zinc-300 font-medium">{backup.id}</TableCell>
                      <TableCell className="text-zinc-300">
                        <div className="flex items-center gap-2">
                          <Database className="w-3.5 h-3.5 text-zinc-500" />
                          {backup.clusterName}
                        </div>
                      </TableCell>
                      <TableCell>
                        <span className="px-2 py-1 rounded-md text-[10px] uppercase font-bold tracking-wider bg-zinc-800 text-zinc-400">
                          {backup.name || 'Manual'}
                        </span>
                      </TableCell>
                      <TableCell className="text-zinc-300 font-mono text-xs">{sizeInGB} GB</TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          {backup.status === 'COMPLETED' ? (
                            <CheckCircle className="w-4 h-4 text-emerald-500" />
                          ) : backup.status === 'IN_PROGRESS' ? (
                            <Loader2 className="w-4 h-4 text-blue-400 animate-spin" />
                          ) : (
                            <XCircle className="w-4 h-4 text-red-500" />
                          )}
                          <span className={cn(
                            "text-sm capitalize",
                            backup.status === 'COMPLETED' && "text-emerald-400",
                            backup.status === 'IN_PROGRESS' && "text-blue-400",
                            backup.status === 'FAILED' && "text-red-400"
                          )}>
                            {backup.status?.toLowerCase().replace('_', ' ')}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="text-zinc-500 text-sm">{duration}</TableCell>
                      <TableCell className="text-zinc-400 text-sm">{createdDate}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button variant="ghost" size="icon" aria-label={`Download backup ${backup.id}`} className="h-8 w-8 text-zinc-400 hover:text-white hover:bg-white/5">
                            <Download className="w-4 h-4" aria-hidden="true" />
                          </Button>
                          <Button 
                            variant="ghost" 
                            size="icon" 
                            aria-label={`Restore backup ${backup.id}`}
                            onClick={() => {
                              setRestoreBackup(backup);
                              setRestoreOpen(true);
                            }}
                            className="h-8 w-8 text-blue-400 hover:text-blue-300 hover:bg-blue-500/10"
                          >
                            <RotateCcw className="w-4 h-4" aria-hidden="true" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })
              ) : (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={8} className="h-32 text-center text-zinc-500">
                    No backups found matching your criteria.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* Create Backup Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="bg-zinc-950 border-white/10 sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-white">Create Manual Backup</DialogTitle>
            <DialogDescription className="text-zinc-400">
              Initiate an immediate backup for a specific cluster. This may impact performance.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <label htmlFor="backup-cluster" className="text-sm font-medium text-zinc-300">Select Cluster</label>
              <Select value={selectedCluster} onValueChange={setSelectedCluster}>
                <SelectTrigger id="backup-cluster" className="bg-zinc-900 border-white/10 text-zinc-200">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="bg-zinc-900 border-white/10">
                  <SelectItem value="production-db">production-db</SelectItem>
                  <SelectItem value="staging-db">staging-db</SelectItem>
                  <SelectItem value="dev-db-01">dev-db-01</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
              <p className="text-xs text-amber-400 flex items-start gap-2">
                <MonitorCheck className="w-4 h-4 shrink-0" />
                This will create a full snapshot of the cluster. Ensure you have enough storage space available.
              </p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setCreateOpen(false)} className="text-zinc-400 hover:text-white">Cancel</Button>
            <Button onClick={handleCreateBackup} disabled={loading} className="bg-emerald-600 hover:bg-emerald-500 text-white">
              {loading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
              Start Backup
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Restore Dialog */}
      <Dialog open={restoreOpen} onOpenChange={setRestoreOpen}>
        <DialogContent className="bg-zinc-950 border-white/10 sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-white">Restore Backup</DialogTitle>
            <DialogDescription className="text-zinc-400">
              Are you sure you want to restore <span className="text-white font-mono break-all">{restoreBackup?.id}</span>?
            </DialogDescription>
          </DialogHeader>
          
          <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-lg space-y-2 my-2">
            <h4 className="text-sm font-bold text-red-500 flex items-center gap-2">
              <XCircle className="w-4 h-4" />
              Warning: Destructive Action
            </h4>
            <ul className="text-xs text-red-400 list-disc list-inside space-y-1">
              <li>Current data on <strong>{restoreBackup?.cluster}</strong> will be overwritten.</li>
              <li>The cluster will be unavailable during restoration.</li>
              <li>This action cannot be undone.</li>
            </ul>
          </div>

          <div className="space-y-2">
             <label htmlFor="confirm-restore" className="text-xs text-zinc-500 uppercase tracking-wider">Type CONFIRM to proceed</label>
             <Input id="confirm-restore" autoComplete="off" className="bg-zinc-900 border-white/10 text-white placeholder:text-zinc-700" placeholder="CONFIRM" />
          </div>

          <DialogFooter>
            <Button variant="ghost" onClick={() => setRestoreOpen(false)} className="text-zinc-400 hover:text-white">Cancel</Button>
            <Button onClick={handleRestore} disabled={loading} variant="destructive">
              {loading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
              Restore Cluster
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function StatsCard({ label, value, icon, trend, status, subtitle }: any) {
  return (
    <div className="glass-card rounded-xl p-5 border border-white/5">
      <div className="flex justify-between items-start mb-2">
        <div className="flex items-center gap-2 text-zinc-500 text-xs font-medium uppercase tracking-wider">
          {icon}
          {label}
        </div>
      </div>
      <div className="flex items-end gap-3">
        <span className={cn("text-2xl font-bold font-mono", status === 'success' ? "text-emerald-400" : "text-white")}>
          {value}
        </span>
        {trend && <span className="text-xs text-emerald-400 mb-1">{trend}</span>}
        {subtitle && <span className="text-xs text-zinc-500 mb-1">{subtitle}</span>}
      </div>
    </div>
  );
}
