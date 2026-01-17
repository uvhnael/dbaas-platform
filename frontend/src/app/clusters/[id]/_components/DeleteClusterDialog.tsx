'use client';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Loader2, Trash2 } from 'lucide-react';

interface DeleteClusterDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  clusterName: string;
  onConfirm: () => void;
  isDeleting: boolean;
}

export function DeleteClusterDialog({ 
  open, 
  onOpenChange, 
  clusterName, 
  onConfirm, 
  isDeleting 
}: DeleteClusterDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-zinc-900 border-white/10 max-w-md">
        <DialogHeader>
          <DialogTitle className="text-white flex items-center gap-2">
            <Trash2 className="w-5 h-5 text-red-400" />
            Delete Cluster
          </DialogTitle>
          <DialogDescription className="text-zinc-400">
            Are you sure you want to delete <span className="font-medium text-white">{clusterName}</span>? 
            This action cannot be undone and all data will be permanently lost.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            className="bg-transparent border-white/10 text-zinc-300 hover:text-white hover:bg-white/5"
          >
            Cancel
          </Button>
          <Button
            onClick={onConfirm}
            disabled={isDeleting}
            className="bg-red-500/20 text-red-400 hover:bg-red-500/30 hover:text-red-300 border border-red-500/20"
          >
            {isDeleting ? (
              <Loader2 className="w-4 h-4 animate-spin mr-2" />
            ) : (
              <Trash2 className="w-4 h-4 mr-2" />
            )}
            Delete Cluster
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
