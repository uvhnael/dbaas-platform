'use client';

import Link from 'next/link';
import { useClusters } from '@/lib/api';
import { ClusterCard } from '@/components/ClusterCard';
import { ClusterCardSkeleton } from '@/components/Skeletons';
import { EmptyState, EmptySearchState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Plus, Search } from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/utils';

const filters = ['All', 'Provisioning', 'Healthy', 'Running', 'Degraded', 'Stopped', 'Deleting', 'Failed'];

export default function ClustersPage() {
  const { data: clusters, isLoading } = useClusters();
  const [activeFilter, setActiveFilter] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');

  const filteredClusters = (clusters as any[])?.filter((cluster: any) => {
    const matchesFilter = activeFilter === 'All' ||
      cluster.status.toUpperCase() === activeFilter.toUpperCase();
    const matchesSearch = cluster.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      cluster.id.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  const runningCount = (clusters as any[])?.filter((c: any) => c.status === 'RUNNING').length || 0;
  const degradedCount = (clusters as any[])?.filter((c: any) => c.status === 'DEGRADED').length || 0;

  // Show skeleton while loading
  if (isLoading) {
    return (
      <div className="space-y-6 animate-in fade-in duration-500">
        <div className="flex items-center justify-between">
          <div>
            <div className="h-9 w-32 bg-muted rounded animate-pulse" />
            <div className="h-5 w-48 bg-muted rounded animate-pulse mt-2" />
          </div>
          <div className="h-10 w-36 bg-muted rounded animate-pulse" />
        </div>
        <div className="flex items-center gap-4">
          {[1, 2, 3, 4, 5].map(i => (
            <div key={i} className="h-9 w-20 bg-muted rounded animate-pulse" />
          ))}
          <div className="flex-1" />
          <div className="h-10 w-64 bg-muted rounded animate-pulse" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3, 4, 5, 6].map(i => (
            <ClusterCardSkeleton key={i} />
          ))}
        </div>
      </div>
    );
  }

  // Show empty state if no clusters exist at all
  if (!clusters || clusters.length === 0) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <EmptyState
          title="No Clusters Yet"
          description="Create your first MySQL cluster with automatic replication, load balancing, and high availability."
        />
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground">Clusters</h1>
          <p className="text-muted-foreground mt-1">
            {(clusters as any[])?.length || 0} clusters • {runningCount} running
            {degradedCount > 0 && <span className="text-yellow-400"> • {degradedCount} degraded</span>}
          </p>
        </div>
        <Link href="/clusters/new">
          <Button className="gap-2">
            <Plus className="w-4 h-4" />
            Create Cluster
          </Button>
        </Link>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          {filters.map((filter) => (
            <Button
              key={filter}
              variant={activeFilter === filter ? 'default' : 'outline'}
              size="sm"
              onClick={() => setActiveFilter(filter)}
              className={cn(
                activeFilter === filter && 'bg-primary/20 text-primary border-primary/30'
              )}
            >
              {filter}
            </Button>
          ))}
        </div>
        <div className="flex-1" />
        <div className="relative w-64">
          <label htmlFor="cluster-search" className="sr-only">Search clusters</label>
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" aria-hidden="true" />
          <Input
            id="cluster-search"
            type="search"
            placeholder="Search clusters…"
            className="pl-10"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Clusters Grid */}
      {filteredClusters && filteredClusters.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredClusters.map((cluster) => (
            <ClusterCard key={cluster.id} cluster={cluster} />
          ))}
        </div>
      ) : (
        <EmptySearchState query={searchQuery || activeFilter} />
      )}
    </div>
  );
}
