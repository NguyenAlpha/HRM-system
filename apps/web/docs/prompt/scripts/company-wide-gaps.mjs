// Usage (from the repo root): node apps/web/docs/prompt/scripts/company-wide-gaps.mjs
// Permissions the API checks with requireCompanyWide(...) although PermissionSeeder does not mark them companyWide.
// A role holding one of them can be scoped narrower than COMPANY, so the web must not offer those actions on the
// permission code alone. Calls that pass a constant (requireCompanyWide(PAYROLL_CALCULATE)) are listed to resolve
// by hand.
import { readFileSync, readdirSync, statSync } from "node:fs"
import { join } from "node:path"

const api = "api/src/main/java/com/htttdn/hrm"
const seeder = readFileSync(join(api, "config/seed/PermissionSeeder.java"), "utf8")
const declarations = seeder.slice(seeder.indexOf("DEFAULT_PERMISSIONS = List.of("), seeder.indexOf("private final PermissionRepository"))
const companyWide = new Set()
for (const part of declarations.split(/(?=\b(?:permission|systemPermission)\()/)) {
  const code = part.match(/"([a-z_]+(?:\.[a-z_]+)+)"/)
  if (code && /companyWideOnly\(\)/.test(part)) companyWide.add(code[1])
}

function walk(path, out = []) {
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (name.endsWith(".java")) out.push(full)
  }
  return out
}

const gaps = new Map()
for (const file of walk(join(api, "service"))) {
  readFileSync(file, "utf8").split("\n").forEach((line, index) => {
    const m = line.match(/requireCompanyWide\((?:"([^"]+)"|([A-Z_]+))\)/)
    if (!m) return
    const where = `${file}:${index + 1}`
    if (m[2]) console.log(`?  ${where} ${m[2]} (constant, resolve by hand)`)
    else if (!companyWide.has(m[1])) gaps.set(m[1], [...(gaps.get(m[1]) ?? []), where])
  })
}
console.log(`${companyWide.size} companyWide permissions in PermissionSeeder`)
for (const [code, places] of gaps) console.log(`${code}: ${places.length} call(s), e.g. ${places[0]}`)
if (!gaps.size) console.log("Every requireCompanyWide permission is companyWide")
