# DBaaS Platform - Architecture Overview

## System Architecture

```
                                    ┌─────────────────┐
                                    │   Cloudflare    │
                                    │     Tunnel      │
                                    └────────┬────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                  INTERNET                       │
                    └────────────────────────┼────────────────────────┘
                                             │
┌────────────────────────────────────────────┼────────────────────────────────────────────┐
│                                   HOST SERVER                                            │
│                                                                                          │
│   ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│   │                           MANAGEMENT LAYER                                        │  │
│   │                                                                                   │  │
│   │   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐         │  │
│   │   │  Dashboard  │   │   DBaaS     │   │    Redis    │   │    MinIO    │         │  │
│   │   │   (Web)     │   │    API      │   │   (Queue)   │   │  (Backup)   │         │  │
│   │   │  :3002      │   │   :8080     │   │   :6379     │   │   :9000     │         │  │
│   │   └─────────────┘   └──────┬──────┘   └─────────────┘   └─────────────┘         │  │
│   │                            │                                                      │  │
│   │                            ├────────────────────────────────────────┐            │  │
│   │                            │                                        │            │  │
│   │                     Docker API                              Orchestrator API     │  │
│   │                            │                                        │            │  │
│   │   ┌────────────────────────┼────────────────────────────────────────┼──────────┐│  │
│   │   │                        │          ORCHESTRATION                 │          ││  │
│   │   │                        ▼                                        ▼          ││  │
│   │   │              ┌─────────────────┐                    ┌─────────────────┐    ││  │
│   │   │              │  Docker Engine  │                    │   Orchestrator  │    ││  │
│   │   │              └─────────────────┘                    │     :3000       │    ││  │
│   │   │                                                     └─────────────────┘    ││  │
│   │   └─────────────────────────────────────────────────────────────────────────────┘│  │
│   └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                          │
│   ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│   │                           CLUSTER LAYER (Per Cluster)                             │  │
│   │                                                                                   │  │
│   │   ┌─────────────────────────────────────────────────────────────────────────┐   │  │
│   │   │                         Cluster Network                                  │   │  │
│   │   │                                                                          │   │  │
│   │   │   ┌───────────────────────────────────────────────────────────────┐     │   │  │
│   │   │   │                        ProxySQL                                │     │   │  │
│   │   │   │              Read/Write Splitting & Load Balancing            │     │   │  │
│   │   │   │                    :6033 (Write) :6034 (Read)                 │     │   │  │
│   │   │   └───────────────────────────┬───────────────────────────────────┘     │   │  │
│   │   │                               │                                          │   │  │
│   │   │              ┌────────────────┼────────────────┐                        │   │  │
│   │   │              │                │                │                        │   │  │
│   │   │              ▼                ▼                ▼                        │   │  │
│   │   │        ┌──────────┐     ┌──────────┐     ┌──────────┐                  │   │  │
│   │   │        │  Master  │────►│ Replica1 │     │ Replica2 │                  │   │  │
│   │   │        │  (R/W)   │     │  (Read)  │     │  (Read)  │                  │   │  │
│   │   │        └──────────┘     └──────────┘     └──────────┘                  │   │  │
│   │   │                                                                          │   │  │
│   │   └─────────────────────────────────────────────────────────────────────────┘   │  │
│   └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                          │
│   ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│   │                           MONITORING LAYER                                        │  │
│   │                                                                                   │  │
│   │   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                           │  │
│   │   │ Prometheus  │──►│   Grafana   │   │     n8n     │                           │  │
│   │   │   :9090     │   │    :3001    │   │   :5678     │                           │  │
│   │   └─────────────┘   └─────────────┘   └─────────────┘                           │  │
│   │                                                                                   │  │
│   └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Query Routing (Read/Write Splitting)

```
Application
    │
    ▼
ProxySQL (:6033/:6034)
    │
    ├─── SELECT queries ──────────► Replica Pool (Round-Robin)
    │                                   ├── Replica 1
    │                                   └── Replica 2
    │
    └─── INSERT/UPDATE/DELETE ───► Master
```

### 2. Replication Flow

```
Master
    │
    └── Binary Log
           │
           ├──────────────────────────────────────┐
           │                                       │
           ▼                                       ▼
    Replica 1 (Relay Log)                   Replica 2 (Relay Log)
           │                                       │
           ▼                                       ▼
    Apply to Data                           Apply to Data
```

### 3. Failover Flow

```
1. Master Dies
       │
       ▼
2. Orchestrator Detects (Health Check Failed)
       │
       ▼
3. Orchestrator Promotes Best Replica
       │
       ▼
4. Orchestrator Reconfigures Other Replicas
       │
       ▼
5. Webhook to DBaaS API
       │
       ▼
6. API Updates ProxySQL Config
       │
       ▼
7. n8n Sends Telegram Notification
```

## Component Responsibilities

| Component        | Responsibility                                     |
| ---------------- | -------------------------------------------------- |
| **DBaaS API**    | Cluster lifecycle management, Docker orchestration |
| **Docker**       | Container runtime, network isolation               |
| **ProxySQL**     | Query routing, connection pooling, health checks   |
| **Orchestrator** | Topology discovery, automatic failover             |
| **Redis**        | Task queue, distributed locking                    |
| **Prometheus**   | Metrics collection                                 |
| **Grafana**      | Visualization, alerting                            |
| **MinIO**        | Backup storage                                     |
| **n8n**          | Workflow automation, notifications                 |

## Network Isolation

Each cluster runs in its own isolated Docker network:

```
┌─────────────────────────────────┐
│     cluster-abc123-network      │
│         (172.28.1.0/24)         │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │ M   │ │ R1  │ │ R2  │       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────────────────────┐       │
│  │      ProxySQL       │       │
│  └─────────────────────┘       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│     cluster-def456-network      │
│         (172.28.2.0/24)         │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │ M   │ │ R1  │ │ R2  │       │
│  └─────┘ └─────┘ └─────┘       │
│  ┌─────────────────────┐       │
│  │      ProxySQL       │       │
│  └─────────────────────┘       │
└─────────────────────────────────┘
```

Both clusters connect to `dbaas-management` network for API access.
