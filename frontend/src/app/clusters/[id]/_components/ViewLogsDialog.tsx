'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select';
import { FileText, RefreshCw, Loader2, Database, Server } from 'lucide-react';
import { useGetClusterLogs, useGetNodeLogs } from '@/lib/api';
import { Node } from '@/lib/api/model';
import { cn } from '@/lib/utils';

interface ViewLogsDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    clusterId: string;
    clusterName: string;
    nodes: Node[];
}

export function ViewLogsDialog({
    open,
    onOpenChange,
    clusterId,
    clusterName,
    nodes,
}: ViewLogsDialogProps) {
    const [selectedNodeId, setSelectedNodeId] = useState<string>('all');
    const [lines, setLines] = useState(100);

    // Fetch cluster logs when "all" is selected
    const { data: clusterLogsData, isLoading: isLoadingClusterLogs, refetch: refetchClusterLogs } = useGetClusterLogs(
        clusterId,
        { lines, timestamps: true },
        {
            query: {
                enabled: open && selectedNodeId === 'all',
            },
        }
    );

    // Fetch single node logs when a specific node is selected
    const { data: nodeLogsData, isLoading: isLoadingNodeLogs, refetch: refetchNodeLogs } = useGetNodeLogs(
        selectedNodeId,
        { lines, timestamps: true },
        {
            query: {
                enabled: open && selectedNodeId !== 'all',
            },
        }
    );

    const isLoading = selectedNodeId === 'all' ? isLoadingClusterLogs : isLoadingNodeLogs;

    const handleRefresh = () => {
        if (selectedNodeId === 'all') {
            refetchClusterLogs();
        } else {
            refetchNodeLogs();
        }
    };

    // Combine logs for display
    const getLogs = (): Array<{ nodeId?: string; containerName?: string; logs: string }> => {
        if (selectedNodeId === 'all') {
            const nodeLogs = clusterLogsData?.data?.nodes || [];
            return nodeLogs.map((nl) => ({
                nodeId: nl.nodeId,
                containerName: nl.containerName,
                logs: nl.logs || 'No logs available',
            }));
        } else {
            const nodeLog = nodeLogsData?.data;
            if (nodeLog) {
                return [{
                    nodeId: nodeLog.nodeId,
                    containerName: nodeLog.containerName,
                    logs: nodeLog.logs || 'No logs available',
                }];
            }
            return [];
        }
    };

    const logs = getLogs();

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="bg-zinc-900 border-white/10 max-w-4xl max-h-[80vh] flex flex-col">
                <DialogHeader>
                    <DialogTitle className="text-white flex items-center gap-2">
                        <FileText className="w-5 h-5 text-blue-400" />
                        Container Logs - {clusterName}
                    </DialogTitle>
                </DialogHeader>

                <div className="flex items-center gap-3 py-2">
                    <Select value={selectedNodeId} onValueChange={setSelectedNodeId}>
                        <SelectTrigger className="w-[200px] bg-zinc-900/50 border-white/10 text-zinc-300">
                            <SelectValue placeholder="Select node" />
                        </SelectTrigger>
                        <SelectContent className="bg-zinc-900 border-white/10">
                            <SelectItem value="all">
                                <div className="flex items-center gap-2">
                                    <Database className="w-4 h-4" />
                                    All Nodes
                                </div>
                            </SelectItem>
                            {nodes.map((node) => (
                                <SelectItem key={node.id} value={node.id || ''}>
                                    <div className="flex items-center gap-2">
                                        <Server className="w-4 h-4" />
                                        {node.containerName}
                                    </div>
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>

                    <Select value={String(lines)} onValueChange={(v) => setLines(Number(v))}>
                        <SelectTrigger className="w-[120px] bg-zinc-900/50 border-white/10 text-zinc-300">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent className="bg-zinc-900 border-white/10">
                            <SelectItem value="50">50 lines</SelectItem>
                            <SelectItem value="100">100 lines</SelectItem>
                            <SelectItem value="200">200 lines</SelectItem>
                            <SelectItem value="500">500 lines</SelectItem>
                        </SelectContent>
                    </Select>

                    <Button
                        variant="outline"
                        size="sm"
                        onClick={handleRefresh}
                        disabled={isLoading}
                        className="bg-transparent border-white/10 text-zinc-300 hover:text-white hover:bg-white/5"
                    >
                        {isLoading ? (
                            <Loader2 className="w-4 h-4 animate-spin" />
                        ) : (
                            <RefreshCw className="w-4 h-4" />
                        )}
                    </Button>
                </div>

                <div className="flex-1 overflow-y-auto min-h-[300px] space-y-4">
                    {isLoading ? (
                        <div className="flex items-center justify-center h-full">
                            <Loader2 className="w-6 h-6 animate-spin text-zinc-500" />
                        </div>
                    ) : logs.length === 0 ? (
                        <div className="flex items-center justify-center h-full text-zinc-500">
                            No logs available
                        </div>
                    ) : (
                        logs.map((log, index) => (
                            <div key={index} className="space-y-2">
                                <div className="flex items-center gap-2 text-sm font-medium text-zinc-400">
                                    <Server className="w-4 h-4" />
                                    {log.containerName}
                                </div>
                                <pre className="bg-zinc-950 rounded-lg p-4 text-xs text-zinc-300 font-mono overflow-x-auto whitespace-pre-wrap max-h-[300px] overflow-y-auto border border-white/5">
                                    {log.logs}
                                </pre>
                            </div>
                        ))
                    )}
                </div>
            </DialogContent>
        </Dialog>
    );
}
