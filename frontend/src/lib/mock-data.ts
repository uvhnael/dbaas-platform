import { Cluster, ClusterMetrics, ClusterNode, ClusterStatus } from '@/types';

// Enhanced mock clusters with topology
export const mockClusters: Cluster[] = [
  {
    id: 'abc12345',
    name: 'production-db',
    userId: 'user1',
    mysqlVersion: '8.0',
    replicaCount: 2,
    status: 'RUNNING',
    networkId: 'net-prod',
    masterContainerId: 'master-abc',
    proxySqlContainerId: 'proxy-abc',
    replicaContainerIds: ['replica-1', 'replica-2'],
    createdAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
  },
  {
    id: 'def67890',
    name: 'staging-db',
    userId: 'user1',
    mysqlVersion: '8.0',
    replicaCount: 1,
    status: 'RUNNING',
    networkId: 'net-staging',
    masterContainerId: 'master-def',
    proxySqlContainerId: 'proxy-def',
    replicaContainerIds: ['replica-3'],
    createdAt: new Date().toISOString(),
  },
  {
    id: 'ghi11111',
    name: 'dev-db',
    userId: 'user1',
    mysqlVersion: '8.0',
    replicaCount: 1,
    status: 'DEGRADED',
    networkId: 'net-dev',
    masterContainerId: 'master-ghi',
    proxySqlContainerId: 'proxy-ghi',
    replicaContainerIds: ['replica-4'],
    createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
  },
  {
    id: 'jkl22222',
    name: 'analytics-db',
    userId: 'user1',
    mysqlVersion: '8.0',
    replicaCount: 2,
    status: 'CREATING',
    networkId: 'net-analytics',
    masterContainerId: '',
    proxySqlContainerId: '',
    replicaContainerIds: [],
    createdAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
  },
];

// Enhanced nodes with more details
export const mockNodes: Record<string, ClusterNode[]> = {
  'abc12345': [
    { 
      id: 'proxy-abc12345', 
      hostname: 'proxysql-abc12345', 
      role: 'proxysql', 
      status: 'online',
      cpuPercent: 15,
      memoryPercent: 20,
      uptime: '15d 4h 32m',
      version: 'ProxySQL 2.5.5',
      connections: 156,
    },
    { 
      id: 'master-abc12345', 
      hostname: 'abc12345-master', 
      role: 'master', 
      status: 'online',
      cpuPercent: 45,
      memoryPercent: 62,
      replicationLag: 0,
      uptime: '15d 4h 30m',
      version: 'MySQL 8.0.35',
      gtidExecuted: 'abc12345:1-9876543',
    },
    { 
      id: 'replica-1-abc12345', 
      hostname: 'replica-1', 
      role: 'replica', 
      status: 'online',
      cpuPercent: 30,
      memoryPercent: 45,
      replicationLag: 0,
      uptime: '15d 4h 28m',
      version: 'MySQL 8.0.35',
    },
    { 
      id: 'replica-2-abc12345', 
      hostname: 'replica-2', 
      role: 'replica', 
      status: 'syncing',
      cpuPercent: 65,
      memoryPercent: 58,
      replicationLag: 3,
      uptime: '2h 15m',
      version: 'MySQL 8.0.35',
    },
  ],
  'def67890': [
    { id: 'proxy-def67890', hostname: 'proxysql-def67890', role: 'proxysql', status: 'online', cpuPercent: 10, memoryPercent: 15 },
    { id: 'master-def67890', hostname: 'def67890-master', role: 'master', status: 'online', cpuPercent: 25, memoryPercent: 40, replicationLag: 0 },
    { id: 'replica-3-def67890', hostname: 'replica-3', role: 'replica', status: 'online', cpuPercent: 20, memoryPercent: 35, replicationLag: 0 },
  ],
  'ghi11111': [
    { id: 'proxy-ghi11111', hostname: 'proxysql-ghi11111', role: 'proxysql', status: 'online', cpuPercent: 5, memoryPercent: 10 },
    { id: 'master-ghi11111', hostname: 'ghi11111-master', role: 'master', status: 'online', cpuPercent: 35, memoryPercent: 50, replicationLag: 0 },
    { id: 'replica-4-ghi11111', hostname: 'replica-4', role: 'replica', status: 'offline', cpuPercent: 0, memoryPercent: 0, replicationLag: 999 },
  ],
};

// Enhanced metrics
export const mockMetrics: Record<string, ClusterMetrics> = {
  'abc12345': {
    queriesPerSecond: 1250,
    connections: 45,
    maxConnections: 500,
    replicationLag: 0,
    cpuUsage: 45,
    memoryUsage: 62,
    diskUsage: 35,
    networkIn: 152,
    networkOut: 89,
  },
  'def67890': {
    queriesPerSecond: 580,
    connections: 22,
    maxConnections: 300,
    replicationLag: 0,
    cpuUsage: 25,
    memoryUsage: 40,
    diskUsage: 20,
    networkIn: 45,
    networkOut: 32,
  },
  'ghi11111': {
    queriesPerSecond: 120,
    connections: 8,
    maxConnections: 100,
    replicationLag: 999,
    cpuUsage: 35,
    memoryUsage: 50,
    diskUsage: 15,
    networkIn: 12,
    networkOut: 8,
  },
  'jkl22222': {
    queriesPerSecond: 500,
    connections: 15,
    maxConnections: 200,
    replicationLag: 0,
    cpuUsage: 20,
    memoryUsage: 30,
    diskUsage: 10,
    networkIn: 25,
    networkOut: 18,
  },
};

// Mock logs for live console
export const mockLogs: string[] = [
  '[2026-01-13 14:30:01] [INFO] MySQL server started successfully',
  '[2026-01-13 14:30:02] [INFO] Replication channel configured',
  '[2026-01-13 14:30:03] [INFO] Connected to master abc12345-master',
  '[2026-01-13 14:30:05] [INFO] Slave SQL thread started',
  '[2026-01-13 14:30:10] [INFO] Binary log position: mysql-bin.000042:12345',
  '[2026-01-13 14:31:00] [DEBUG] Query executed: SELECT * FROM users LIMIT 100',
  '[2026-01-13 14:31:15] [INFO] Checkpoint completed',
  '[2026-01-13 14:32:00] [WARN] Slow query detected (2.5s): SELECT COUNT(*) FROM orders',
  '[2026-01-13 14:32:30] [INFO] Connection pool: 45/500 active',
  '[2026-01-13 14:33:00] [INFO] Replication lag: 0 seconds',
];

// Topology edges for React Flow
export const mockEdges: Record<string, { from: string; to: string; label: string }[]> = {
  'abc12345': [
    { from: 'proxy-abc12345', to: 'master-abc12345', label: 'Read/Write' },
    { from: 'master-abc12345', to: 'replica-1-abc12345', label: 'Replication' },
    { from: 'master-abc12345', to: 'replica-2-abc12345', label: 'Replication' },
  ],
  'def67890': [
    { from: 'proxy-def67890', to: 'master-def67890', label: 'Read/Write' },
    { from: 'master-def67890', to: 'replica-3-def67890', label: 'Replication' },
  ],
  'ghi11111': [
    { from: 'proxy-ghi11111', to: 'master-ghi11111', label: 'Read/Write' },
    { from: 'master-ghi11111', to: 'replica-4-ghi11111', label: 'Replication' },
  ],
};

// Simulate API delay
export function simulateDelay(ms: number = 500): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
