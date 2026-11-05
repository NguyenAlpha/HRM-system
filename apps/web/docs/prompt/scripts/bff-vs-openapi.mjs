// Usage: node bff-vs-openapi.mjs <apps/web dir>
// Lists whitelisted BFF routes (lib/auth/server.ts) that match no endpoint + method in openapi.json.
import { readFileSync } from "node:fs"
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
    endpoints.push({ path: path.replace(/\{[^}]+\}/g, "1"), method: method.toUpperCase() })
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
for (const [list, prefix] of Object.entries(PREFIX)) {
  const body = routeList(list)
  if (!body) { console.log(`?? ${list} not found in server.ts`); problems++; continue }
  for (const line of body.split("\n")) {
    const source = patternSource(line)
    if (!source) continue
    const pattern = new RegExp(source)
    const methods = line.match(/methods: \[(.*?)\]/)[1].replaceAll('"', "").split(",").map((s) => s.trim())
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
