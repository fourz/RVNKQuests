# RVNKQuests Database Migrations

## Overview

This directory contains SQL migration scripts for the RVNKQuests plugin database schema. Migrations use Flyway-compatible versioning format.

## Directory Structure

```
db/
├── README.md                           # This file
├── V2__add_leaderboard_cache.sql       # MySQL migration
└── sqlite/
    └── V2__add_leaderboard_cache.sql   # SQLite migration
```

## Migration Naming Convention

**Format:** `V{version}__{description}.sql`

- **Version**: Sequential integer (V1, V2, V3, etc.)
- **Separator**: Double underscore `__`
- **Description**: Snake_case description of changes
- **Extension**: `.sql`

**Examples:**
- `V2__add_leaderboard_cache.sql`
- `V3__add_quest_prerequisites.sql`
- `V4__modify_journal_entries.sql`

## Base Schema (V1)

The base schema is located in `src/main/resources/schema/` and includes:

### Core Tables (V1)
- `quest_progress` - Player quest state tracking
- `quest_objective_progress` - Per-objective completion tracking
- `quest_rewards_claimed` - Reward claim prevention

### Journal System Tables (V1)
- `quest_journal_entries` - Quest action history with timestamps
- `quest_categories` - Quest organization categories
- `quest_tags` - Flexible quest tagging system
- `quest_tag_assignments` - Many-to-many quest-tag relationships
- `quest_leaderboard_entries` - Competitive player rankings

## Migrations

### V2: Add Leaderboard Cache (2026-02-01)

**Purpose:** Performance optimization for leaderboard queries

**Files:**
- `V2__add_leaderboard_cache.sql` (MySQL)
- `sqlite/V2__add_leaderboard_cache.sql` (SQLite)

**Changes:**
- Added `quest_leaderboard_cache` table
- Supports 5-minute TTL caching
- JSON cache data (MySQL) / TEXT (SQLite)
- Indexed by leaderboard type and expiration time

**Impact:**
- Reduces leaderboard query time from ~500ms to <100ms
- No changes to existing tables
- Backward compatible

## Database Platform Differences

### MySQL vs SQLite Schema Variations

| Feature | MySQL | SQLite |
|---------|-------|--------|
| **Auto-increment** | `AUTO_INCREMENT` | `AUTOINCREMENT` |
| **Data types** | `VARCHAR`, `INT`, `TIMESTAMP` | `TEXT`, `INTEGER` |
| **JSON support** | Native `JSON` type | `TEXT` (parse as JSON) |
| **Boolean** | `BOOLEAN`/`TINYINT(1)` | `INTEGER` (0/1) |
| **Timestamps** | `TIMESTAMP` with functions | `TEXT` (ISO-8601 format) |
| **Index creation** | Inline with `CREATE TABLE` | Separate `CREATE INDEX` statements |
| **Foreign keys** | Enforced by default | Requires `PRAGMA foreign_keys=ON` |

### Query Compatibility Patterns

**Timestamp handling:**
```sql
-- MySQL
WHERE created_at >= NOW() - INTERVAL 5 MINUTE

-- SQLite
WHERE created_at >= datetime('now', '-5 minutes')
```

**JSON access:**
```sql
-- MySQL
SELECT JSON_EXTRACT(metadata, '$.key') FROM table;

-- SQLite
SELECT json_extract(metadata, '$.key') FROM table;
```

## Migration Execution

### Manual Execution

**MySQL:**
```bash
mysql -u username -p database_name < db/V2__add_leaderboard_cache.sql
```

**SQLite:**
```bash
sqlite3 database.db < db/sqlite/V2__add_leaderboard_cache.sql
```

### Programmatic Execution

Migrations should be executed by `DatabaseManager.java` during plugin initialization:

```java
// Example migration execution pattern
public void runMigrations() {
    if (isMySQL()) {
        executeMigration("db/V2__add_leaderboard_cache.sql");
    } else {
        executeMigration("db/sqlite/V2__add_leaderboard_cache.sql");
    }
}
```

## Validation

### Post-Migration Checks

**Verify table exists:**
```sql
-- MySQL
SHOW TABLES LIKE 'quest_leaderboard_cache';

-- SQLite
SELECT name FROM sqlite_master WHERE type='table' AND name='quest_leaderboard_cache';
```

**Verify schema structure:**
```sql
-- MySQL
DESCRIBE quest_leaderboard_cache;

-- SQLite
PRAGMA table_info(quest_leaderboard_cache);
```

