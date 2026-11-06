// Usage (from the repo root): node api/docs/prompt/scripts/controller-calls.mjs [repo root]
// Starts from every service method a controller calls and prints two lists:
//  1. calls whose method has no @PreAuthorize: not on the method, not on its class, not through an annotation type
//     that carries @PreAuthorize (@CanManageRbac…). Each must be a public endpoint or check access in its own code.
//  2. services no test reaches: the class name is in no file of api/src/test, and no file of api/src/test or
//     apps/web/e2e names every fixed segment of an endpoint that calls it. "PATH" lines did match a path: open the
//     file and judge whether it really exercises the service (a BFF guard test does not).
import { readFileSync, readdirSync, statSync } from "node:fs"
import { join, relative } from "node:path"

const root = process.argv[2] ?? "."
const src = join(root, "api/src/main/java/com/htttdn/hrm")

function walk(path, extensions, out = []) {
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) walk(full, extensions, out)
    else if (extensions.some((extension) => name.endsWith(extension))) out.push(full)
  }
  return out
}
const read = (file) => readFileSync(file, "utf8").replace(/\r\n/g, "\n")
const lineOf = (text, index) => text.slice(0, index).split("\n").length

// Annotation types that are themselves annotated with @PreAuthorize.
const meta = []
for (const file of walk(src, [".java"])) {
  const text = read(file)
  const type = text.match(/public @interface (\w+)/)
  if (type && text.includes("@PreAuthorize(")) meta.push(type[1])
}
const guard = new RegExp(`@PreAuthorize\\(${meta.length ? `|@(?:${meta.join("|")})\\b` : ""}`)

const services = new Map()
for (const file of walk(join(src, "service"), [".java"])) {
  services.set(file.split(/[\\/]/).pop().replace(".java", ""), { file, text: read(file) })
}

// Text between the previous member (or the imports) and a declaration: its javadoc and annotations.
function preamble(text, index) {
  const start = Math.max(text.lastIndexOf("\n    }\n", index), text.lastIndexOf(";\n", index), text.lastIndexOf("{\n", index))
  return text.slice(start, index)
}

function guarded(service, method) {
  const classAt = service.text.search(/\npublic (?:abstract |final )?class /)
  if (guard.test(preamble(service.text, classAt))) return { state: "class" }
  const declarations = [...service.text.matchAll(new RegExp(`\\n {4}public [^;={]*?\\b${method}\\(`, "g"))]
  if (!declarations.length) return { state: "not found" }
  const with_ = declarations.filter((d) => guard.test(preamble(service.text, d.index)))
  const line = lineOf(service.text, declarations[0].index + 1)
  if (with_.length === declarations.length) return { state: "method" }
  return { state: with_.length ? "some overloads" : "none", line }
}

const tests = [...walk(join(root, "api/src/test"), [".java"]), ...walk(join(root, "apps/web/e2e"), [".ts"])]
  .map((file) => ({ file: relative(root, file), text: read(file) }))
const usage = new Map() // service -> endpoints that call it
let calls = 0
const unguarded = []

for (const file of walk(join(src, "controller"), [".java"])) {
  const text = read(file)
  const controller = file.split(/[\\/]/).pop()
  const base = text.match(/@RequestMapping\(\s*"([^"]*)"/)?.[1] ?? ""
  const fields = new Map([...text.matchAll(/private final (\w+) (\w+);/g)].map((m) => [m[2], m[1]]))
  const chunks = text.split(/(?=@(?:Get|Post|Put|Patch|Delete)Mapping\b)/).slice(1)
  for (const chunk of chunks) {
    const mapping = chunk.match(/^@(\w+)Mapping(?:\(([^)]*)\))?/)
    const path = base + (mapping[2]?.match(/"([^"]*)"/)?.[1] ?? "")
    const endpoint = `${mapping[1].toUpperCase()} ${path}`
    for (const call of chunk.matchAll(/\b(\w+)\.(\w+)\(/g)) {
      const type = fields.get(call[1])
      if (!type || !services.has(type)) continue
      calls++
      if (!usage.has(type)) usage.set(type, new Set())
      usage.get(type).add(path)
      const result = guarded(services.get(type), call[2])
      if (result.state === "none" || result.state === "some overloads" || result.state === "not found") {
        unguarded.push(`${result.state.toUpperCase().padEnd(14)} ${endpoint}  ${type}.${call[2]}  ` +
          `${relative(root, services.get(type).file)}${result.line ? `:${result.line}` : ""}`)
      }
    }
  }
}

console.log(`== ${calls} controller -> service calls, ${unguarded.length} without @PreAuthorize (meta: ${meta.join(", ")})`)
for (const line of [...new Set(unguarded)].sort()) console.log(line)

console.log(`\n== Services called by controllers that no test reaches`)
let none = 0
for (const [service, paths] of [...usage].sort()) {
  if (tests.some((t) => new RegExp(`\\b${service}\\b`).test(t.text))) continue
  const hits = new Set()
  for (const path of paths) {
    const segments = path.split("/").filter((s) => s && !s.startsWith("{"))
    for (const t of tests) if (segments.every((s) => t.text.includes(`/${s}`))) hits.add(t.file)
  }
  if (!hits.size) none++
  console.log(`${hits.size ? "PATH" : "NONE"}  ${service}  ${[...paths].join(", ")}${hits.size ? `  <- ${[...hits].join(", ")}` : ""}`)
}
console.log(`${usage.size} services called by controllers, ${none} with no test at all`)
