'use client';

import { useState } from 'react';
import { useTasks, useClusters } from '@/lib/api';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow
} from '@/components/ui/table';
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
    Search,
    CheckCircle,
    XCircle,
    Loader2,
    Clock,
    Database,
    RefreshCw,
    Filter
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Skeleton } from '@/components/ui/skeleton';

const statusConfig: Record<string, { icon: React.ReactNode; color: string; label: string }> = {
    PENDING: {
        icon: <Clock className="w-4 h-4" />,
        color: 'text-yellow-400 bg-yellow-500/10',
        label: 'Pending'
    },
    RUNNING: {
        icon: <Loader2 className="w-4 h-4 animate-spin" />,
        color: 'text-blue-400 bg-blue-500/10',
        label: 'Running'
    },
    COMPLETED: {
        icon: <CheckCircle className="w-4 h-4" />,
        color: 'text-emerald-400 bg-emerald-500/10',
        label: 'Completed'
    },
    FAILED: {
        icon: <XCircle className="w-4 h-4" />,
        color: 'text-red-400 bg-red-500/10',
        label: 'Failed'
    },
};

export default function TasksPage() {
    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState('all');
    const [typeFilter, setTypeFilter] = useState('all');

    const { data: tasks, isLoading: tasksLoading, refetch } = useTasks();
    const { data: clusters } = useClusters();

    // Create cluster name lookup
    const clusterNameMap = new Map(clusters?.map(c => [c.id, c.name]) || []);

    const filteredTasks = tasks?.filter(task => {
        const matchesSearch =
            task.id?.toLowerCase().includes(searchTerm.toLowerCase()) ||
            task.type?.toLowerCase().includes(searchTerm.toLowerCase()) ||
            clusterNameMap.get(task.clusterId || '')?.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesStatus = statusFilter === 'all' || task.status === statusFilter;
        const matchesType = typeFilter === 'all' || task.type === typeFilter;
        return matchesSearch && matchesStatus && matchesType;
    }) || [];

    // Get unique task types for filter
    const taskTypes = [...new Set(tasks?.map(t => t.type).filter(Boolean))] as string[];

    if (tasksLoading) {
        return <TasksSkeleton />;
    }

    return (
        <div className="space-y-8 animate-in fade-in duration-500">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-white tracking-tight">Tasks</h1>
                    <p className="text-sm text-zinc-500 mt-1">
                        View all background tasks and their status
                    </p>
                </div>
                <Button
                    variant="outline"
                    onClick={() => refetch()}
                    className="bg-zinc-900/50 border-white/10 text-zinc-300 hover:text-white hover:bg-white/5 gap-2"
                >
                    <RefreshCw className="w-4 h-4" />
                    Refresh
                </Button>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <StatCard
                    label="Total Tasks"
                    value={tasks?.length || 0}
                    color="zinc"
                />
                <StatCard
                    label="Running"
                    value={tasks?.filter(t => t.status === 'RUNNING').length || 0}
                    color="blue"
                />
                <StatCard
                    label="Completed"
                    value={tasks?.filter(t => t.status === 'COMPLETED').length || 0}
                    color="emerald"
                />
                <StatCard
                    label="Failed"
                    value={tasks?.filter(t => t.status === 'FAILED').length || 0}
                    color="red"
                />
            </div>

            {/* Filters */}
            <div className="glass-card rounded-xl p-6">
                <div className="flex flex-wrap items-center gap-4 mb-6">
                    <div className="relative flex-1 min-w-[200px] max-w-sm">
                        <label htmlFor="task-search" className="sr-only">Search tasks</label>
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
                        <Input
                            id="task-search"
                            placeholder="Search by ID, type, or cluster…"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="pl-9 bg-zinc-900/50 border-white/10 text-zinc-300 placeholder:text-zinc-600"
                        />
                    </div>

                    <Select value={statusFilter} onValueChange={setStatusFilter}>
                        <SelectTrigger className="w-[150px] bg-zinc-900/50 border-white/10 text-zinc-300">
                            <Filter className="w-4 h-4 mr-2" />
                            <SelectValue placeholder="Status" />
                        </SelectTrigger>
                        <SelectContent className="bg-zinc-900 border-white/10">
                            <SelectItem value="all">All Status</SelectItem>
                            <SelectItem value="PENDING">Pending</SelectItem>
                            <SelectItem value="RUNNING">Running</SelectItem>
                            <SelectItem value="COMPLETED">Completed</SelectItem>
                            <SelectItem value="FAILED">Failed</SelectItem>
                        </SelectContent>
                    </Select>

                    <Select value={typeFilter} onValueChange={setTypeFilter}>
                        <SelectTrigger className="w-[180px] bg-zinc-900/50 border-white/10 text-zinc-300">
                            <SelectValue placeholder="Task Type" />
                        </SelectTrigger>
                        <SelectContent className="bg-zinc-900 border-white/10">
                            <SelectItem value="all">All Types</SelectItem>
                            {taskTypes.map(type => (
                                <SelectItem key={type} value={type}>{type.replace(/_/g, ' ')}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>

                {/* Table */}
                <div className="rounded-lg border border-white/5 overflow-hidden">
                    <Table>
                        <TableHeader className="bg-zinc-900/50">
                            <TableRow className="hover:bg-transparent border-white/5">
                                <TableHead className="text-zinc-400">Task ID</TableHead>
                                <TableHead className="text-zinc-400">Type</TableHead>
                                <TableHead className="text-zinc-400">Cluster</TableHead>
                                <TableHead className="text-zinc-400">Status</TableHead>
                                <TableHead className="text-zinc-400">Progress</TableHead>
                                <TableHead className="text-zinc-400">Created</TableHead>
                                <TableHead className="text-zinc-400">Completed</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {filteredTasks.length > 0 ? (
                                filteredTasks.map((task) => {
                                    const status = statusConfig[task.status || 'PENDING'] || statusConfig.PENDING;
                                    const createdDate = task.createdAt
                                        ? new Date(task.createdAt).toLocaleString()
                                        : '-';
                                    const completedDate = task.completedAt
                                        ? new Date(task.completedAt).toLocaleString()
                                        : '-';
                                    const clusterName = clusterNameMap.get(task.clusterId || '') || task.clusterId;

                                    return (
                                        <TableRow key={task.id} className="hover:bg-white/[0.02] border-white/5">
                                            <TableCell className="font-mono text-zinc-300 text-sm">
                                                {task.id?.substring(0, 8)}...
                                            </TableCell>
                                            <TableCell>
                                                <span className="px-2 py-1 rounded-md text-[10px] uppercase font-bold tracking-wider bg-zinc-800 text-zinc-400">
                                                    {task.type?.replace(/_/g, ' ')}
                                                </span>
                                            </TableCell>
                                            <TableCell className="text-zinc-300">
                                                <div className="flex items-center gap-2">
                                                    <Database className="w-3.5 h-3.5 text-zinc-500" />
                                                    {clusterName}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <div className={cn("flex items-center gap-2 px-2 py-1 rounded-md w-fit", status.color)}>
                                                    {status.icon}
                                                    <span className="text-sm">{status.label}</span>
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                {task.status === 'RUNNING' ? (
                                                    <div className="w-24 h-2 bg-zinc-800 rounded-full overflow-hidden">
                                                        <div
                                                            className="h-full bg-blue-500 rounded-full transition-all animate-pulse"
                                                            style={{ width: '50%' }}
                                                        />
                                                    </div>
                                                ) : task.status === 'COMPLETED' ? (
                                                    <span className="text-emerald-400 text-sm">100%</span>
                                                ) : task.status === 'FAILED' ? (
                                                    <span className="text-red-400 text-sm">Failed</span>
                                                ) : (
                                                    <span className="text-zinc-500 text-sm">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell className="text-zinc-400 text-sm">{createdDate}</TableCell>
                                            <TableCell className="text-zinc-400 text-sm">{completedDate}</TableCell>
                                        </TableRow>
                                    );
                                })
                            ) : (
                                <TableRow className="hover:bg-transparent">
                                    <TableCell colSpan={7} className="h-32 text-center text-zinc-500">
                                        No tasks found matching your criteria.
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </div>
            </div>
        </div>
    );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
    const colorClasses: Record<string, string> = {
        zinc: 'text-zinc-300',
        blue: 'text-blue-400',
        emerald: 'text-emerald-400',
        red: 'text-red-400',
    };

    return (
        <div className="glass-card rounded-xl p-4 border border-white/5">
            <div className="text-xs text-zinc-500 font-medium uppercase tracking-wider mb-1">
                {label}
            </div>
            <div className={cn("text-2xl font-bold font-mono", colorClasses[color])}>
                {value}
            </div>
        </div>
    );
}

function TasksSkeleton() {
    return (
        <div className="space-y-8">
            <div className="flex justify-between items-center">
                <div className="space-y-2">
                    <Skeleton className="h-8 w-32 bg-zinc-800" />
                    <Skeleton className="h-4 w-48 bg-zinc-800" />
                </div>
                <Skeleton className="h-10 w-24 bg-zinc-800" />
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                {[1, 2, 3, 4].map(i => (
                    <Skeleton key={i} className="h-20 rounded-xl bg-zinc-800" />
                ))}
            </div>
            <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-6">
                <div className="flex gap-4 mb-6">
                    <Skeleton className="h-10 w-64 bg-zinc-800" />
                    <Skeleton className="h-10 w-32 bg-zinc-800" />
                    <Skeleton className="h-10 w-40 bg-zinc-800" />
                </div>
                <div className="space-y-3">
                    {[1, 2, 3, 4, 5].map(i => (
                        <Skeleton key={i} className="h-12 w-full bg-zinc-800" />
                    ))}
                </div>
            </div>
        </div>
    );
}
