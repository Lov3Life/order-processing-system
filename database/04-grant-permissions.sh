#!/bin/bash
set -e

mysql -u root -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
    GRANT ALL PRIVILEGES ON \`order-processing-system-orders_db\`.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON \`order-processing-system-inventory_db\`.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON \`order-processing-system-notifications_db\`.* TO '${MYSQL_USER}'@'%';
EOSQL