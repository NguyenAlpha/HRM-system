// Usage (from the repo root): node api/docs/prompt/scripts/request-sizes.mjs [repo root]
// String fields of request records without @Size: records in dto/request and controller/, and records named
// *Command, *Request or *Body in service/. Search criteria (keyword, roleCode…) never reach a column and may stay
// unbounded; any other field listed here can hit a VARCHAR column as a 500, or let the web form drift from the API.
// The limit itself is not compared with the column: check new or changed limits against the migration by hand.
import { readFileSync, readdirSync, statSync } from "node:fs"
import { join, relative } from "node:path"

const root = process.argv[2] ?? "."
const src = join(root, "api/src/main/java/com/htttdn/hrm")

function walk(path, out = []) {
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (name.endsWith(".java")) out.push(full)
  }
  return out
}

const files = [...walk(join(src, "dto/request")), ...walk(join(src, "controller")), ...walk(join(src, "service"))]
let fields = 0
const missing = []
for (const file of files) {
  const text = readFileSync(file, "utf8").replace(/\r\n/g, "\n")
  const inService = /[\\/]service[\\/]/.test(file)
  for (const record of text.matchAll(/record\s+(\w+)\s*\(/g)) {
    if (inService && !/(Command|Request|Body)$/.test(record[1])) continue
    let depth = 1
    let i = record.index + record[0].length
    while (depth && i < text.length) {
      if (text[i] === "(") depth++
      else if (text[i] === ")") depth--
      i++
    }
    const header = text.slice(record.index + record[0].length, i - 1).replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*/g, "")
    const params = []
    let current = ""
    depth = 0
    for (const ch of header) {
      if ("(<".includes(ch)) depth++
      if (")>".includes(ch)) depth--
      if (ch === "," && depth === 0) {
        params.push(current)
        current = ""
      } else current += ch
    }
    params.push(current)
    for (const param of params) {
      const field = param.trim().match(/\b(?:String|List<String>|Set<String>)\s+(\w+)$/)
      if (!field) continue
      fields++
      if (!param.includes("@Size")) missing.push(`${relative(root, file)}: ${record[1]}.${field[1]}`)
    }
  }
}
console.log(`${fields} string fields in request records, ${missing.length} without @Size`)
for (const line of missing) console.log(line)
