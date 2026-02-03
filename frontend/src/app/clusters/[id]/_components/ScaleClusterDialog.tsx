'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
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
import { Loader2, Scale, Server, Cpu, MemoryStick, HardDrive, Settings2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

// Resource configuration for new replicas
interface ReplicaResourceConfig {
    cpuCores: number;
    memory: string;
    storage: string;
}

interface ScaleClusterDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    currentReplicaCount: number;
    clusterName: string;
    onConfirm: (replicaCount: number, resources?: ReplicaResourceConfig) => void;
    isScaling: boolean;
}

export function ScaleClusterDialog({
    open,
    onOpenChange,
    currentReplicaCount,
    clusterName,
    onConfirm,
    isScaling,
}: ScaleClusterDialogProps) {
    const [selectedCount, setSelectedCount] = useState(currentReplicaCount);
    const [showResourceConfig, setShowResourceConfig] = useState(false);
    const [resourceConfig, setResourceConfig] = useState<ReplicaResourceConfig>({
        cpuCores: 2,
        memory: '4G',
        storage: '10G',
    });

    // Reset when dialog opens
    useEffect(() => {
        if (open) {
            setSelectedCount(currentReplicaCount);
            setShowResourceConfig(false);
        }
    }, [open, currentReplicaCount]);

    const handleConfirm = () => {
        if (selectedCount > currentReplicaCount && showResourceConfig) {
            onConfirm(selectedCount, resourceConfig);
        } else {
            onConfirm(selectedCount);
        }
    };

    const isScalingUp = selectedCount > currentReplicaCount;
    const newReplicasCount = selectedCount - currentReplicaCount;

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="bg-zinc-900 border-white/10 max-w-lg">
                <DialogHeader>
                    <DialogTitle className="text-white flex items-center gap-2">
                        <Scale className="w-5 h-5 text-emerald-400" />
                        Scale Cluster
                    </DialogTitle>
                    <DialogDescription className="text-zinc-400">
                        Adjust the number of read replicas for{' '}
                        <span className="font-medium text-white">{clusterName}</span>.
                        Changes will be applied immediately.
                    </DialogDescription>
                </DialogHeader>

                <div className="py-4 space-y-4">
                    <div className="flex items-center justify-between">
                        <label className="text-sm font-medium text-zinc-300">Read Replicas</label>
                        <span className="text-sm font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded">
                            {selectedCount} Node{selectedCount !== 1 ? 's' : ''}
                        </span>
                    </div>

                    <div className="flex gap-2">
                        {[1, 2, 3, 4, 5].map((num) => (
                            <button
                                key={num}
                                type="button"
                                onClick={() => setSelectedCount(num)}
                                className={cn(
                                    'flex-1 h-12 rounded-lg border font-medium transition-all text-sm flex items-center justify-center gap-2',
                                    selectedCount === num
                                        ? 'border-emerald-500/50 bg-emerald-500/10 text-emerald-400 shadow-[0_0_15px_rgba(16,185,129,0.15)]'
                                        : 'border-white/5 bg-zinc-900/50 text-zinc-400 hover:border-white/10 hover:bg-white/5'
                                )}
                            >
                                <Server className="w-4 h-4" />
                                {num}
                            </button>
                        ))}
                    </div>

                    {selectedCount !== currentReplicaCount && (
                        <div className={cn(
                            "p-3 rounded-lg border",
                            isScalingUp 
                                ? "bg-blue-500/10 border-blue-500/20" 
                                : "bg-amber-500/10 border-amber-500/20"
                        )}>
                            <p className={cn("text-sm", isScalingUp ? "text-blue-400" : "text-amber-400")}>
                                {isScalingUp
                                    ? `${newReplicasCount} replica(s) will be added`
                                    : `${currentReplicaCount - selectedCount} replica(s) will be removed`}
                            </p>
                        </div>
                    )}

                    {/* Resource Configuration for Scale Up */}
                    <AnimatePresence>
                        {isScalingUp && (
                            <motion.div
                                initial={{ opacity: 0, height: 0 }}
                                animate={{ opacity: 1, height: 'auto' }}
                                exit={{ opacity: 0, height: 0 }}
                                className="space-y-3"
                            >
                                <div className="flex items-center justify-between pt-2">
                                    <span className="text-sm text-zinc-400">Configure new replica resources</span>
                                    <button
                                        type="button"
                                        onClick={() => setShowResourceConfig(!showResourceConfig)}
                                        className={cn(
                                            "flex items-center gap-1.5 px-2 py-1 rounded text-xs font-medium transition-all",
                                            showResourceConfig
                                                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                                                : "bg-zinc-800 text-zinc-400 hover:text-white"
                                        )}
                                    >
                                        <Settings2 className="w-3 h-3" />
                                        {showResourceConfig ? 'Custom' : 'Default'}
                                    </button>
                                </div>

                                <AnimatePresence>
                                    {showResourceConfig && (
                                        <motion.div
                                            initial={{ opacity: 0, y: -10 }}
                                            animate={{ opacity: 1, y: 0 }}
                                            exit={{ opacity: 0, y: -10 }}
                                            className="grid grid-cols-3 gap-3 p-4 rounded-lg bg-zinc-800/50 border border-white/5"
                                        >
                                            <div className="space-y-1.5">
                                                <label className="text-[10px] text-zinc-500 uppercase tracking-wider flex items-center gap-1">
                                                    <Cpu className="w-3 h-3" /> CPU
                                                </label>
                                                <Select
                                                    value={String(resourceConfig.cpuCores)}
                                                    onValueChange={(value) => setResourceConfig(prev => ({ ...prev, cpuCores: parseInt(value) }))}
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
                                                    value={resourceConfig.memory}
                                                    onValueChange={(value) => setResourceConfig(prev => ({ ...prev, memory: value }))}
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
                                                    value={resourceConfig.storage}
                                                    onValueChange={(value) => setResourceConfig(prev => ({ ...prev, storage: value }))}
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
                                                    </SelectContent>
                                                </Select>
                                            </div>
                                        </motion.div>
                                    )}
                                </AnimatePresence>

                                {showResourceConfig && (
                                    <p className="text-xs text-zinc-500">
                                        These settings will apply to all {newReplicasCount} new replica(s).
                                    </p>
                                )}
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>

                <DialogFooter className="gap-2 sm:gap-0">
                    <Button
                        variant="outline"
                        onClick={() => onOpenChange(false)}
                        className="bg-transparent border-white/10 text-zinc-300 hover:text-white hover:bg-white/5"
                    >
                        Cancel
                    </Button>
                    <Button
                        onClick={handleConfirm}
                        disabled={isScaling || selectedCount === currentReplicaCount}
                        className="bg-emerald-600 hover:bg-emerald-500 text-white border-0"
                    >
                        {isScaling ? (
                            <Loader2 className="w-4 h-4 animate-spin mr-2" />
                        ) : (
                            <Scale className="w-4 h-4 mr-2" />
                        )}
                        Apply Changes
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
