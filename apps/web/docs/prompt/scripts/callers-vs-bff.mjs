// Usage: node callers-vs-bff.mjs <apps/web dir>
// workforceApi("…") calls in components/ and lib/ that no WORKFORCE_ROUTES pattern (lib/auth/server.ts) accepts.
// The query string is ignored. A ${…} ending in "id"/"Id" stands for an id; any other ${…} (a variable prefix such as
// `${base}`, a chosen action) makes the call "?", to check by hand.
import { readFileSync, readdirSync, statSync } from "node:fs"
import { join } from "node:path"

const dir = process.argv[2]
const server = readFileSync(join(dir, "lib/auth/server.ts"), "utf8")
const ID = "[1-9]\\d*"
const start = server.indexOf("const WORKFORCE_ROUTES: Route[] = [")
const body = server.slice(start, server.indexOf("\n]", start))
const patterns = []
for (const line of body.split("\n")) {
  let m = line.match(/pattern: \/(.*?)\/, methods: \[(.*?)\]/) ?? line.match(/pattern: new RegExp\(`(.*?)`\), methods: \[(.*?)\]/)
  let source = m?.[1]
  if (!m) {
    m = line.match(/pattern: ([A-Z_]+), methods: \[(.*?)\]/)
    if (!m) continue
    source = server.match(new RegExp("const " + m[1] + " = new RegExp\\(`(.*?)`\\)"))[1]
  }
  patterns.push({ re: new RegExp(source.replaceAll("${ID}", ID)), methods: m[2].replaceAll('"', "").split(",").map((s) => s.trim()) })
}

function walk(path, out = []) {
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (/\.tsx?$/.test(name) && name !== "api-schema.ts") out.push(full)
  }
  return out
}

let checked = 0
let manual = 0
let problems = 0
for (const file of [...walk(join(dir, "components")), ...walk(join(dir, "lib"))]) {
  const text = readFileSync(file, "utf8")
  const call = /workforceApi(?:<[^>]*>)?\(\s*(["'`])((?:(?!\1).)*)\1\s*(?:,\s*"([A-Z]+)")?/g
  for (const m of text.matchAll(call)) {
    const raw = m[2]
    const method = m[3] ?? "GET"
    const line = text.slice(0, m.index).split("\n").length
    // Drop the query string (a "?" outside ${…}), then resolve each ${…}: an id (ends in "id"/"Id") stands for 1,
    // anything else (a variable prefix, a chosen action) is left to check by hand.
    let depth = 0
    let cut = raw.length
    for (let i = 0; i < raw.length; i++) {
      if (raw.startsWith("${", i)) depth++
      else if (raw[i] === "}" && depth > 0) depth--
      else if (raw[i] === "?" && depth === 0) { cut = i; break }
    }
    let unresolved = false
    const path = raw.slice(0, cut).replace(/\$\{([^}]*)\}/g, (_, expr) => {
      if (/^[\w.?]*(?:id|Id)$/.test(expr.trim())) return "1"
      unresolved = true
      return "?"
    })
    if (unresolved) {
      manual++
      console.log(`?  ${file}:${line} ${method} ${raw}`)
      continue
    }
    checked++
    if (!patterns.some((p) => p.re.test(path) && p.methods.includes(method))) {
      problems++
      console.log(`X  ${file}:${line} ${method} ${raw}`)
    }
  }
}
console.log(`${checked} call(s) matched against ${patterns.length} routes, ${problems} without a route, ${manual} to check by hand`)
