---
globs: "app/src/main/kotlin/com/metrolist/music/db/**/*.kt"
---

# Database Patterns

- Single `InternalDatabase` (Room) wrapped by `MusicDatabase` delegating to `DatabaseDao`
- `DatabaseDao.kt` is 1747 lines — add new queries at the bottom in labeled sections
- Entity pattern: `@Entity(tableName = "...")` + `@Immutable` + `@PrimaryKey val id: String`
- Use `@ColumnInfo(defaultValue = "...")` for migration-safe defaults
- Timestamps: `java.time.LocalDateTime` (core library desugaring enabled)
- Schema export: `app/schemas/` directory — auto-generated, commit after migrations
- Migrations: prefer AutoMigration. Manual `Migration` only when ALTER TABLE is insufficient
- Bridge cache tables (`bridge_similar_artists`, `bridge_artist_meta`) get PURGED on cold start
- History tables must be SEPARATE from cache tables — never extend purgeable tables
- `database.transaction { }` and `database.query { }` for wrapped operations
- Suspend functions for all DAO methods that touch bridge/history tables
