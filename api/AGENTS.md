# API (`api`)

Backend rules that the code alone does not tell you. The root `AGENTS.md` still applies.

## Tests

- `./mvnw test` needs PostgreSQL: run `docker compose up -d database` from the repo root. A `Connection refused` at `flywayInitializer` means the database is down, not a code bug.
- Integration tests must pass on both the seeded dev database and an empty one: create rows with random codes inside a `@Transactional` test, never assert global counts, never reuse seeded codes.

## Database

- Before the first deployment, change the schema by editing the domain's baseline file (`V1`–`V8` in `src/main/resources/db/migration`) — no patch files, and simply leave out anything removed. Local databases then need `docker compose down -v`; say so when you change a baseline.
- Update `docs/database/DATABASE_SCHEMA.md` (the table's own section and the §15 table) in the same change.
- After deleting or renaming a migration, run `./mvnw clean`, or stale copies in `target/classes` still run.
- Permissions and roles live only in the Java seeders (`config/seed/`), never in migrations.
- Adding people to `src/main/resources/seed/staff.json` breaks an existing dev database (their past leaves hit approved payroll periods); it needs `docker compose down -v`.

## Authorization

- `@PreAuthorize` goes on service methods only, never on controllers (`docs/adr/0002-authorization-in-service-layer.md`).

## DTOs and the web types

- The web app's TypeScript types are generated from the OpenAPI spec. Mark every nullable response field with `jakarta.annotation.Nullable`.
- After a DTO change, refresh the snapshot with `./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`, then run `pnpm gen:api` in `apps/web`.
- Do not remove the nullable fix-up block in `OpenApiConfig`; swagger-core otherwise corrupts shared schemas of nested DTOs.