**Verify indexes:**
```sql
-- MySQL
SHOW INDEX FROM quest_leaderboard_cache;

-- SQLite
SELECT * FROM sqlite_master WHERE type='index' AND tbl_name='quest_leaderboard_cache';
```

## Rollback Strategy

### V2 Rollback (Leaderboard Cache)

**MySQL:**
```sql
DROP TABLE IF EXISTS quest_leaderboard_cache;
```

**SQLite:**
```sql
DROP TABLE IF EXISTS quest_leaderboard_cache;
```

**Impact:** No data loss in other tables. Leaderboards will revert to non-cached queries (slower performance).

## Performance Considerations

### Migration Execution Time

| Migration | Table Size | Expected Duration | Impact |
|-----------|------------|-------------------|--------|
| V2 | New table | <1 second | None (no data) |

### Index Creation

All indexes are created during initial table creation for optimal performance. No post-creation index builds required.

### Connection Pooling

- **MySQL**: Use HikariCP with `maximumPoolSize=10`
- **SQLite**: Use HikariCP with `maximumPoolSize=1` (single-writer limitation)

## Testing

### Migration Testing Checklist

- [ ] Execute migration on empty MySQL database
- [ ] Execute migration on empty SQLite database
- [ ] Verify table structure matches specification
- [ ] Verify indexes are created correctly
- [ ] Test rollback script successfully removes table
- [ ] Verify plugin starts without errors
- [ ] Test cache read/write operations
- [ ] Validate TTL expiration logic

### Test Queries

**Insert test cache entry:**
```sql
-- MySQL
INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES ('most_completed', '{"entries": []}', NOW() + INTERVAL 5 MINUTE);

-- SQLite
INSERT INTO quest_leaderboard_cache (leaderboard_type, cache_data, expires_at)
VALUES ('most_completed', '{"entries": []}', datetime('now', '+5 minutes'));
```

**Check expired entries:**
```sql
-- MySQL
SELECT * FROM quest_leaderboard_cache WHERE expires_at < NOW();

-- SQLite
SELECT * FROM quest_leaderboard_cache WHERE expires_at < datetime('now');
```

**Clear expired cache:**
```sql
-- MySQL
DELETE FROM quest_leaderboard_cache WHERE expires_at < NOW();

-- SQLite
DELETE FROM quest_leaderboard_cache WHERE expires_at < datetime('now');
```

## Future Migrations

### Planned Migrations (Pending)

- **V3**: Add quest prerequisites system (quest dependencies)
- **V4**: Add quest cooldown tracking (daily/weekly reset timers)
- **V5**: Add quest completion statistics aggregation tables
- **V6**: Add player achievement tracking integration

### Migration Guidelines

1. **Always maintain backward compatibility**
2. **Test on both MySQL and SQLite**
3. **Document all schema changes**
4. **Include rollback scripts**
5. **Verify performance impact**
6. **Update README with new migrations**

## Security Considerations

### SQL Injection Prevention

All migrations use:
- Prepared statements in application code
- Parameterized queries
- No dynamic SQL generation
- Input validation for all user data

### Credential Management

- Database credentials stored in `config.yml`
- Never commit credentials to version control
- Use environment variables in production
- Rotate credentials regularly

## Troubleshooting

### Common Issues

**Issue:** Migration fails with "table already exists"
**Solution:** Check if migration was previously executed. Drop table manually if needed.

**Issue:** Foreign key constraint fails
**Solution:** Ensure referenced tables exist. Enable foreign key support in SQLite.

**Issue:** JSON column error in SQLite
**Solution:** SQLite stores JSON as TEXT. Parse manually in application code.

**Issue:** Timestamp format mismatch
**Solution:** Use ISO-8601 format for SQLite (`YYYY-MM-DD HH:MM:SS`).

## References

- **Main Schema:** `src/main/resources/schema/mysql.sql`
- **SQLite Schema:** `src/main/resources/schema/sqlite.sql`
- **DatabaseManager:** `src/main/java/org/fourz/RVNKQuests/data/DatabaseManager.java`
- **PRP Document:** `PRPs/quest-features-sprint.md`

## Changelog

| Version | Date | Description | Files |
|---------|------|-------------|-------|
| V2 | 2026-02-01 | Add leaderboard cache table | MySQL + SQLite |

---

**Last Updated:** 2026-02-01
**Maintained By:** RVNKQuests Development Team
**Contact:** See main README.md for support information
