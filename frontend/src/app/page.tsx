'use client';

import Link from 'next/link';
import { useClusters, useDashboardSummary } from '@/lib/api';
import { ClusterCard } from '@/components/ClusterCard';
import { DashboardSkeleton } from '@/components/Skeletons';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { 
  Plus, 
  Database, 
  CheckCircle, 
  Server, 
  Activity,
  BarChart3,
  CloudUpload,
  Settings,
  ArrowRight,
  Cpu,
  HardDrive,
} from 'lucide-react';
import { AreaChart, Area, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

// Mock chart data (will be replaced with real metrics later)
const chartData = [
  { time: '00:00', qps: 800 },
  { time: '04:00', qps: 600 },
  { time: '08:00', qps: 1200 },
  { time: '12:00', qps: 1800 },
  { time: '16:00', qps: 2200 },
  { time: '20:00', qps: 1600 },
  { time: '24:00', qps: 1200 },
];

export default function DashboardPage() {
  const { data: clusters, isLoading: clustersLoading } = useClusters();
  const { data: summary, isLoading: summaryLoading } = useDashboardSummary();

  const isLoading = clustersLoading || summaryLoading;

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  if (!clusters || clusters.length === 0) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <EmptyState 
          title="Welcome to DBaaS Platform"
          description="Create your first MySQL cluster with automatic replication, load balancing, and high availability."
        />
      </div>
    );
  }

  // Use backend data if available, otherwise fallback to calculated values
  const totalClusters = summary?.totalClusters ?? clusters.length;
  const runningClusters = summary?.runningClusters ?? clusters.filter(c => c.status === 'RUNNING' || c.status === 'HEALTHY').length;
  const totalNodes = summary?.totalNodes ?? clusters.reduce((acc, c) => acc + 1 + (c.replicaCount || 0) + 1, 0);
  const healthyNodes = summary?.healthyNodes ?? totalNodes;
  const avgCpu = summary?.resourceUsage?.avgCpuPercent ?? 0;
  const avgMemory = summary?.resourceUsage?.avgMemoryPercent ?? 0;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Dashboard</h1>
          <p className="text-sm text-zinc-500 mt-1">Overview of your database clusters</p>
        </div>
        <Link href="/clusters/new">
          <Button className="gap-2 bg-emerald-600 hover:bg-emerald-500 text-white border-0">
            <Plus className="w-4 h-4" />
            Create Cluster
          </Button>
        </Link>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Clusters"
          value={totalClusters}
          icon={<Database className="w-4 h-4" />}
          color="blue"
        />
        <StatCard
          title="Running"
          value={runningClusters}
          subtitle={`of ${totalClusters}`}
          icon={<CheckCircle className="w-4 h-4" />}
          color="green"
        />
        <StatCard
          title="Healthy Nodes"
          value={healthyNodes}
          subtitle={`of ${totalNodes} total`}
          icon={<Server className="w-4 h-4" />}
          color="purple"
        />
        <StatCard
          title="Avg. CPU"
          value={`${avgCpu.toFixed(1)}%`}
          subtitle={`Memory: ${avgMemory.toFixed(1)}%`}
          icon={<Cpu className="w-4 h-4" />}
          color="orange"
        />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* QPS Chart */}
        <div className="glass-card rounded-xl p-6">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-sm font-medium text-white">Queries Per Second</h3>
            <span className="text-xs text-zinc-500">Last 24h</span>
          </div>
          <div className="h-[180px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorQps" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10b981" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <XAxis 
                  dataKey="time" 
                  stroke="#52525b" 
                  fontSize={11} 
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis 
                  stroke="#52525b" 
                  fontSize={11} 
                  tickLine={false}
                  axisLine={false}
                  width={40}
                />
                <Tooltip 
                  contentStyle={{ 
                    background: 'hsl(240 6% 10%)', 
                    border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: '8px',
                    fontSize: '12px'
                  }}
                  labelStyle={{ color: '#a1a1aa' }}
                />
                <Area 
                  type="monotone" 
                  dataKey="qps" 
                  stroke="#10b981" 
                  fillOpacity={1} 
                  fill="url(#colorQps)" 
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Quick Actions */}
        <div className="glass-card rounded-xl p-6">
          <h3 className="text-sm font-medium text-white mb-6">Quick Actions</h3>
          <div className="grid grid-cols-2 gap-3">
            <QuickAction href="/clusters/new" icon={<Plus className="w-4 h-4" />} label="New Cluster" />
            <QuickAction href="/monitoring" icon={<BarChart3 className="w-4 h-4" />} label="Monitoring" />
            <QuickAction href="/backups" icon={<CloudUpload className="w-4 h-4" />} label="Backups" />
            <QuickAction href="/settings" icon={<Settings className="w-4 h-4" />} label="Settings" />
          </div>
        </div>
      </div>

      {/* Recent Clusters */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-medium text-white">Recent Clusters</h2>
          <Link href="/clusters" className="text-xs text-zinc-400 hover:text-emerald-400 transition-colors flex items-center gap-1">
            View all <ArrowRight className="w-3 h-3" />
          </Link>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {clusters.slice(0, 3).map((cluster) => (
            <ClusterCard key={cluster.id} cluster={cluster} />
          ))}
        </div>
      </div>
    </div>
  );
}

// Components
interface StatCardProps {
  title: string;
  value: number | string;
  subtitle?: string;
  icon: React.ReactNode;
  color: 'blue' | 'green' | 'purple' | 'orange';
}

function StatCard({ title, value, subtitle, icon, color }: StatCardProps) {
  const colors = {
    blue: { bg: 'bg-blue-500/10', text: 'text-blue-400', border: 'border-blue-500/20' },
    green: { bg: 'bg-emerald-500/10', text: 'text-emerald-400', border: 'border-emerald-500/20' },
    purple: { bg: 'bg-violet-500/10', text: 'text-violet-400', border: 'border-violet-500/20' },
    orange: { bg: 'bg-orange-500/10', text: 'text-orange-400', border: 'border-orange-500/20' },
  };

  const c = colors[color];

  return (
    <div className="glass-card rounded-xl p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-zinc-500 uppercase tracking-wide">{title}</p>
          <p className="text-2xl font-bold text-white mt-1">{value}</p>
          {subtitle && <p className="text-xs text-zinc-500 mt-0.5">{subtitle}</p>}
        </div>
        <div className={`p-2.5 rounded-lg ${c.bg} ${c.text} border ${c.border}`}>
          {icon}
        </div>
      </div>
    </div>
  );
}

function QuickAction({ href, icon, label }: { href: string; icon: React.ReactNode; label: string }) {
  return (
    <Link href={href}>
      <div className="p-4 rounded-lg bg-zinc-900/50 border border-white/5 hover:border-white/10 transition-all cursor-pointer group">
        <div className="w-9 h-9 rounded-lg bg-zinc-800/50 flex items-center justify-center text-zinc-400 group-hover:text-emerald-400 group-hover:bg-emerald-500/10 transition-all mb-3">
          {icon}
        </div>
        <p className="text-sm text-zinc-300 group-hover:text-white transition-colors">{label}</p>
      </div>
    </Link>
  );
}
