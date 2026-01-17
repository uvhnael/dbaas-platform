import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Database, Plus, Sparkles } from 'lucide-react';
import { motion, useReducedMotion } from 'framer-motion';

interface EmptyStateProps {
  title: string;
  description: string;
  showCreate?: boolean;
  icon?: React.ReactNode;
}

export function EmptyState({ 
  title, 
  description, 
  showCreate = true,
  icon 
}: EmptyStateProps) {
  const prefersReducedMotion = useReducedMotion();

  return (
    <motion.div 
      initial={{ opacity: 0, y: prefersReducedMotion ? 0 : 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: prefersReducedMotion ? 0 : 0.5 }}
      className="flex flex-col items-center justify-center py-16 px-4"
    >
      {/* Illustration */}
      <div className="relative mb-8">
        {/* Background glow */}
        <div className="absolute inset-0 bg-gradient-to-r from-blue-500/20 to-purple-500/20 rounded-full blur-3xl" />
        
        {/* Icon container */}
        <motion.div 
          animate={prefersReducedMotion ? {} : { 
            y: [0, -10, 0],
            rotate: [0, 5, -5, 0]
          }}
          transition={{ 
            duration: 4, 
            repeat: Infinity,
            ease: "easeInOut"
          }}
          className="relative w-32 h-32 rounded-full bg-gradient-to-br from-blue-500/20 to-purple-500/20 border border-blue-500/30 flex items-center justify-center"
        >
          {icon || <Database className="w-16 h-16 text-blue-400" />}
          
          {/* Floating sparkles */}
          {!prefersReducedMotion && (
            <>
              <motion.div
                animate={{ 
                  opacity: [0, 1, 0],
                  scale: [0.5, 1, 0.5],
                  y: [-10, -20, -30]
                }}
                transition={{ duration: 2, repeat: Infinity, delay: 0 }}
                className="absolute -top-2 right-0"
              >
                <Sparkles className="w-4 h-4 text-yellow-400" />
              </motion.div>
              <motion.div
                animate={{ 
                  opacity: [0, 1, 0],
                  scale: [0.5, 1, 0.5],
                  y: [-5, -15, -25]
                }}
                transition={{ duration: 2, repeat: Infinity, delay: 0.5 }}
                className="absolute top-4 -left-2"
              >
                <Sparkles className="w-3 h-3 text-purple-400" />
              </motion.div>
              <motion.div
                animate={{ 
                  opacity: [0, 1, 0],
                  scale: [0.5, 1, 0.5],
                  y: [0, -15, -30]
                }}
                transition={{ duration: 2, repeat: Infinity, delay: 1 }}
                className="absolute -top-4 left-1/2"
              >
                <Sparkles className="w-3 h-3 text-cyan-400" />
              </motion.div>
            </>
          )}
        </motion.div>
      </div>

      {/* Text */}
      <h3 className="text-2xl font-bold text-foreground mb-2 text-center">
        {title}
      </h3>
      <p className="text-muted-foreground text-center max-w-md mb-8">
        {description}
      </p>

      {/* CTA */}
      {showCreate && (
        <Link href="/clusters/new">
          <Button size="lg" className="gap-2 shadow-lg shadow-primary/25">
            <Plus className="w-5 h-5" />
            Create Your First Cluster
          </Button>
        </Link>
      )}

      {/* Decorative elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-64 h-64 bg-blue-500/5 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-64 h-64 bg-purple-500/5 rounded-full blur-3xl" />
      </div>
    </motion.div>
  );
}

// Empty state for search results
export function EmptySearchState({ query }: { query: string }) {
  return (
    <motion.div 
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="text-center py-12 bg-card/50 rounded-xl border border-border"
    >
      <div className="w-16 h-16 mx-auto rounded-full bg-muted flex items-center justify-center mb-4">
        <Database className="w-8 h-8 text-muted-foreground" />
      </div>
      <h3 className="text-xl font-semibold text-foreground mb-2">No clusters found</h3>
      <p className="text-muted-foreground mb-2">
        No clusters match "{query}"
      </p>
      <p className="text-sm text-muted-foreground">
        Try a different search term or create a new cluster
      </p>
    </motion.div>
  );
}

export default EmptyState;
