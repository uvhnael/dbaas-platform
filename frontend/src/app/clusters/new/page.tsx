'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCreateClusterMutation } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from '@/components/ui/select';
import { Loader2, Database, Server, ArrowRight, ChevronRight, Activity } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { motion } from 'framer-motion';

export default function CreateClusterPage() {
  const router = useRouter();
  const createCluster = useCreateClusterMutation();
  
  const [formData, setFormData] = useState({
    name: '',
    mysqlVersion: '8.0',
    replicaCount: 2,
    cpuCores: 2,
    memory: '4G',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!formData.name) {
      toast.error('Please enter a cluster name');
      return;
    }

    createCluster.mutate({
      name: formData.name,
      mysqlVersion: formData.mysqlVersion,
      replicaCount: formData.replicaCount,
      resources: {
        cpuCores: formData.cpuCores,
        memory: formData.memory,
      },
    });
    
    // Redirect after mutation starts (it's async with toast handling)
    setTimeout(() => router.push('/clusters'), 500);
  };

  return (
    <div className="max-w-3xl mx-auto py-8 animate-in fade-in duration-500">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-1.5 text-sm text-zinc-500 mb-8">
        <Link href="/clusters" className="hover:text-white transition-colors">Clusters</Link>
        <ChevronRight className="w-4 h-4" aria-hidden="true" />
        <span className="text-white">Create New</span>
      </nav>

      <div className="flex items-start justify-between mb-10">
        <div>
          <h1 className="text-3xl font-bold text-white tracking-tight">Create Cluster</h1>
          <p className="text-zinc-400 mt-2">Configure distributed database topology</p>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-8">
        {/* Basic Info */}
        <section className="space-y-4">
          <h2 className="text-lg font-semibold text-white">General</h2>
          <div className="glass-card rounded-xl p-6 space-y-6">
            <div className="grid grid-cols-2 gap-6">
              <div className="space-y-2">
                <label htmlFor="cluster-name" className="text-sm font-medium text-zinc-300">Cluster Name</label>
                <Input
                  id="cluster-name"
                  name="clusterName"
                  autoComplete="off"
                  placeholder="production-db-01"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="bg-zinc-900/50 border-white/10 focus:border-emerald-500/50 focus:ring-emerald-500/20"
                />
                <p className="text-[11px] text-zinc-500">Lowercase alphanumeric and hyphens only</p>
              </div>
              <div className="space-y-2">
                <label htmlFor="mysql-version" className="text-sm font-medium text-zinc-300">MySQL Version</label>
                <Select
                  value={formData.mysqlVersion}
                  onValueChange={(value) => setFormData({ ...formData, mysqlVersion: value })}
                >
                  <SelectTrigger id="mysql-version" className="bg-zinc-900/50 border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-zinc-900 border-white/10">
                    <SelectItem value="8.0">MySQL 8.0 (Recommended)</SelectItem>
                    <SelectItem value="8.0.35">MySQL 8.0.35</SelectItem>
                    <SelectItem value="5.7">MySQL 5.7 (Legacy)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </section>

        {/* Topology Configuration */}
        <section className="space-y-4">
          <h2 className="text-lg font-semibold text-white">Topology</h2>
          <div className="glass-card rounded-xl p-6">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <label className="text-sm font-medium text-zinc-300">Read Replicas</label>
                <span className="text-sm font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded">
                  {formData.replicaCount} Nodes
                </span>
              </div>
              
              <div role="radiogroup" aria-label="Number of read replicas" className="flex gap-3">
                {[1, 2, 3, 4, 5].map((num) => (
                  <button
                    key={num}
                    type="button"
                    role="radio"
                    aria-checked={formData.replicaCount === num}
                    onClick={() => setFormData({ ...formData, replicaCount: num })}
                    className={cn(
                      'flex-1 h-12 rounded-lg border font-medium transition-all text-sm',
                      formData.replicaCount === num
                        ? 'border-emerald-500/50 bg-emerald-500/10 text-emerald-400 shadow-[0_0_15px_rgba(16,185,129,0.15)]'
                        : 'border-white/5 bg-zinc-900/50 text-zinc-400 hover:border-white/10 hover:bg-white/5'
                    )}
                  >
                    {num}
                  </button>
                ))}
              </div>

              {/* Dynamic Preview */}
              <div className="mt-8 pt-6 border-t border-white/5">
                <p className="text-xs text-zinc-500 mb-4 uppercase tracking-wider font-medium text-center">Topology Preview</p>
                <div className="flex items-center justify-center gap-4 overflow-x-auto py-4">
                  {/* Master */}
                  <div className="flex flex-col items-center gap-2">
                    <div className="w-14 h-14 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center shadow-[0_0_15px_rgba(16,185,129,0.15)]">
                      <Database className="w-6 h-6 text-emerald-400" aria-hidden="true" />
                    </div>
                    <span className="text-[10px] font-bold text-emerald-400 tracking-wide uppercase">Master</span>
                  </div>
                  
                  {/* Connection Line */}
                  <div className="h-[2px] w-12 bg-gradient-to-r from-emerald-500/30 to-blue-500/30" />
                  
                  {/* Replicas */}
                  <div className="flex gap-2">
                    {Array.from({ length: formData.replicaCount }).map((_, i) => (
                      <motion.div 
                        key={i}
                        initial={{ scale: 0, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        transition={{ duration: 0.2, delay: i * 0.05 }}
                        className="flex flex-col items-center gap-2"
                      >
                        <div className="w-14 h-14 rounded-xl bg-blue-500/10 border border-blue-500/30 flex items-center justify-center">
                          <Server className="w-6 h-6 text-blue-400" aria-hidden="true" />
                        </div>
                        <span className="text-[10px] font-bold text-blue-400 tracking-wide uppercase">Replica {i + 1}</span>
                      </motion.div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Resources */}
        <section className="space-y-4">
          <h2 className="text-lg font-semibold text-white">Resources <span className="text-zinc-500 text-sm font-normal ml-2">(Per Node)</span></h2>
          <div className="glass-card rounded-xl p-6">
            <div className="grid grid-cols-2 gap-6">
              <div className="space-y-2">
                <label htmlFor="cpu-cores" className="text-sm font-medium text-zinc-300">CPU Cores</label>
                <Select
                  value={String(formData.cpuCores)}
                  onValueChange={(value) => setFormData({ ...formData, cpuCores: parseInt(value) })}
                >
                  <SelectTrigger id="cpu-cores" className="bg-zinc-900/50 border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-zinc-900 border-white/10">
                    <SelectItem value="1">1 vCPU</SelectItem>
                    <SelectItem value="2">2 vCPU</SelectItem>
                    <SelectItem value="4">4 vCPU</SelectItem>
                    <SelectItem value="8">8 vCPU</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <label htmlFor="memory" className="text-sm font-medium text-zinc-300">Memory</label>
                <Select
                  value={formData.memory}
                  onValueChange={(value) => setFormData({ ...formData, memory: value })}
                >
                  <SelectTrigger id="memory" className="bg-zinc-900/50 border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-zinc-900 border-white/10">
                    <SelectItem value="2G">2 GB RAM</SelectItem>
                    <SelectItem value="4G">4 GB RAM</SelectItem>
                    <SelectItem value="8G">8 GB RAM</SelectItem>
                    <SelectItem value="16G">16 GB RAM</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </section>

        {/* Calculation Summary */}
        <div className="bg-zinc-900/80 border border-white/5 rounded-xl p-6 mt-8">
          <div className="grid grid-cols-4 gap-8 text-center divide-x divide-white/5">
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total Nodes</p>
              <p className="text-2xl font-bold text-white">{1 + formData.replicaCount}</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total vCPU</p>
              <p className="text-2xl font-bold text-white">{(1 + formData.replicaCount) * formData.cpuCores}</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total RAM</p>
              <p className="text-2xl font-bold text-white">{(1 + formData.replicaCount) * parseInt(formData.memory)} GB</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Estimated Cost</p>
              <p className="text-2xl font-bold text-emerald-400">$84<span className="text-sm text-zinc-500 font-normal">/mo</span></p>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-4 pt-4">
          <Link href="/clusters">
            <Button type="button" variant="ghost" className="text-zinc-400 hover:text-white hover:bg-white/5">
              Cancel
            </Button>
          </Link>
          <Button
            type="submit"
            disabled={createCluster.isPending || !formData.name}
            className="gap-2 bg-emerald-600 hover:bg-emerald-500 text-white min-w-[150px]"
          >
            {createCluster.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Provisioning...
              </>
            ) : (
              <>
                Create Cluster
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </Button>
        </div>
      </form>
    </div>
  );
}
