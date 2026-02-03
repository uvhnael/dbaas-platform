#!/bin/bash

# Test MySQL connection via ProxySQL
# Connection details from cluster

HOST="proxysql-5df5109a"
PORT="16140"
USER="app_user"
PASSWORD="VRyh7SzxnS9DYmuqgv_2yw"

echo "=========================================="
echo "Testing MySQL Connection via ProxySQL"
echo "=========================================="
echo "Host: $HOST"
echo "Port: $PORT"
echo "User: $USER"
echo ""

# Test 1: Basic connection test
echo "Test 1: Basic Connection Test"
echo "----------------------------------------"
docker exec mysql-5df5109a-master mysql \
  -h "$HOST" \
  -P 6033 \
  -u "$USER" \
  -p"$PASSWORD" \
  -e "SELECT 'Connection successful!' as status, VERSION() as mysql_version, @@hostname as connected_to;" \
  2>&1

if [ $? -eq 0 ]; then
    echo "✓ Connection successful!"
else
    echo "✗ Connection failed!"
fi
echo ""

# Test 2: Test read/write splitting
echo "Test 2: Show Databases"
echo "----------------------------------------"
docker exec mysql-5df5109a-master mysql \
  -h "$HOST" \
  -P 6033 \
  -u "$USER" \
  -p"$PASSWORD" \
  -e "SHOW DATABASES;" \
  2>&1
echo ""

# Test 3: Create and query test table
echo "Test 3: Create Table and Insert Data"
echo "----------------------------------------"
docker exec mysql-5df5109a-master mysql \
  -h "$HOST" \
  -P 6033 \
  -u "$USER" \
  -p"$PASSWORD" \
  -e "USE appdb; DROP TABLE IF EXISTS test_connection; CREATE TABLE test_connection (id INT PRIMARY KEY, message VARCHAR(100)); INSERT INTO test_connection VALUES (1, 'Connection test passed!'); SELECT * FROM test_connection;" \
  2>&1
echo ""

# Test 4: ProxySQL stats
echo "Test 4: ProxySQL Connection Stats"
echo "----------------------------------------"
docker exec proxysql-5df5109a mysql \
  -h 127.0.0.1 \
  -P 6032 \
  -u radmin \
  -pradmin \
  -e "SELECT * FROM stats_mysql_connection_pool;" \
  2>&1
echo ""

echo "=========================================="
echo "Connection tests completed!"
echo "=========================================="
