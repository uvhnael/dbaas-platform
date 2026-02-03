'use client';

import { useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { AlertCircle, RefreshCw, Home } from 'lucide-react';
import Link from 'next/link';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log the error to an error reporting service
    console.error('App Error:', error);
  }, [error]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-950 px-4">
      <div className="glass-card p-8 rounded-xl max-w-md w-full text-center border border-red-500/20 shadow-lg shadow-red-500/10">
        <div className="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
          <AlertCircle className="w-8 h-8 text-red-500" />
        </div>
        
        <h2 className="text-2xl font-bold text-white mb-2">Something went wrong!</h2>
        <p className="text-zinc-400 mb-6">
          {error.message || "An unexpected error occurred while rendering this page."}
        </p>

        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Button 
            onClick={() => reset()} 
            variant="default"
            className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700"
          >
            <RefreshCw className="w-4 h-4" />
            Try again
          </Button>
          
          <Link href="/">
            <Button variant="outline" className="w-full sm:w-auto flex items-center gap-2 border-zinc-700 hover:bg-zinc-800 text-zinc-300">
              <Home className="w-4 h-4" />
              Go Home
            </Button>
          </Link>
        </div>
        
        {error.digest && (
          <p className="mt-6 text-xs text-zinc-600 font-mono">
            Error ID: {error.digest}
          </p>
        )}
      </div>
    </div>
  );
}
