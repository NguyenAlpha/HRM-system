// Usage (from the repo root): node api/docs/prompt/scripts/entity-vs-schema.mjs /tmp/hrm_schema.txt [repo root]
// The export comes from schema-export.sql. Compares the JPA entities with the migrated schema where
// ddl-auto=validate does not look:
//  1. enum columns (@Enumerated): Java constants a CHECK rejects (the insert fails), values a CHECK allows that no
//     Java constant has, enum columns without a CHECK, constants longer than the VARCHAR;
//  2. NOT NULL columns without a DEFAULT whose entity field may stay null (the insert fails, 409 CONFLICT), and
//     fields the entity declares non-null over a nullable column;
//  3. columns no entity maps (dead, or written only by SQL), and mapped columns the export does not have (a parse
//     problem of this script: validate would refuse to start).
import { readFileSync, readdirSync } from "node:fs"
import { join } from "node:path"

const [exportFile, root = "."] = process.argv.slice(2)
const entityDir = join(root, "api/src/main/java/com/htttdn/hrm/entity")
const read = (file) => readFileSync(file, "utf8").replace(/\r\n/g, "\n")

const columns = new Map() // table -> Map(column -> { type, length, nullable, hasDefault })
const checks = new Map() // table -> [CHECK definitions]
const triggers = new Map()
for (const line of read(exportFile).split("\n")) {
  const f = line.split("|")
  if (f[0] === "C") {
    if (!columns.has(f[1])) columns.set(f[1], new Map())
    columns.get(f[1]).set(f[2], { type: f[3], length: f[4] ? Number(f[4]) : null, nullable: f[7] === "YES", hasDefault: f[8] === "true" })
  } else if (f[0] === "K" && f[3] === "c") {
    if (!checks.has(f[1])) checks.set(f[1], [])
    checks.get(f[1]).push(f.slice(4).join("|"))
  } else if (f[0] === "G") {
    if (!triggers.has(f[1])) triggers.set(f[1], [])
    triggers.get(f[1]).push(f[2])
  }
}
if (!columns.size) {
  console.log(`?? no column lines in ${exportFile}`)
  process.exit(1)
}

// Values the table's CHECKs allow for a column: "(col)::text = ANY (ARRAY['A'::…])" and "(col)::text = 'A'::text".
function allowedValues(table, column) {
  let values = null
  for (const def of checks.get(table) ?? []) {
    for (const m of def.matchAll(new RegExp(`\\(${column}\\)::text = ANY \\(\\(ARRAY\\[([^\\]]*)\\]`, "g"))) {
      values ??= new Set()
      for (const v of m[1].matchAll(/'([^']*)'/g)) values.add(v[1])
    }
    for (const m of def.matchAll(new RegExp(`\\(${column}\\)::text = '([^']*)'::text`, "g"))) {
      values ??= new Set()
      values.add(m[1])
    }
  }
  return values
}

function enumConstants(type) {
  let text
  try {
    text = read(join(entityDir, "enums", `${type}.java`))
  } catch {
    return null
  }
  let body = text.slice(text.indexOf("{", text.indexOf(`enum ${type}`)) + 1)
  body = body.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*/g, "")
  while (/\([^()]*\)/.test(body)) body = body.replace(/\([^()]*\)/g, "")
  return body.split(/;|}/)[0].split(",").map((s) => s.trim()).filter((s) => /^[A-Z][A-Z0-9_]*$/.test(s))
}

const snake = (name) => name.replace(/[A-Z]/g, (c) => "_" + c.toLowerCase())

