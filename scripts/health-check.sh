#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# health-check.sh - Check cluster health
# ═══════════════════════════════════════════════════════════════════

CLUSTER_ID=$1

if [ -z "$CLUSTER_ID" ]; then
    echo "Usage: $0 <cluster_id>"
    exit 1
fi

echo "═══════════════════════════════════════════════════════════════════"
echo "Health Check for Cluster: $CLUSTER_ID"
echo "═══════════════════════════════════════════════════════════════════"

PASSWORD="root_${CLUSTER_ID}_pwd"
MASTER_NAME="mysql-${CLUSTER_ID}-master"

# Check Master
echo ""
echo "📊 Master Status:"
echo "─────────────────────────────────────────────────────────────────────"
if docker ps --format '{{.Names}}' | grep -q "^${MASTER_NAME}$"; then
    echo "✅ Master container is running"
    docker exec $MASTER_NAME mysql -uroot -p$PASSWORD -e "SHOW MASTER STATUS\G" 2>/dev/null | grep -E "File|Position|Gtid"
else
    echo "❌ Master container is NOT running"
fi

# Check Replicas
echo ""
echo "📊 Replica Status:"
echo "─────────────────────────────────────────────────────────────────────"

for container in $(docker ps --format '{{.Names}}' | grep "mysql-${CLUSTER_ID}-replica"); do
    echo ""
    echo "Replica: $container"
    
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        REPLICA_STATUS=$(docker exec $container mysql -uroot -p$PASSWORD -e "SHOW REPLICA STATUS\G" 2>/dev/null)
        
        IO_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_IO_Running" | awk '{print $2}')
        SQL_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_SQL_Running:" | awk '{print $2}')
        LAG=$(echo "$REPLICA_STATUS" | grep "Seconds_Behind_Source" | awk '{print $2}')
        
        if [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "Yes" ]; then
            echo "  ✅ Replication running"
            echo "  📈 Lag: ${LAG}s"
        else
            echo "  ❌ Replication NOT running"
            echo "  IO: $IO_RUNNING, SQL: $SQL_RUNNING"
        fi
    else
        echo "  ❌ Container not running"
    fi
done

# Check ProxySQL
echo ""
echo "📊 ProxySQL Status:"
echo "─────────────────────────────────────────────────────────────────────"
PROXYSQL_NAME="proxysql-${CLUSTER_ID}"
if docker ps --format '{{.Names}}' | grep -q "^${PROXYSQL_NAME}$"; then
    echo "✅ ProxySQL container is running"
else
    echo "⚠️ ProxySQL container not found"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "Health check complete!"
echo "═══════════════════════════════════════════════════════════════════"
