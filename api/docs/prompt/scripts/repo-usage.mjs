// Usage (from the repo root): node api/docs/prompt/scripts/repo-usage.mjs /tmp/hrm_schema.txt [repo root]
// The export comes from schema-export.sql. Prints the repository code that decides two schema questions:
//  1. each single-column foreign key with no index starting with it (audit *_by_account_id left out, as in
//     schema-checks.sql section 1): the repository lines that filter or join on its entity field. No line means
//     no query uses it, so the missing index costs nothing; a line is a query to judge (how often it runs).
//  2. each unique constraint or index on a soft-deleted table (deleted_at) that keeps deleted rows: the repository
//     duplicate checks (existsBy/findBy/countBy) on its columns, marked "counts deleted" when they do not filter
//     deletedAt. Deleted rows then hold the value for good in both the DB and the service.
// Matching is by name (method names and JPQL paths), so read each line before writing a finding.
import { readFileSync, readdirSync } from "node:fs"
import { join } from "node:path"

const [exportFile, root = "."] = process.argv.slice(2)
const javaRoot = join(root, "api/src/main/java/com/htttdn/hrm")
const read = (file) => readFileSync(file, "utf8").replace(/\r\n/g, "\n")

const tables = new Map() // table -> { columns: Set, constraints: [{ type, def }], indexes: [def] }
const table = (name) => {
  if (!tables.has(name)) tables.set(name, { columns: new Set(), constraints: [], indexes: [] })
  return tables.get(name)
}
for (const line of read(exportFile).split("\n")) {
  const f = line.split("|")
  if (f[0] === "C") table(f[1]).columns.add(f[2])
  else if (f[0] === "K") table(f[1]).constraints.push({ type: f[3], def: f.slice(4).join("|") })
  else if (f[0] === "I") table(f[1]).indexes.push(f.slice(3).join("|"))
}
if (!tables.size) {
  console.log(`?? no lines in ${exportFile}`)
  process.exit(1)
}

// Entity class and field name of each table column, from @Table and @Column/@JoinColumn (or the default naming).
const entities = new Map() // table -> { cls, fields: Map(column -> field) }
const camel = (s) => s.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase())
for (const file of readdirSync(join(javaRoot, "entity")).filter((f) => f.endsWith(".java"))) {
  const src = read(join(javaRoot, "entity", file))
  const tableName = src.match(/@Table\(\s*name\s*=\s*"(\w+)"/)?.[1]
  if (!tableName) continue
  const fields = new Map()
  for (const m of src.matchAll(/((?:\n\s*@[^\n]+)*)\n\s*private (?!static )(?:final )?[\w<>, ?[\]]+? (\w+)( = [^;]+)?;/g)) {
    const column = m[1].match(/@(?:Join)?Column\([^)]*name\s*=\s*"(\w+)"/)?.[1]
    fields.set(column ?? m[2].replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`), m[2])
  }
  entities.set(tableName, { cls: file.replace(".java", ""), fields })
}

const repoDir = join(javaRoot, "repository")
const repos = readdirSync(repoDir).filter((f) => f.endsWith(".java"))
  .map((f) => ({ name: f.replace(".java", ""), lines: read(join(repoDir, f)).split("\n") }))
const pascal = (s) => s[0].toUpperCase() + s.slice(1)

// Lines of the entity's own repository that use a field: a derived method name with "By…Field" (Field, FieldId),
// a JPQL path ".field", or the column name in a native query. Joins written in other repositories are not seen.
function usages(tableName, column) {
  const entity = entities.get(tableName)
  const field = entity?.fields.get(column) ?? camel(column.replace(/_id$/, ""))
  const out = []
  for (const repo of repos.filter((r) => entity && r.name === `${entity.cls}Repository`)) {
    repo.lines.forEach((text, i) => {
      const derived = new RegExp(`\\b(find|exists|count|delete|get|read|query|stream)\\w*By\\w*${pascal(field)}`).test(text)
      const jpql = new RegExp(`\\.${field}\\b(?!\\()`).test(text) && /[A-Za-z]\.\w/.test(text) && !/^\s*(import|package)/.test(text)
      const nativeSql = new RegExp(`\\b${column}\\b`).test(text)
      if (derived || jpql || nativeSql) out.push(`${repo.name}.java:${i + 1}: ${text.trim()}`)
    })
  }
  return out
}

console.log("1. Foreign keys with no index starting with their column: repository lines using the column")
let fkCount = 0
for (const [name, t] of [...tables].sort()) {
  for (const k of t.constraints.filter((c) => c.type === "f")) {
    const cols = k.def.match(/^FOREIGN KEY \(([^)]+)\)/)?.[1].split(", ")
    if (!cols || cols.length !== 1 || cols[0].endsWith("_by_account_id")) continue
    const col = cols[0]
    // Leading column only: "USING btree (col, …)", "UNIQUE (col, …)", "EXCLUDE USING gist (col WITH =, …)".
    const indexed = t.indexes.some((d) => new RegExp(`USING \\w+ \\((?:lower\\()?${col}\\b`).test(d))
      || t.constraints.some((c) => ["p", "u", "x"].includes(c.type) && new RegExp(`^[A-Z ]+(?:USING \\w+ )?\\(${col}\\b`).test(c.def))
    if (indexed) continue
    fkCount++
    const lines = usages(name, col)
    console.log(`  ${name}.${col}: ${lines.length ? "" : "no repository line uses it"}`)
    for (const l of lines) console.log(`    ${l}`)
  }
}
console.log(`  (${fkCount} foreign keys)`)

console.log("\n2. Uniques on soft-deleted tables that keep deleted rows: repository duplicate checks on the same columns")
for (const [name, t] of [...tables].sort()) {
  if (!t.columns.has("deleted_at")) continue
  const uniques = [
    ...t.constraints.filter((c) => c.type === "u").map((c) => c.def),
    ...t.indexes.filter((d) => /^CREATE UNIQUE INDEX/.test(d) && !/_pkey ON /.test(d)),
  ].filter((d) => !/deleted_at IS NULL/.test(d))
  const seen = new Set()
  for (const def of uniques) {
    const col = def.match(/\((?:lower\(\(?)?(\w+)/)?.[1]
    if (!col || seen.has(col)) continue
    seen.add(col)
    const entity = entities.get(name)
    const field = entity?.fields.get(col) ?? camel(col)
    const repo = entity && repos.find((r) => r.name === `${entity.cls}Repository`)
    const checks = (repo?.lines ?? []).map((text, i) => ({ text: text.trim(), n: i + 1 }))
      .filter((l) => new RegExp(`\\b(exists|find|count)\\w*By${pascal(field)}`).test(l.text))
    console.log(`  ${name}.${col}: ${checks.length ? "" : "no duplicate check in the repository"}`)
    for (const l of checks) {
      // A @Query sits on the lines just above its method.
      const query = repo.lines.slice(Math.max(0, l.n - 4), l.n).join(" ")
      const skips = /DeletedAtIsNull/.test(l.text) || (/@Query/.test(query) && /deletedAt IS NULL/.test(query))
      const mark = skips ?"skips deleted " : "counts deleted"
      console.log(`    ${mark}  ${repo.name}.java:${l.n}: ${l.text}`)
    }
  }
}
