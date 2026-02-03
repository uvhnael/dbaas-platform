'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Loader2, CloudUpload } from 'lucide-react';

interface CreateBackupDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    clusterName: string;
    onConfirm: (backupName?: string) => void;
    isCreating: boolean;
}

export function CreateBackupDialog({
    open,
    onOpenChange,
    clusterName,
    onConfirm,
    isCreating,
}: CreateBackupDialogProps) {
    const [backupName, setBackupName] = useState('');

    const handleConfirm = () => {
        onConfirm(backupName || undefined);
        setBackupName('');
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="bg-zinc-900 border-white/10 max-w-md">
                <DialogHeader>
                    <DialogTitle className="text-white flex items-center gap-2">
                        <CloudUpload className="w-5 h-5 text-emerald-400" />
                        Create Backup
                    </DialogTitle>
                    <DialogDescription className="text-zinc-400">
                        Create a backup of{' '}
                        <span className="font-medium text-white">{clusterName}</span>.
                        This will capture the current state of your database.
                    </DialogDescription>
                </DialogHeader>

                <div className="py-4 space-y-4">
                    <div className="space-y-2">
                        <label htmlFor="backup-name" className="text-sm font-medium text-zinc-300">
                            Backup Name (optional)
                        </label>
                        <Input
                            id="backup-name"
                            placeholder="my-backup"
                            value={backupName}
                            onChange={(e) => setBackupName(e.target.value)}
                            className="bg-zinc-900/50 border-white/10 focus:border-emerald-500/50 focus:ring-emerald-500/20"
                        />
                        <p className="text-xs text-zinc-500">
                            Leave empty for auto-generated name with timestamp
                        </p>
                    </div>
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
                        disabled={isCreating}
                        className="bg-emerald-600 hover:bg-emerald-500 text-white border-0"
                    >
                        {isCreating ? (
                            <Loader2 className="w-4 h-4 animate-spin mr-2" />
                        ) : (
                            <CloudUpload className="w-4 h-4 mr-2" />
                        )}
                        Create Backup
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
