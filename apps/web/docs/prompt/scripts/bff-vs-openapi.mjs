// Usage: node bff-vs-openapi.mjs <apps/web dir>
// Lists whitelisted BFF routes (lib/auth/server.ts) that match no endpoint + method in openapi.json, then the other
// way round: API endpoints that no route list, no reference catalogue route and no hard-coded upstream path (a
// single-endpoint handler in app/api or lib) reaches.
import { readFileSync, readdirSync, statSync } from "node:fs"
import { join } from "node:path"

const dir = process.argv[2]
const server = readFileSync(join(dir, "lib/auth/server.ts"), "utf8")
const spec = JSON.parse(readFileSync(join(dir, "openapi.json"), "utf8"))
const ID = "[1-9]\\d*"

// Upstream path the handler puts in front of each route list's relative path.
const PREFIX = {
  RBAC_ROUTES: "/api",
  EMPLOYEE_ROUTES: "/api/employees",
  WORKFORCE_ROUTES: "/api",
  ACCOUNT_LIFECYCLE_ROUTES: "/api/accounts",
  ACCOUNT_ROLE_ASSIGNMENT_ROUTES: "/api/accounts/1/role-assignments",
  ROLE_ASSIGNMENT_REQUEST_ROUTES: "/api/role-assignment-requests",
}

const endpoints = []
for (const [path, operations] of Object.entries(spec.paths)) {
  for (const method of Object.keys(operations)) {
    endpoints.push({ path: path.replace(/\{[^}]+\}/g, "1"), template: path, method: method.toUpperCase() })
  }
}

function routeList(name) {
  const start = server.indexOf(`const ${name}: Route[] = [`)
  if (start < 0) return null
  return server.slice(start, server.indexOf("\n]", start))
}

function patternSource(line) {
  let m = line.match(/pattern: \/(.*?)\/, methods/)
  if (m) return m[1]
  m = line.match(/pattern: new RegExp\(`(.*?)`\), methods/)
  if (m) return m[1].replaceAll("${ID}", ID)
  m = line.match(/pattern: ([A-Z_]+), methods/)
  if (m) {
    const named = server.match(new RegExp("const " + m[1] + " = new RegExp\\(`(.*?)`\\)"))
    return named[1].replaceAll("${ID}", ID)
  }
  return null
}

let problems = 0
let checked = 0
const routes = []
for (const [list, prefix] of Object.entries(PREFIX)) {
  const body = routeList(list)
  if (!body) { console.log(`?? ${list} not found in server.ts`); problems++; continue }
  for (const line of body.split("\n")) {
    const source = patternSource(line)
    if (!source) continue
    const pattern = new RegExp(source)
    const methods = line.match(/methods: \[(.*?)\]/)[1].replaceAll('"', "").split(",").map((s) => s.trim())
    routes.push({ prefix, pattern, methods })
    for (const method of methods) {
      const hit = endpoints.some((e) => {
        if (e.method !== method) return false
        if (e.path === prefix) return pattern.test("")
        return e.path.startsWith(prefix + "/") && pattern.test(e.path.slice(prefix.length + 1))
      })
      checked++
      if (!hit) { problems++; console.log(`${list}: ${method} ${source} -> no ${prefix}/… endpoint`) }
    }
  }
}
console.log(`${checked} route+method pairs checked`)
console.log(problems ? `${problems} route(s) without an API endpoint` : "Every whitelisted BFF route has an API endpoint")

// handleReferenceRequest: list/create on each catalogue in REFERENCE_RESOURCES, update/delete on one entry. Keep in
// step with that handler.
const reference = server.match(/const REFERENCE_RESOURCES = \[(.*?)\]/)?.[1].replaceAll('"', "").split(",")
  .map((s) => s.trim()) ?? []
if (!reference.length) console.log("?? REFERENCE_RESOURCES not found in server.ts")
for (const resource of reference) {
  routes.push({ prefix: "/api", pattern: new RegExp(`^${resource}$`), methods: ["GET", "POST"] })
  routes.push({ prefix: "/api", pattern: new RegExp(`^${resource}/${ID}$`), methods: ["PUT", "DELETE"] })
}

function walk(path, out = []) {
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (/\.tsx?$/.test(name) && name !== "api-schema.ts") out.push(full)
  }
  return out
}
const handlers = [...walk(join(dir, "app/api")), ...walk(join(dir, "lib"))].map((f) => readFileSync(f, "utf8")).join("\n")

let viaList = 0
let viaPath = 0
const unreached = []
for (const e of endpoints) {
  const listed = routes.some((r) => r.methods.includes(e.method) &&
    (e.path === r.prefix ? r.pattern.test("") : e.path.startsWith(r.prefix + "/") && r.pattern.test(e.path.slice(r.prefix.length + 1))))
  if (listed) { viaList++; continue }
  // The upstream path written out in a handler, with any ${…} or literal value for each path variable.
  const literal = new RegExp(e.template.split(/\{[^}]+\}/).map((s) => s.replace(/[.*+?^$()|[\]\\]/g, "\\$&"))
    .join("(?:\\$\\{[^}]+\\}|[^/\"'`\\s]+)") + "(?![\\w/-])")
  if (literal.test(handlers)) { viaPath++; continue }
  unreached.push(`${e.method} ${e.template}`)
}
console.log(`\n${endpoints.length} API endpoints: ${viaList} through a route list, ${viaPath} through a hard-coded path, ` +
  `${unreached.length} not reachable from the web`)
for (const line of unreached.sort()) console.log(line)
