# RVNKQuests Database Migration Deployment Guide

## Overview

This guide covers the deployment of the V2 migration (quest_leaderboard_cache table) for the RVNKQuests Journal System.

## Pre-Deployment Checklist

### 1. Environment Verification

- [ ] Verify database platform (MySQL 8+ or SQLite 3+)
- [ ] Check database connection credentials
- [ ] Confirm database backup exists
- [ ] Verify plugin is stopped (if production)
- [ ] Review current schema version

### 2. Backup Procedure

**MySQL:**
```bash
# Full database backup
mysqldump -u username -p database_name > rvnkquests_backup_$(date +%Y%m%d_%H%M%S).sql

# Table-specific backup (if preferred)
mysqldump -u username -p database_name quest_progress quest_objective_progress quest_rewards_claimed quest_journal_entries quest_categories quest_tags quest_tag_assignments quest_leaderboard_entries > rvnkquests_tables_backup_$(date +%Y%m%d_%H%M%S).sql
```

**SQLite:**
```bash
# Full database backup
cp /path/to/database.db /path/to/backups/database_backup_$(date +%Y%m%d_%H%M%S).db

# Or use SQLite backup command
sqlite3 /path/to/database.db ".backup /path/to/backups/database_backup_$(date +%Y%m%d_%H%M%S).db"
```

### 3. Migration File Verification

```bash
# Verify migration files exist
ls -lh src/main/resources/db/V2__add_leaderboard_cache.sql
ls -lh src/main/resources/db/sqlite/V2__add_leaderboard_cache.sql

# Check file integrity (optional)
sha256sum src/main/resources/db/V2__add_leaderboard_cache.sql
sha256sum src/main/resources/db/sqlite/V2__add_leaderboard_cache.sql
```

## Deployment Procedure

### Option 1: Manual Deployment (Recommended for Production)

#### MySQL Deployment

**Step 1: Connect to Database**
```bash
mysql -u username -p database_name
```

**Step 2: Verify Current State**
```sql
-- Check if migration already applied
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'quest_leaderboard_cache';

-- Expected: Empty result (0 rows) if migration not yet applied
```

**Step 3: Execute Migration**
```bash
# Execute from shell
mysql -u username -p database_name < src/main/resources/db/V2__add_leaderboard_cache.sql

# OR from MySQL prompt
source src/main/resources/db/V2__add_leaderboard_cache.sql;
```

**Step 4: Verify Migration**
```sql
-- Verify table exists
SHOW TABLES LIKE 'quest_leaderboard_cache';

-- Verify structure
DESCRIBE quest_leaderboard_cache;

-- Verify indexes
SHOW INDEX FROM quest_leaderboard_cache;
```

**Expected Output:**
```
+------------------------+------------+------+-----+-------------------+-------------------+
| Field                  | Type       | Null | Key | Default           | Extra             |
+------------------------+------------+------+-----+-------------------+-------------------+
| id                     | int        | NO   | PRI | NULL              | auto_increment    |
| leaderboard_type       | varchar(32)| NO   | UNI | NULL              |                   |
| cache_data             | json       | NO   |     | NULL              |                   |
| generated_at           | timestamp  | NO   |     | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| expires_at             | timestamp  | NO   | MUL | NULL              |                   |
+------------------------+------------+------+-----+-------------------+-------------------+
```

#### SQLite Deployment

**Step 1: Connect to Database**
```bash
sqlite3 /path/to/database.db
```

**Step 2: Verify Current State**
```sql
-- Check if migration already applied
SELECT name FROM sqlite_master WHERE type='table' AND name='quest_leaderboard_cache';

-- Expected: Empty result (0 rows) if migration not yet applied
```

**Step 3: Execute Migration**
```bash
# Execute from shell
sqlite3 /path/to/database.db < src/main/resources/db/sqlite/V2__add_leaderboard_cache.sql

# OR from SQLite prompt
.read src/main/resources/db/sqlite/V2__add_leaderboard_cache.sql
```

**Step 4: Verify Migration**
```sql
-- Verify table exists
.tables quest_leaderboard_cache

-- Verify structure
PRAGMA table_info(quest_leaderboard_cache);

-- Verify indexes
SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='quest_leaderboard_cache';
```

**Expected Output:**
```
cid  name              type     notnull  dflt_value  pk
---  ----------------  -------  -------  ----------  --
0    id                INTEGER  0        NULL        1
1    leaderboard_type  TEXT     1        NULL        0
2    cache_data        TEXT     1        NULL        0
3    generated_at      TEXT     1        CURRENT_TIMESTAMP  0
4    expires_at        TEXT     1        NULL        0
```

