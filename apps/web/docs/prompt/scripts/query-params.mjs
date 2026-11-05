// Usage: node query-params.mjs <apps/web dir>
// For each BFF handler group: the query parameters the API endpoints accept (openapi.json) next to the
// queryKeys the handler passes on (lib/auth/server.ts). A key the API needs but the handler drops makes a
// filter silently do nothing; a key no endpoint takes is dead.
import { readFileSync } from "node:fs"
import { join } from "node:path"

const dir = process.argv[2]
const spec = JSON.parse(readFileSync(join(dir, "openapi.json"), "utf8"))
const server = readFileSync(join(dir, "lib/auth/server.ts"), "utf8")

// Upstream paths each handler reaches, and a snippet that locates its queryKeys array in server.ts.
const GROUPS = {
  workforce: { paths: /^\/api\/(attendance|company-holidays|company$|leave-requests|leave-entitlements|resignation-requests|payroll|compensation|reports\/(hr\/(turnover|monthly-status|leave-balances)|payroll\/summary))/, keys: 'forwardWithSession(request, "hrm", `/api/${path}`' },
  reference: { paths: /^\/api\/(organization-units|work-locations|job-positions|work-shifts)$/, keys: "`/api/${resource}" },
  employees: { paths: /^\/api\/employees/, keys: "`/api/employees${path" },
  accounts: { paths: /^\/api\/accounts(\/\{accountId\})?$/, keys: "`/api/accounts${path" },
  rbac: { paths: /^\/api\/(roles|permissions)/, keys: "`/api/${path}`, [" },
  roleRequests: { paths: /^\/api\/role-assignment-requests/, keys: "`/api/role-assignment-requests${path" },
  reports: { paths: /workforce-distribution|salary-distribution/, keys: "const endpoint = report" },
}

for (const [group, { paths, keys }] of Object.entries(GROUPS)) {
  const api = new Set()
  for (const [path, operations] of Object.entries(spec.paths)) {
    if (!paths.test(path)) continue
    for (const operation of Object.values(operations)) {
      for (const parameter of operation.parameters ?? []) {
        if (parameter.in !== "query") continue
        if (/Pageable/.test(parameter.schema?.$ref ?? "")) ["page", "size", "sort"].forEach((key) => api.add(key))
        else api.add(parameter.name)
      }
    }
  }
  const at = server.indexOf(keys)
  const list = at < 0 ? null : server.slice(at, server.indexOf("])", at) + 1).match(/\[([^\]]*)\]\s*$/)
  const bff = new Set((list?.[1] ?? "").match(/"[^"]+"/g)?.map((key) => key.slice(1, -1)) ?? [])
  const dropped = [...api].filter((key) => !bff.has(key))
  const dead = [...bff].filter((key) => !api.has(key))
  console.log(`${group.padEnd(13)} api: ${[...api].sort().join(" ") || "-"}`)
  if (at < 0) console.log(`${"".padEnd(13)} ?? queryKeys not found; update GROUPS`)
  if (dropped.length) console.log(`${"".padEnd(13)} dropped by BFF: ${dropped.join(" ")}`)
  if (dead.length) console.log(`${"".padEnd(13)} no endpoint takes: ${dead.join(" ")}`)
}
