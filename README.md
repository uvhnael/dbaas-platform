# 🚀 DBaaS Platform

**Database as a Service** - Nền tảng quản lý MySQL Cluster với High Availability

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-24.0-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📋 Tổng Quan

DBaaS Platform cho phép doanh nghiệp nhỏ triển khai và quản lý cụm cơ sở dữ liệu MySQL với:

- **High Availability (HA)**: Master-Replica với automatic failover
- **Load Balancing**: ProxySQL tự động phân tách Read/Write
- **One-click Deployment**: Triển khai cluster chỉ với một API call
- **Auto-healing**: Tự động phục hồi khi có sự cố
- **Monitoring**: Tích hợp Prometheus + Grafana

---

## 🏗️ Kiến Trúc

```
┌─────────────────────────────────────────────────────────────────┐
│                        DBaaS Platform                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│   │  Dashboard  │◄──►│ Spring Boot │◄──►│   Docker    │         │
│   │    (Web)    │    │     API     │    │   Engine    │         │
│   └─────────────┘    └──────┬──────┘    └─────────────┘         │
│                             │                                    │
│              ┌──────────────┼──────────────┐                    │
│              ▼              ▼              ▼                    │
│        ┌──────────┐   ┌──────────┐   ┌──────────┐              │
│        │ ProxySQL │   │Orchestrator│  │   n8n    │              │
│        └──────────┘   └──────────┘   └──────────┘              │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    MySQL Cluster                         │   │
│   │   ┌────────┐    ┌────────┐    ┌────────┐               │   │
│   │   │ Master │───►│Replica1│    │Replica2│               │   │
│   │   └────────┘    └────────┘    └────────┘               │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

| Component     | Technology               |
| ------------- | ------------------------ |
| Backend       | Java 17, Spring Boot 3.2 |
| Database      | MySQL 8.0, H2 (metadata) |
| Load Balancer | ProxySQL 2.5             |
| Failover      | Orchestrator             |
| Container     | Docker, Docker Compose   |
| Queue         | Redis                    |
| Monitoring    | Prometheus, Grafana      |
| Backup        | MinIO (S3-compatible)    |
| Automation    | n8n                      |

---

## 🚀 Quick Start

### Prerequisites

- Docker Desktop 24.0+
- Java 17+ (for development)
- Maven 3.8+ (for development)

### 1. Clone & Setup

```bash
git clone https://github.com/your-username/dbaas-platform.git
cd dbaas-platform

# Copy environment file
cp .env.example .env
```

### 2. Start Infrastructure

```bash
# Start all services
docker-compose up -d

# Check status
docker-compose ps
```

### 3. Access Services

| Service       | URL                                       | Credentials           |
| ------------- | ----------------------------------------- | --------------------- |
| API           | http://localhost:8080/api                 | -                     |
| Swagger UI    | http://localhost:8080/api/swagger-ui.html | -                     |
| Orchestrator  | http://localhost:3000                     | -                     |
| Grafana       | http://localhost:3001                     | admin/admin           |
| n8n           | http://localhost:5678                     | admin/admin           |
| MinIO Console | http://localhost:9001                     | minioadmin/minioadmin |

---

## 📡 API Usage

### Create a Cluster

```bash
curl -X POST http://localhost:8080/api/clusters \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -d '{
    "name": "production-db",
    "mysqlVersion": "8.0",
    "replicaCount": 2
  }'
```

### List Clusters

```bash
curl http://localhost:8080/api/clusters \
  -H "X-User-Id: user1"
```

### Scale Cluster

```bash
curl -X POST http://localhost:8080/api/clusters/{id}/scale \
  -H "Content-Type: application/json" \
  -d '{"replicaCount": 3}'
```

### Delete Cluster

```bash
curl -X DELETE http://localhost:8080/api/clusters/{id}
```

---

## 📁 Project Structure

```
dbaas-platform/
├── backend/                 # Spring Boot API
│   ├── src/main/java/com/dbaas/
│   │   ├── config/         # Configuration
│   │   ├── controller/     # REST Controllers
│   │   ├── service/        # Business Logic
│   │   ├── model/          # Entities & DTOs
│   │   ├── repository/     # Data Access
│   │   └── exception/      # Error Handling
│   └── Dockerfile
├── config/                  # Infrastructure configs
│   ├── master.cnf
│   ├── replica.cnf
│   ├── proxysql.cnf
│   ├── orchestrator.conf.json
│   └── prometheus.yml
├── scripts/                 # Utility scripts
├── docs/                    # Documentation
├── docker-compose.yml
└── .env.example
```

---

## 🔧 Development

### Build Backend

```bash
cd backend
mvn clean package -DskipTests
```

### Run Locally

```bash
mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

---

## 📊 Monitoring

### Grafana Dashboards

1. Access Grafana at http://localhost:3001
2. Add Prometheus datasource: `http://prometheus:9090`
3. Import MySQL dashboard (ID: 7362)

### Alert Configuration

Configure alerts in n8n:

1. Access n8n at http://localhost:5678
2. Create workflow for Telegram notifications
3. Connect Prometheus alerts via webhook

---

## 🔒 Security

- All clusters use isolated Docker networks
- ProxySQL handles connection authentication
- API supports JWT authentication (optional)
- Cloudflare Tunnel for secure remote access

---

## 📝 License

MIT License - see [LICENSE](LICENSE) for details.

---

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing`)
5. Open Pull Request

---

**Made with ❤️ for small businesses**