### Option 2: Automated Deployment (via DatabaseManager)

**Step 1: Update DatabaseManager.java**

Add migration execution logic to `DatabaseManager.java`:

```java
public class DatabaseManager {
    private static final String V2_MIGRATION_TABLE = "quest_leaderboard_cache";

    public void initialize() {
        try {
            // 1. Initialize base schema
            initializeBaseSchema();

            // 2. Run pending migrations
            runMigrations();

            // 3. Validate schema
            validateSchema();
        } catch (SQLException e) {
            logger.severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void runMigrations() throws SQLException {
        logger.info("Checking for pending migrations...");

        // Check if V2 migration is needed
        if (!tableExists(V2_MIGRATION_TABLE)) {
            logger.info("Running migration V2: Add leaderboard cache table");
            executeMigration(isMySQL()
                ? "db/V2__add_leaderboard_cache.sql"
                : "db/sqlite/V2__add_leaderboard_cache.sql");
            logger.info("Migration V2 completed successfully");
        } else {
            logger.info("Migration V2 already applied - skipping");
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        }
    }

    private void executeMigration(String migrationFile) throws SQLException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(migrationFile)) {
            if (is == null) {
                throw new SQLException("Migration file not found: " + migrationFile);
            }

            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read migration file: " + migrationFile, e);
        }
    }
}
```

**Step 2: Plugin Initialization**

The migration will run automatically when the plugin starts:

```java
@Override
public void onEnable() {
    // Database initialization (includes migrations)
    databaseManager = new DatabaseManager(this);
    databaseManager.initialize();

    // ... rest of plugin initialization
}
```

## Post-Deployment Verification

### 1. Run Validation Script

**MySQL:**
```bash
mysql -u username -p database_name < src/main/resources/db/validation.sql
```

**SQLite:**
```bash
sqlite3 /path/to/database.db < src/main/resources/db/sqlite/validation.sql
```

**Expected Results:**
- All V1 base tables show ✅ EXISTS
- V2 migration shows ✅ V2 APPLIED
- All indexes present
- No errors or warnings

### 2. Run Test Script

**MySQL:**
```bash
mysql -u username -p database_name < src/main/resources/db/test_migration.sql
```

**SQLite:**
```bash
sqlite3 /path/to/database.db < src/main/resources/db/sqlite/test_migration.sql
```

**Expected Results:**
- All 11 tests pass
- No SQL errors
- Test data cleanup successful

### 3. Plugin Startup Verification

```bash
# Start plugin
# Check server console for errors

# Expected log output:
[RVNKQuests] Checking for pending migrations...
[RVNKQuests] Migration V2 already applied - skipping
[RVNKQuests] Database initialized successfully
```

### 4. Functional Testing

**Test Leaderboard Cache Operations:**

```sql
-- Insert test cache entry
REPLACE INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES ('most_completed', '{"entries": []}', NOW() + INTERVAL 5 MINUTE);  -- MySQL
-- OR
VALUES ('most_completed', '{"entries": []}', datetime('now', '+5 minutes'));  -- SQLite

-- Verify cache retrieval
SELECT * FROM quest_leaderboard_cache WHERE leaderboard_type = 'most_completed';

-- Clean up test data
DELETE FROM quest_leaderboard_cache WHERE leaderboard_type = 'most_completed';
```

## Rollback Procedure

### When to Rollback

Rollback V2 migration if:
- Migration fails with errors
- Plugin fails to start after migration
- Data corruption detected
- Performance issues observed

### Rollback Steps

**Step 1: Stop Plugin**
```bash
# Stop Minecraft server or disable plugin
```

**Step 2: Execute Rollback SQL**

**MySQL:**
```sql
DROP TABLE IF EXISTS quest_leaderboard_cache;
```

**SQLite:**
```sql
DROP TABLE IF EXISTS quest_leaderboard_cache;
```

**Step 3: Restore from Backup (if needed)**

**MySQL:**
```bash
mysql -u username -p database_name < rvnkquests_backup_YYYYMMDD_HHMMSS.sql
```

**SQLite:**
```bash
cp /path/to/backups/database_backup_YYYYMMDD_HHMMSS.db /path/to/database.db
```

**Step 4: Verify Rollback**
```sql
-- MySQL
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quest_leaderboard_cache';

-- SQLite
SELECT name FROM sqlite_master WHERE type='table' AND name='quest_leaderboard_cache';

-- Expected: 0 rows (table should not exist)
```

**Step 5: Restart Plugin**
```bash
# Restart Minecraft server or re-enable plugin
```

