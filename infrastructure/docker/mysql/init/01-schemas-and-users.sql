-- Trading Capture System — schema-per-service + user-per-service (ADR-0013).
-- Runs once on first MySQL container start (empty data volume).
-- Development credentials only; production would use a secret store.

CREATE DATABASE IF NOT EXISTS tcs_trading    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS tcs_account    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS tcs_enrichment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS tcs_allocation CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'trading_svc'@'%'    IDENTIFIED BY 'trading_dev';
CREATE USER IF NOT EXISTS 'account_svc'@'%'    IDENTIFIED BY 'account_dev';
CREATE USER IF NOT EXISTS 'enrichment_svc'@'%' IDENTIFIED BY 'enrichment_dev';
CREATE USER IF NOT EXISTS 'allocation_svc'@'%' IDENTIFIED BY 'allocation_dev';

-- Ownership boundaries are physical: each user can touch only its own schema.
GRANT ALL PRIVILEGES ON tcs_trading.*    TO 'trading_svc'@'%';
GRANT ALL PRIVILEGES ON tcs_account.*    TO 'account_svc'@'%';
GRANT ALL PRIVILEGES ON tcs_enrichment.* TO 'enrichment_svc'@'%';
GRANT ALL PRIVILEGES ON tcs_allocation.* TO 'allocation_svc'@'%';

FLUSH PRIVILEGES;
