// Cluster status enum (use generated type from API)
export type { ClusterStatus } from '@/lib/api/model';

// Node status
export type NodeStatus = 'online' | 'offline' | 'syncing';
export type NodeRole = 'master' | 'replica' | 'proxysql';

// Cluster entity
export interface Cluster {
  id: string;
  name: string;
  userId: string;
  mysqlVersion: string;
  replicaCount: number;
  status: ClusterStatus;
  networkId: string;
  masterContainerId: string;
  proxySqlContainerId: string;
  replicaContainerIds: string[];
  createdAt: string;
  updatedAt?: string;
}

// Cluster node with enhanced details
export interface ClusterNode {
  id: string;
  hostname: string;
  role: NodeRole;
  status: NodeStatus;
  replicationLag?: number;
  cpuPercent?: number;
  memoryPercent?: number;
  uptime?: string;
  version?: string;
  connections?: number;
  gtidExecuted?: string;
}

// Cluster metrics
export interface ClusterMetrics {
  queriesPerSecond: number;
  connections: number;
  maxConnections: number;
  replicationLag: number;
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  networkIn: number;
  networkOut: number;
}

// Create cluster request
export interface CreateClusterRequest {
  name: string;
  mysqlVersion: string;
  replicaCount: number;
  cpuCores?: number;
  memory?: string;
  storage?: string;
}

// API Response types
export interface ClusterDetailResponse {
  id: string;
  name: string;
  status: ClusterStatus;
  metrics: ClusterMetrics;
  topology: {
    nodes: ClusterNode[];
    edges: TopologyEdge[];
  };
}

export interface TopologyEdge {
  from: string;
  to: string;
  label: string;
}

// WebSocket event types
export interface FailoverEvent {
  type: 'FAILOVER';
  clusterId: string;
  oldMaster: string;
  newMaster: string;
  timestamp: string;
}

export interface AlertEvent {
  type: 'ALERT';
  severity: 'info' | 'warning' | 'error';
  message: string;
  clusterId?: string;
  timestamp: string;
}

export type WebSocketEvent = FailoverEvent | AlertEvent;