## Troubleshooting

### Common Issues

#### Issue 1: Migration File Not Found

**Error:**
```
Migration file not found: db/V2__add_leaderboard_cache.sql
```

**Solution:**
```bash
# Verify file exists in plugin JAR
jar -tf RVNKQuests-1.0-SNAPSHOT.jar | grep V2__add_leaderboard_cache.sql

# If missing, rebuild plugin
mvn clean package
```

#### Issue 2: Table Already Exists

**Error:**
```sql
ERROR 1050 (42S01): Table 'quest_leaderboard_cache' already exists
```

**Solution:**
```sql
-- Migration was already applied - safe to skip
-- Verify table structure matches expected schema
DESCRIBE quest_leaderboard_cache;
```

#### Issue 3: Permission Denied

**Error:**
```
ERROR 1142 (42000): CREATE command denied to user 'username'@'localhost'
```

**Solution:**
```sql
-- Grant CREATE permission to database user
GRANT CREATE ON database_name.* TO 'username'@'localhost';
FLUSH PRIVILEGES;
```

#### Issue 4: JSON Type Not Supported (MySQL < 5.7)

**Error:**
```
ERROR 1064 (42000): You have an error in your SQL syntax near 'JSON'
```

**Solution:**
```sql
-- Upgrade MySQL to 8.0+ OR use TEXT column instead of JSON
-- Modify migration file to use TEXT for older MySQL versions
ALTER TABLE quest_leaderboard_cache MODIFY cache_data TEXT NOT NULL;
```

#### Issue 5: Foreign Key Constraint Fails (SQLite)

**Error:**
```
FOREIGN KEY constraint failed
```

**Solution:**
```sql
-- Enable foreign keys in SQLite
PRAGMA foreign_keys = ON;

-- Re-run migration
```

## Performance Monitoring

### Metrics to Track

**Post-Deployment:**
- Leaderboard query response time (target: <100ms)
- Cache hit rate (target: >80%)
- TPS impact (target: no degradation, maintain ≥19.5)
- Database connection pool utilization

**Monitoring Queries:**

```sql
-- MySQL: Check cache entry count
SELECT COUNT(*) AS total_cache_entries FROM quest_leaderboard_cache;

-- MySQL: Check cache freshness
SELECT
    leaderboard_type,
    TIMESTAMPDIFF(SECOND, NOW(), expires_at) AS seconds_until_expiry
FROM quest_leaderboard_cache
ORDER BY expires_at;

-- SQLite: Check cache entry count
SELECT COUNT(*) AS total_cache_entries FROM quest_leaderboard_cache;

-- SQLite: Check cache freshness
SELECT
    leaderboard_type,
    CAST((julianday(expires_at) - julianday('now')) * 86400 AS INTEGER) AS seconds_until_expiry
FROM quest_leaderboard_cache
ORDER BY expires_at;
```

## Scheduled Maintenance

### Cache Cleanup (Recommended)

**Setup cron job to clean expired cache entries:**

**MySQL:**
```sql
-- Run every 5 minutes
DELETE FROM quest_leaderboard_cache WHERE expires_at < NOW();
```

**SQLite:**
```sql
-- Run every 5 minutes
DELETE FROM quest_leaderboard_cache WHERE expires_at < datetime('now');
```

**Cron Configuration:**
```bash
# Add to crontab (Linux)
*/5 * * * * mysql -u username -ppassword database_name -e "DELETE FROM quest_leaderboard_cache WHERE expires_at < NOW();"

# OR for SQLite
*/5 * * * * sqlite3 /path/to/database.db "DELETE FROM quest_leaderboard_cache WHERE expires_at < datetime('now');"
```

## Contact & Support

### Issue Reporting

If you encounter issues during migration:

1. Check this guide's Troubleshooting section
2. Review validation.sql and test_migration.sql output
3. Check server logs for SQL errors
4. Create GitHub issue with:
   - Database platform and version
   - Migration error message
   - Relevant log excerpts
   - Steps to reproduce

### Resources

- **Migration README**: `src/main/resources/db/README.md`
- **Migration Summary**: `src/main/resources/db/MIGRATION_SUMMARY.md`
- **PRP Document**: `PRPs/quest-features-sprint.md`
- **Validation Scripts**: `db/validation.sql` and `db/sqlite/validation.sql`
- **Test Scripts**: `db/test_migration.sql` and `db/sqlite/test_migration.sql`

---

**Document Version**: 1.0
**Last Updated**: 2026-02-01
**Migration Version**: V2 (Leaderboard Cache)
**Status**: Production Ready
