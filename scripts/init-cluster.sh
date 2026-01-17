#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# init-cluster.sh - Initialize MySQL Cluster
# ═══════════════════════════════════════════════════════════════════

set -e

CLUSTER_ID=$1
MYSQL_VERSION=${2:-8.0}
REPLICA_COUNT=${3:-2}

if [ -z "$CLUSTER_ID" ]; then
    echo "Usage: $0 <cluster_id> [mysql_version] [replica_count]"
    exit 1
fi

echo "═══════════════════════════════════════════════════════════════════"
echo "Initializing MySQL Cluster: $CLUSTER_ID"
echo "MySQL Version: $MYSQL_VERSION"
echo "Replica Count: $REPLICA_COUNT"
echo "═══════════════════════════════════════════════════════════════════"

NETWORK_NAME="cluster-${CLUSTER_ID}-network"
MASTER_NAME="mysql-${CLUSTER_ID}-master"

# Create network
echo "[1/5] Creating network: $NETWORK_NAME"
docker network create $NETWORK_NAME 2>/dev/null || echo "Network already exists"

# Start Master
echo "[2/5] Starting MySQL Master: $MASTER_NAME"
docker run -d \
    --name $MASTER_NAME \
    --hostname $MASTER_NAME \
    --network $NETWORK_NAME \
    -e MYSQL_ROOT_PASSWORD=root_${CLUSTER_ID}_pwd \
    -e MYSQL_DATABASE=appdb \
    mysql:$MYSQL_VERSION \
    --server-id=1 \
    --log-bin=mysql-bin \
    --binlog-format=ROW \
    --gtid-mode=ON \
    --enforce-gtid-consistency=ON \
    --log-slave-updates=ON

# Wait for master to be ready
echo "[3/5] Waiting for Master to be ready..."
sleep 30

# Create replication user
echo "[4/5] Creating replication user..."
docker exec $MASTER_NAME mysql -uroot -proot_${CLUSTER_ID}_pwd -e "
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
"

# Start Replicas
echo "[5/5] Starting Replicas..."
for i in $(seq 1 $REPLICA_COUNT); do
    REPLICA_NAME="mysql-${CLUSTER_ID}-replica-${i}"
    echo "  Starting: $REPLICA_NAME"
    
    docker run -d \
        --name $REPLICA_NAME \
        --hostname $REPLICA_NAME \
        --network $NETWORK_NAME \
        -e MYSQL_ROOT_PASSWORD=root_${CLUSTER_ID}_pwd \
        mysql:$MYSQL_VERSION \
        --server-id=$((i + 1)) \
        --relay-log=mysql-relay-bin \
        --gtid-mode=ON \
        --enforce-gtid-consistency=ON \
        --log-slave-updates=ON \
        --read-only=ON
done

# Wait for replicas
sleep 20

# Configure replication on each replica
for i in $(seq 1 $REPLICA_COUNT); do
    REPLICA_NAME="mysql-${CLUSTER_ID}-replica-${i}"
    echo "  Configuring replication on: $REPLICA_NAME"
    
    docker exec $REPLICA_NAME mysql -uroot -proot_${CLUSTER_ID}_pwd -e "
    CHANGE REPLICATION SOURCE TO
        SOURCE_HOST='$MASTER_NAME',
        SOURCE_USER='repl',
        SOURCE_PASSWORD='repl_password',
        SOURCE_AUTO_POSITION=1;
    START REPLICA;
    "
done

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "✅ Cluster $CLUSTER_ID initialized successfully!"
echo "═══════════════════════════════════════════════════════════════════"
echo "Master: $MASTER_NAME"
echo "Replicas: $REPLICA_COUNT"
echo ""
echo "To check replication status:"
echo "  docker exec $MASTER_NAME mysql -uroot -proot_${CLUSTER_ID}_pwd -e 'SHOW MASTER STATUS\\G'"
echo "  docker exec mysql-${CLUSTER_ID}-replica-1 mysql -uroot -proot_${CLUSTER_ID}_pwd -e 'SHOW REPLICA STATUS\\G'"
