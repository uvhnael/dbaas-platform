'use client';

import { useState, useEffect } from 'react';
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
import { Loader2, Database, Server, ArrowRight, ChevronRight, Settings2, HardDrive, Cpu, MemoryStick } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

// Node configuration interface
interface NodeConfig {
  cpuCores: number;
  memory: string;
  storage: string;
}

// Default node config
const defaultNodeConfig: NodeConfig = {
  cpuCores: 2,
  memory: '4G',
  storage: '10G',
};

export default function CreateClusterPage() {
  const router = useRouter();
  const createCluster = useCreateClusterMutation();

  const [formData, setFormData] = useState({
    name: '',
    mysqlVersion: '8.0',
    replicaCount: 2,
  });

  // Per-node configurations
  const [masterConfig, setMasterConfig] = useState<NodeConfig>({ ...defaultNodeConfig });
  const [replicaConfigs, setReplicaConfigs] = useState<NodeConfig[]>([]);
  const [useCustomConfig, setUseCustomConfig] = useState(false);

  // Initialize replica configs when replica count changes
  useEffect(() => {
    const newConfigs: NodeConfig[] = [];
    for (let i = 0; i < formData.replicaCount; i++) {
      newConfigs.push(replicaConfigs[i] || { ...defaultNodeConfig });
    }
    setReplicaConfigs(newConfigs);
  }, [formData.replicaCount]);

  const updateReplicaConfig = (index: number, field: keyof NodeConfig, value: string | number) => {
    const newConfigs = [...replicaConfigs];
    newConfigs[index] = { ...newConfigs[index], [field]: value };
    setReplicaConfigs(newConfigs);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name) {
      toast.error('Please enter a cluster name');
      return;
    }

    // Build request with per-node configs
    const requestData: any = {
      name: formData.name,
      mysqlVersion: formData.mysqlVersion,
      replicaCount: formData.replicaCount,
    };

    if (useCustomConfig) {
      requestData.masterConfig = masterConfig;
      requestData.replicaConfigs = replicaConfigs;
    } else {
      // Use master config for all nodes
      requestData.resources = {
        cpuCores: masterConfig.cpuCores,
        memory: masterConfig.memory,
      };
    }

    createCluster.mutate(requestData, {
      onSuccess: () => {
        router.push('/clusters');
      },
    });
  };

  // Calculate totals
  const calculateTotals = () => {
    let totalCpu = masterConfig.cpuCores;
    let totalMemoryGB = parseInt(masterConfig.memory);
    let totalStorageGB = parseInt(masterConfig.storage);

    if (useCustomConfig) {
      replicaConfigs.forEach(config => {
        totalCpu += config.cpuCores;
        totalMemoryGB += parseInt(config.memory);
        totalStorageGB += parseInt(config.storage);
      });
    } else {
      totalCpu += masterConfig.cpuCores * formData.replicaCount;
      totalMemoryGB += parseInt(masterConfig.memory) * formData.replicaCount;
      totalStorageGB += parseInt(masterConfig.storage) * formData.replicaCount;
    }

    return { totalCpu, totalMemoryGB, totalStorageGB };
  };

  const totals = calculateTotals();

  // Node config card component
  const NodeConfigCard = ({
    title,
    icon: Icon,
    config,
    onChange,
    accentColor = 'emerald'
  }: {
    title: string;
    icon: any;
    config: NodeConfig;
    onChange: (field: keyof NodeConfig, value: string | number) => void;
    accentColor?: 'emerald' | 'blue';
  }) => {
    const colorClasses = {
      emerald: {
        bg: 'bg-emerald-500/10',
        border: 'border-emerald-500/30',
        text: 'text-emerald-400',
        icon: 'text-emerald-400',
      },
      blue: {
        bg: 'bg-blue-500/10',
        border: 'border-blue-500/30',
        text: 'text-blue-400',
        icon: 'text-blue-400',
      },
    };
    const colors = colorClasses[accentColor];

    return (
      <div className={cn("rounded-xl border p-4 space-y-4", colors.border, colors.bg)}>
        <div className="flex items-center gap-2">
          <div className={cn("w-8 h-8 rounded-lg flex items-center justify-center", colors.bg, colors.border, "border")}>
            <Icon className={cn("w-4 h-4", colors.icon)} />
          </div>
          <span className={cn("text-sm font-semibold", colors.text)}>{title}</span>
        </div>

        <div className="grid grid-cols-3 gap-3">
          <div className="space-y-1.5">
            <label className="text-[10px] text-zinc-500 uppercase tracking-wider flex items-center gap-1">
              <Cpu className="w-3 h-3" /> CPU
            </label>
            <Select
              value={String(config.cpuCores)}
              onValueChange={(value) => onChange('cpuCores', parseInt(value))}
            >
              <SelectTrigger className="h-9 bg-zinc-900/50 border-white/10 text-sm">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-white/10">
                <SelectItem value="1">1 vCPU</SelectItem>
                <SelectItem value="2">2 vCPU</SelectItem>
                <SelectItem value="4">4 vCPU</SelectItem>
                <SelectItem value="8">8 vCPU</SelectItem>
                <SelectItem value="16">16 vCPU</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <label className="text-[10px] text-zinc-500 uppercase tracking-wider flex items-center gap-1">
              <MemoryStick className="w-3 h-3" /> RAM
            </label>
            <Select
              value={config.memory}
              onValueChange={(value) => onChange('memory', value)}
            >
              <SelectTrigger className="h-9 bg-zinc-900/50 border-white/10 text-sm">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-white/10">
                <SelectItem value="2G">2 GB</SelectItem>
                <SelectItem value="4G">4 GB</SelectItem>
                <SelectItem value="8G">8 GB</SelectItem>
                <SelectItem value="16G">16 GB</SelectItem>
                <SelectItem value="32G">32 GB</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <label className="text-[10px] text-zinc-500 uppercase tracking-wider flex items-center gap-1">
              <HardDrive className="w-3 h-3" /> Storage
            </label>
            <Select
              value={config.storage}
              onValueChange={(value) => onChange('storage', value)}
            >
              <SelectTrigger className="h-9 bg-zinc-900/50 border-white/10 text-sm">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-white/10">
                <SelectItem value="10G">10 GB</SelectItem>
                <SelectItem value="20G">20 GB</SelectItem>
                <SelectItem value="50G">50 GB</SelectItem>
                <SelectItem value="100G">100 GB</SelectItem>
                <SelectItem value="200G">200 GB</SelectItem>
                <SelectItem value="500G">500 GB</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="max-w-4xl mx-auto py-8 animate-in fade-in duration-500">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-1.5 text-sm text-zinc-500 mb-8">
        <Link href="/clusters" className="hover:text-white transition-colors">Clusters</Link>
        <ChevronRight className="w-4 h-4" aria-hidden="true" />
        <span className="text-white">Create New</span>
      </nav>

      <div className="flex items-start justify-between mb-10">
        <div>
          <h1 className="text-3xl font-bold text-white tracking-tight">Create Cluster</h1>
          <p className="text-zinc-400 mt-2">Configure distributed database topology with per-node resources</p>
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

        {/* Resources Configuration */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white">Resources</h2>
            <button
              type="button"
              onClick={() => setUseCustomConfig(!useCustomConfig)}
              className={cn(
                "flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-all",
                useCustomConfig
                  ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                  : "bg-zinc-800 text-zinc-400 border border-white/5 hover:border-white/10"
              )}
            >
              <Settings2 className="w-4 h-4" />
              {useCustomConfig ? 'Per-Node Config' : 'Uniform Config'}
            </button>
          </div>

          <div className="glass-card rounded-xl p-6 space-y-6">
            <AnimatePresence mode="wait">
              {useCustomConfig ? (
                <motion.div
                  key="custom"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="space-y-4"
                >
                  <p className="text-xs text-zinc-500 uppercase tracking-wider font-medium">Per-Node Configuration</p>

                  {/* Master Config */}
                  <NodeConfigCard
                    title="Master Node"
                    icon={Database}
                    config={masterConfig}
                    onChange={(field, value) => setMasterConfig(prev => ({ ...prev, [field]: value }))}
                    accentColor="emerald"
                  />

                  {/* Replica Configs */}
                  <div className="space-y-3">
                    {replicaConfigs.map((config, index) => (
                      <motion.div
                        key={index}
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: index * 0.1 }}
                      >
                        <NodeConfigCard
                          title={`Replica ${index + 1}`}
                          icon={Server}
                          config={config}
                          onChange={(field, value) => updateReplicaConfig(index, field, value)}
                          accentColor="blue"
                        />
                      </motion.div>
                    ))}
                  </div>
                </motion.div>
              ) : (
                <motion.div
                  key="uniform"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="space-y-4"
                >
                  <p className="text-xs text-zinc-500 uppercase tracking-wider font-medium">Same Configuration for All Nodes</p>

                  <NodeConfigCard
                    title="All Nodes"
                    icon={Database}
                    config={masterConfig}
                    onChange={(field, value) => setMasterConfig(prev => ({ ...prev, [field]: value }))}
                    accentColor="emerald"
                  />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </section>

        {/* Calculation Summary */}
        <div className="bg-zinc-900/80 border border-white/5 rounded-xl p-6 mt-8">
          <div className="grid grid-cols-5 gap-6 text-center divide-x divide-white/5">
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total Nodes</p>
              <p className="text-2xl font-bold text-white">{1 + formData.replicaCount}</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total vCPU</p>
              <p className="text-2xl font-bold text-white">{totals.totalCpu}</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total RAM</p>
              <p className="text-2xl font-bold text-white">{totals.totalMemoryGB} GB</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Total Storage</p>
              <p className="text-2xl font-bold text-white">{totals.totalStorageGB} GB</p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 uppercase tracking-widest mb-1">Estimated Cost</p>
              <p className="text-2xl font-bold text-emerald-400">
                ${Math.round(totals.totalCpu * 10 + totals.totalMemoryGB * 5 + totals.totalStorageGB * 0.1)}
                <span className="text-sm text-zinc-500 font-normal">/mo</span>
              </p>
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
