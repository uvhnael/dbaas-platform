'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useClusters } from '@/lib/api';
import { 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar
} from 'recharts';
import { 
  Cpu, 
  HardDrive, 
  Activity, 
  Network, 
  ChevronDown,
  RefreshCw,
  Clock
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { 
  Select, 
  SelectContent, 
  SelectItem, 
  SelectTrigger, 
  SelectValue 
} from '@/components/ui/select';
import { DateRangePicker } from '@/components/DateRangePicker';
import { cn } from '@/lib/utils';
import { Skeleton } from '@/components/ui/skeleton';

// Mock Data Generators
const generateTimeSeriesData = (points: number, baseValue: number, variance: number) => {
  const now = new Date();
  return Array.from({ length: points }, (_, i) => {
    const time = new Date(now.getTime() - (points - i) * 60000);
    return {
      time: time.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
      value: Math.max(0, Math.min(100, baseValue + (Math.random() - 0.5) * variance)),
      value2: Math.max(0, Math.min(100, baseValue * 0.7 + (Math.random() - 0.5) * variance)),
    };
  });
};

const cpuData = generateTimeSeriesData(24, 45, 30);
const memoryData = generateTimeSeriesData(24, 60, 10);
const ioData = generateTimeSeriesData(24, 25, 40);
const networkData = generateTimeSeriesData(24, 40, 50);

export default function MonitoringPage() {
  const { data: clusters } = useClusters();
  const [selectedCluster, setSelectedCluster] = useState("");
  const [timeRange, setTimeRange] = useState("1h");
  const [isLoading, setIsLoading] = useState(true);

  // Set first cluster as default when clusters load
  useEffect(() => {
    if (clusters && clusters.length > 0 && !selectedCluster) {
      setSelectedCluster(clusters[0].id);
    }
  }, [clusters, selectedCluster]);

  useEffect(() => {
    // Simulate loading
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 1000);
    return () => clearTimeout(timer);
  }, [selectedCluster, timeRange]);

  if (isLoading) {
    return <MonitoringSkeleton />;
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {/* Header Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">Monitoring</h1>
          <p className="text-sm text-zinc-500 mt-1">Real-time performance metrics</p>
        </div>
        
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex flex-col">
            <label htmlFor="cluster-select" className="sr-only">Select Cluster</label>
            <Select value={selectedCluster} onValueChange={setSelectedCluster}>
              <SelectTrigger id="cluster-select" className="w-[180px] bg-zinc-900/50 border-white/10 text-zinc-300">
                <SelectValue placeholder="Select Cluster" />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-white/10">
                {clusters?.map(cluster => (
                  <SelectItem key={cluster.id} value={cluster.id}>{cluster.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div role="group" aria-label="Time range" className="flex items-center gap-1 bg-zinc-900/50 p-1 rounded-lg border border-white/10">
            {['1h', '6h', '24h', '7d'].map((range) => (
              <button
                key={range}
                onClick={() => setTimeRange(range)}
                aria-pressed={timeRange === range}
                className={cn(
                  "px-3 py-1.5 text-xs font-medium rounded-md transition-all",
                  timeRange === range 
                    ? "bg-zinc-800 text-white shadow-sm" 
                    : "text-zinc-500 hover:text-zinc-300"
                )}
              >
                {range}
              </button>
            ))}
          </div>

          <DateRangePicker />
          
          <Button variant="outline" size="icon" aria-label="Refresh metrics" className="bg-zinc-900/50 border-white/10 text-zinc-400 hover:text-white">
            <RefreshCw className="w-4 h-4" aria-hidden="true" />
          </Button>
        </div>
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <ChartCard 
          title="CPU Usage" 
          subtitle="Average across all nodes"
          icon={<Cpu className="w-4 h-4 text-emerald-400" />}
        >
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={cpuData}>
              <defs>
                <linearGradient id="colorCpu" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#10b981" stopOpacity={0.2}/>
                  <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
              <XAxis dataKey="time" stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} />
              <YAxis stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} unit="%" />
              <Tooltip content={<CustomTooltip />} />
              <Area type="monotone" dataKey="value" stroke="#10b981" fillOpacity={1} fill="url(#colorCpu)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard 
          title="Memory Usage" 
          subtitle="System RAM utilization"
          icon={<HardDrive className="w-4 h-4 text-blue-400" />}
        >
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={memoryData}>
              <defs>
                <linearGradient id="colorMem" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.2}/>
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
              <XAxis dataKey="time" stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} />
              <YAxis stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} unit="%" />
              <Tooltip content={<CustomTooltip />} />
              <Area type="monotone" dataKey="value" stroke="#3b82f6" fillOpacity={1} fill="url(#colorMem)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard 
          title="Disk I/O" 
          subtitle="Read/Write Operations per second"
          icon={<Activity className="w-4 h-4 text-violet-400" />}
        >
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={ioData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
              <XAxis dataKey="time" stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} />
              <YAxis stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} />
              <Tooltip content={<CustomTooltip />} />
              <Bar dataKey="value" name="Read" fill="#8b5cf6" radius={[4, 4, 0, 0]} />
              <Bar dataKey="value2" name="Write" fill="#a78bfa" radius={[4, 4, 0, 0]} fillOpacity={0.5} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard 
          title="Network Traffic" 
          subtitle="Inbound/Outbound bandwidth"
          icon={<Network className="w-4 h-4 text-amber-400" />}
        >
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={networkData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
              <XAxis dataKey="time" stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} />
              <YAxis stroke="#52525b" fontSize={10} tickLine={false} axisLine={false} unit=" MB/s" />
              <Tooltip content={<CustomTooltip />} />
              <Line type="monotone" dataKey="value" name="Inbound" stroke="#f59e0b" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="value2" name="Outbound" stroke="#fbbf24" strokeWidth={2} strokeDasharray="5 5" dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>
    </div>
  );
}

function ChartCard({ title, subtitle, icon, children }: { title: string; subtitle: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="glass-card rounded-xl p-6 flex flex-col h-[350px]">
      <div className="flex items-start justify-between mb-6">
        <div>
          <h3 className="text-base font-semibold text-white flex items-center gap-2">
            {title}
            {icon}
          </h3>
          <p className="text-xs text-zinc-500 mt-0.5">{subtitle}</p>
        </div>
      </div>
      <div className="flex-1 w-full min-h-0">
        {children}
      </div>
    </div>
  );
}

function CustomTooltip({ active, payload, label }: any) {
  if (active && payload && payload.length) {
    return (
      <div className="bg-zinc-900/90 backdrop-blur border border-white/10 p-3 rounded-lg shadow-xl text-xs">
        <p className="text-zinc-400 mb-2">{label}</p>
        {payload.map((entry: any, index: number) => (
          <div key={index} className="flex items-center gap-2 mb-1 last:mb-0">
            <div 
              className="w-2 h-2 rounded-full" 
              style={{ backgroundColor: entry.color || entry.fill }}
            />
            <span className="text-zinc-300 capitalize">
              {entry.name || 'Value'}:
            </span>
            <span className="font-mono font-medium text-white">
              {entry.value.toFixed(1)}
              {entry.unit}
            </span>
          </div>
        ))}
      </div>
    );
  }
  return null;
}

function MonitoringSkeleton() {
  return (
    <div className="space-y-8">
       <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="space-y-2">
          <Skeleton className="h-8 w-48 bg-zinc-800" />
          <Skeleton className="h-4 w-32 bg-zinc-800" />
        </div>
        <div className="flex gap-3">
          <Skeleton className="h-10 w-[180px] bg-zinc-800" />
          <Skeleton className="h-10 w-[200px] bg-zinc-800" />
        </div>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-[350px] rounded-xl border border-zinc-800 bg-zinc-900/50 p-6 space-y-4">
            <div className="flex justify-between">
              <div className="space-y-2">
                <Skeleton className="h-5 w-32 bg-zinc-800" />
                <Skeleton className="h-3 w-24 bg-zinc-800" />
              </div>
              <Skeleton className="h-8 w-8 rounded-full bg-zinc-800" />
            </div>
            <Skeleton className="h-[240px] w-full bg-zinc-800/50" />
          </div>
        ))}
      </div>
    </div>
  );
}