// Fields of a class with the annotations and javadoc above each one; superclasses in the entity package and
// @EmbeddedId classes are followed.
function fields(className) {
  let text
  try {
    text = read(join(entityDir, `${className}.java`))
  } catch {
    return []
  }
  const out = []
  const parent = text.match(/\bclass \w+ extends (\w+)/)?.[1]
  if (parent) out.push(...fields(parent))
  const start = text.indexOf("{", text.search(/\bclass \w+/)) + 1
  let last = start
  for (const m of text.slice(start).matchAll(/\n\s*private (?!static )(?:final )?([\w<>, ?[\]]+?) (\w+)( = [^;]+)?;/g)) {
    const at = start + m.index
    const notes = text.slice(last, at)
    last = at + m[0].length
    if (/@(Transient|OneToMany|ManyToMany)\b/.test(notes) || /mappedBy\s*=/.test(notes)) continue
    if (/@EmbeddedId\b/.test(notes)) {
      for (const inner of fields(m[1])) out.push({ ...inner, nonNull: true })
      continue
    }
    const named = notes.match(/@(?:Join)?Column\([^)]*\bname\s*=\s*"(\w+)"/)
    out.push({
      field: m[2],
      column: named ? named[1] : snake(m[2]),
      enumType: /@Enumerated\b/.test(notes) ? m[1] : null,
      // @MapsId takes the value from the primary key, so it is never null either.
      nonNull: /nullable\s*=\s*false|optional\s*=\s*false|@Id\b|@MapsId\b|@CreatedDate|@LastModifiedDate/.test(notes) ||
        /^(int|long|short|boolean|double)$/.test(m[1]) || Boolean(m[3]),
    })
  }
  return out
}

const report = { rejected: [], unused: [], noCheck: [], tooLong: [], mayBeNull: [], strictEntity: [], missing: [] }
const mapped = new Map() // table -> Set(columns)
let entityCount = 0
let fieldCount = 0
let enumCount = 0
for (const file of readdirSync(entityDir).filter((name) => name.endsWith(".java"))) {
  const text = read(join(entityDir, file))
  const table = text.match(/@Table\(\s*name\s*=\s*"(\w+)"/)?.[1]
  if (!table || !/@Entity\b/.test(text)) continue
  entityCount++
  const schema = columns.get(table)
  if (!schema) {
    report.missing.push(`${table} (${file}): table not in the export`)
    continue
  }
  if (!mapped.has(table)) mapped.set(table, new Set())
  for (const f of fields(file.replace(".java", ""))) {
    fieldCount++
    mapped.get(table).add(f.column)
    const col = schema.get(f.column)
    const where = `${table}.${f.column}`
    if (!col) {
      report.missing.push(`${where} (${file} ${f.field})`)
      continue
    }
    if (f.enumType) {
      enumCount++
      const constants = enumConstants(f.enumType)
      if (!constants) {
        report.missing.push(`${where}: enum ${f.enumType} not found in entity/enums`)
        continue
      }
      const allowed = allowedValues(table, f.column)
      if (!allowed) report.noCheck.push(`${where} (${f.enumType})`)
      else {
        const rejected = constants.filter((c) => !allowed.has(c))
        const unused = [...allowed].filter((v) => !constants.includes(v))
        if (rejected.length) report.rejected.push(`${where} (${f.enumType}): ${rejected.join(", ")}`)
        if (unused.length) report.unused.push(`${where} (${f.enumType}): ${unused.join(", ")}`)
      }
      const longest = constants.reduce((a, b) => (b.length > a.length ? b : a), "")
      if (col.length && longest.length > col.length) report.tooLong.push(`${where} VARCHAR(${col.length}): ${longest}`)
    }
    if (!col.nullable && !col.hasDefault && !f.nonNull) {
      const trigger = triggers.get(table)
      report.mayBeNull.push(`${where} (${f.field})${trigger ? `  [trigger on table: ${trigger.join(", ")}]` : ""}`)
    }
    if (col.nullable && f.nonNull) report.strictEntity.push(`${where} (${f.field})`)
  }
}
const unmapped = []
for (const [table, cols] of columns) {
  if (!mapped.has(table)) continue
  for (const col of cols.keys()) if (!mapped.get(table).has(col)) unmapped.push(`${table}.${col}`)
}
const unmappedTables = [...columns.keys()].filter((t) => !mapped.has(t))

const section = (title, lines) => {
  console.log(`\n-- ${title}: ${lines.length}`)
  for (const line of lines) console.log(line)
}
console.log(`== ${entityCount} entities, ${fieldCount} mapped fields, ${enumCount} enum columns; ` +
  `${columns.size} tables in the export`)
section("enum constants a CHECK rejects", report.rejected)
section("CHECK values no enum constant has", report.unused)
section("enum columns without a CHECK", report.noCheck)
section("enum constants longer than the column", report.tooLong)
section("NOT NULL columns without DEFAULT whose field may stay null", report.mayBeNull)
section("fields declared non-null over a nullable column", report.strictEntity)
section("columns no entity maps", unmapped)
section("tables no entity maps", unmappedTables)
section("mapped but not in the export (script parse problem)", report.missing)
