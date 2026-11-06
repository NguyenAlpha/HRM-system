// Usage (from the repo root): node api/docs/prompt/scripts/doc-vs-schema.mjs /tmp/hrm_schema.txt [schema doc]
// The export comes from schema-export.sql; the doc defaults to api/docs/database/DATABASE_SCHEMA.md. Compares:
//  1. each "### `table`" section's column rows (name, type, NOT NULL/PK) with the migrated schema, both ways;
//  2. §11 index names (the doc calls the idx_* list complete) with the database, both ways;
//  3. §12.1 constraint names with the database (the doc lists the main ones only, so one way);
//  4. §1.2 table count and table names with the database, both ways;
//  5. §15: every migration file the doc names exists, every file exists in the doc, and every `table` named in a
//     row is created by that file.
import { existsSync, readFileSync, readdirSync } from "node:fs"
import { join } from "node:path"

const [exportFile, docFile = "api/docs/database/DATABASE_SCHEMA.md"] = process.argv.slice(2)
const migrationDir = "api/src/main/resources/db/migration"
const read = (file) => readFileSync(file, "utf8").replace(/\r\n/g, "\n")

const columns = new Map() // table -> Map(column -> { type, nullable })
const constraints = new Set()
const indexes = new Set()
for (const line of read(exportFile).split("\n")) {
  const f = line.split("|")
  if (f[0] === "C") {
    const type = f[3] === "character varying" || f[3] === "character" ? `${f[3]}(${f[4]})`
      : f[3] === "numeric" && f[5] ? `numeric(${f[5]},${f[6]})` : f[3]
    if (!columns.has(f[1])) columns.set(f[1], new Map())
    columns.get(f[1]).set(f[2], { type, nullable: f[7] === "YES" })
  } else if (f[0] === "K") constraints.add(f[2])
  else if (f[0] === "I") indexes.add(f[2])
}
if (!columns.size) {
  console.log(`?? no column lines in ${exportFile}`)
  process.exit(1)
}

