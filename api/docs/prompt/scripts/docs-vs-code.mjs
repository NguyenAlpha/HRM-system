// Usage (from the repo root): node api/docs/prompt/scripts/docs-vs-code.mjs [repo root]
//  1. Relative links in api/docs and docs (archive/ skipped) whose target file does not exist, e.g. code links left
//     behind when a class moves package.
//  2. Controller endpoints that no doc in api/docs/api or api/docs/concepts names as "METHOD /api/path" (path
//     variables and numeric ids compare equal). A prefix-only row in api/docs/api/README.md does not count.
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs"
import { dirname, join, relative } from "node:path"

const root = process.argv[2] ?? "."

function walk(path, extension, out = []) {
  if (!existsSync(path)) return out
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) {
      if (name !== "archive") walk(full, extension, out)
    } else if (name.endsWith(extension)) out.push(full)
  }
  return out
}

const docs = [...walk(join(root, "api/docs"), ".md"), ...walk(join(root, "docs"), ".md")]
const broken = []
for (const file of docs) {
  const text = readFileSync(file, "utf8")
  for (const m of text.matchAll(/\]\(([^)\s]+)\)/g)) {
    const target = m[1].split("#")[0]
    if (!target || /^(https?:|mailto:)/.test(target)) continue
    if (!existsSync(join(dirname(file), target))) {
      broken.push(`${relative(root, file)}:${text.slice(0, m.index).split("\n").length}: ${m[1]}`)
    }
  }
}
console.log(`== ${docs.length} docs, ${broken.length} broken relative links`)
for (const line of broken) console.log(line)

const normalize = (path) => path.replace(/\{[^}]+\}/g, "{}").replace(/\/\d+(?=\/|$)/g, "/{}").replace(/\/$/, "")
const documented = new Set()
for (const file of [...walk(join(root, "api/docs/api"), ".md"), ...walk(join(root, "api/docs/concepts"), ".md")]) {
  for (const m of readFileSync(file, "utf8").matchAll(/\b(GET|POST|PUT|PATCH|DELETE)\s+`?(\/api\/[^\s`|)?,]*)/g)) {
    documented.add(`${m[1]} ${normalize(m[2])}`)
  }
}
const undocumented = []
let endpoints = 0
for (const file of walk(join(root, "api/src/main/java/com/htttdn/hrm/controller"), ".java")) {
  const text = readFileSync(file, "utf8")
  const base = text.match(/@RequestMapping\(\s*"([^"]*)"/)?.[1] ?? ""
  for (const m of text.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\(([^)]*)\))?/g)) {
    endpoints++
    const endpoint = `${m[1].toUpperCase()} ${normalize(base + (m[2]?.match(/"([^"]*)"/)?.[1] ?? ""))}`
    if (!documented.has(endpoint)) undocumented.push(`${endpoint}  (${file.split(/[\\/]/).pop()})`)
  }
}
console.log(`\n== ${endpoints} endpoints, ${undocumented.length} not named in api/docs/api or api/docs/concepts`)
for (const line of undocumented.sort()) console.log(line)