const doc = read(docFile)
const lines = doc.split("\n")
const section = (heading) => {
  const start = lines.findIndex((l) => l.startsWith(heading))
  if (start < 0) return ""
  const level = heading.match(/^#+/)[0].length
  const end = lines.findIndex((l, i) => i > start && new RegExp(`^#{1,${level}} `).test(l))
  return lines.slice(start, end < 0 ? undefined : end).join("\n")
}

const SQL_TYPE = {
  BIGSERIAL: "bigint", SERIAL: "integer", BIGINT: "bigint", INTEGER: "integer", SMALLINT: "smallint", TEXT: "text",
  BOOLEAN: "boolean", DATE: "date", TIME: "time without time zone", TIMESTAMPTZ: "timestamp with time zone",
  BYTEA: "bytea",
}
function normalize(docType) {
  const t = docType.trim().toUpperCase()
  let m
  if ((m = t.match(/^VARCHAR\((\d+)\)$/))) return `character varying(${m[1]})`
  if ((m = t.match(/^CHAR\((\d+)\)$/))) return `character(${m[1]})`
  if ((m = t.match(/^NUMERIC\((\d+),\s*(\d+)\)$/))) return `numeric(${m[1]},${m[2]})`
  return SQL_TYPE[t] ?? `?${docType.trim()}`
}

// 1. Column rows of each table section.
const out = { tables: [], columns: [], types: [], nullability: [] }
const documented = new Set()
let table = null
let rows = 0
for (const line of lines) {
  const heading = line.match(/^#{3,4} `(\w+)`/)
  if (heading) {
    table = heading[1]
    documented.add(table)
    if (!columns.has(table)) out.tables.push(`${table}: documented, not in the database`)
    continue
  }
  if (/^#{1,4} /.test(line)) table = null
  const row = table && line.match(/^\| `(\w+)` \| ([^|]+) \| ([^|]*) \|/)
  if (!row || !columns.has(table)) continue
  rows++
  const [, column, docType, docConstraints] = row
  const actual = columns.get(table).get(column)
  if (!actual) {
    out.columns.push(`${table}.${column}: documented, not in the database`)
    continue
  }
  if (normalize(docType) !== actual.type) out.types.push(`${table}.${column}: doc ${docType.trim()}, database ${actual.type}`)
  const docNotNull = /NOT NULL|\bPK\b/.test(docConstraints)
  if (docNotNull === actual.nullable) {
    out.nullability.push(`${table}.${column}: doc ${docNotNull ? "NOT NULL" : "nullable"}, database ${actual.nullable ? "nullable" : "NOT NULL"}`)
  }
}
for (const [t, cols] of columns) {
  if (!documented.has(t)) {
    out.tables.push(`${t}: in the database, no section`)
    continue
  }
  const docText = section(lines.find((l) => new RegExp(`^#{3,4} \`${t}\``).test(l)))
  for (const c of cols.keys()) if (!docText.includes(`| \`${c}\` |`)) out.columns.push(`${t}.${c}: in the database, not documented`)
}

// 2. §11 indexes, 3. §12.1 constraints.
const docIndexes = new Set([...section("## 11.").matchAll(/CREATE (?:UNIQUE )?INDEX (\w+)/g)].map((m) => m[1]))
const indexDiff = [
  ...[...docIndexes].filter((i) => !indexes.has(i)).map((i) => `${i}: documented, not in the database`),
  ...[...indexes].filter((i) => i.startsWith("idx_") && !docIndexes.has(i)).map((i) => `${i}: in the database, not in §11`),
]
const docConstraints = [...section("### 12.1.").matchAll(/ADD CONSTRAINT (\w+)/g)].map((m) => m[1])
const constraintDiff = docConstraints.filter((c) => !constraints.has(c)).map((c) => `${c}: documented, not in the database`)

// 4. §1.2 table list.
const listSection = section("### 1.2.")
const listed = new Set([...listSection.matchAll(/`(\w+)`/g)].map((m) => m[1]).filter((n) => n.includes("_") || columns.has(n)))
const count = Number(listSection.match(/Danh sách (\d+) bảng/)?.[1])
const listRows = [...listSection.matchAll(/^\| [^|]+ \| (.+) \|$/gm)].flatMap((m) => [...m[1].matchAll(/`(\w+)`/g)].map((x) => x[1]))
const listDiff = [
  ...(count !== listRows.length ? [`heading says ${count} tables, the list has ${listRows.length}`] : []),
  ...[...listed].filter((n) => !columns.has(n) && n !== "flyway_schema_history").map((n) => `${n}: listed, not in the database`),
  ...[...columns.keys()].filter((n) => !listed.has(n)).map((n) => `${n}: in the database, not in §1.2`),
]

// 5. §15 migration table.
const files = existsSync(migrationDir) ? readdirSync(migrationDir).filter((f) => f.endsWith(".sql")) : []
const migrationDiff = []
const docFiles = new Set()
for (const m of section("## 15.").matchAll(/^\| `(V\d+__\w+\.sql)` \| ([^|]+) \|/gm)) {
  docFiles.add(m[1])
  if (!files.includes(m[1])) {
    migrationDiff.push(`${m[1]}: documented, no such file`)
    continue
  }
  const sql = read(join(migrationDir, m[1]))
  for (const t of m[2].matchAll(/`(\w+)`/g)) {
    if (columns.has(t[1]) && !new RegExp(`CREATE TABLE ${t[1]}\\b`).test(sql)) migrationDiff.push(`${m[1]}: \`${t[1]}\` is created elsewhere`)
  }
}
for (const f of files) if (!docFiles.has(f)) migrationDiff.push(`${f}: file not in §15`)

const print = (title, items) => {
  console.log(`\n-- ${title}: ${items.length}`)
  for (const item of items) console.log(item)
}
console.log(`== ${documented.size} table sections, ${rows} column rows checked against ${columns.size} tables; ` +
  `${docIndexes.size} indexes in §11, ${docConstraints.length} constraints in §12.1, ${docFiles.size} migrations in §15`)
print("tables", out.tables)
print("columns", out.columns)
print("types", out.types)
print("NOT NULL", out.nullability)
print("§11 indexes", indexDiff)
print("§12.1 constraint names", constraintDiff)
print("§1.2 table list", listDiff)
print("§15 migrations", migrationDiff)
