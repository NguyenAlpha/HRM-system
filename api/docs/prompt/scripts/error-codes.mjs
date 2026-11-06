// Usage (from the repo root): node api/docs/prompt/scripts/error-codes.mjs [repo root]
// ErrorCode values against the API code and the web's translations (ERROR_MESSAGES in apps/web/lib/api-helpers.ts):
//  - unused: no ErrorCode.X anywhere in api/src/main besides the enum (dead code; check the docs still listing it);
//  - untranslated: thrown by the API but the web has no Vietnamese message, so users see the fallback text;
//  - web only: translated by the web but not an ErrorCode (a BFF code, or a translation left behind).
import { readFileSync, readdirSync, statSync } from "node:fs"
import { join } from "node:path"

const root = process.argv[2] ?? "."
const main = join(root, "api/src/main/java/com/htttdn/hrm")
const enumFile = join(main, "dto/response/common/ErrorCode.java")

function walk(path, out = []) {
  for (const name of readdirSync(path)) {
    const full = join(path, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (name.endsWith(".java")) out.push(full)
  }
  return out
}

const enumText = readFileSync(enumFile, "utf8").replace(/\r\n/g, "\n")
// One constant per line, the last one without a trailing comma.
const codes = [...enumText.matchAll(/^\s+([A-Z][A-Z0-9_]+)\s*[,;(]?\s*(?:\/\/.*)?$/gm)].map((m) => m[1])
const source = walk(main).filter((file) => file !== enumFile).map((file) => readFileSync(file, "utf8")).join("\n")
const web = readFileSync(join(root, "apps/web/lib/api-helpers.ts"), "utf8")
const block = web.slice(web.indexOf("const ERROR_MESSAGES"), web.indexOf("\n}", web.indexOf("const ERROR_MESSAGES")))
const translated = new Set([...block.matchAll(/^\s+([A-Z][A-Z0-9_]+):/gm)].map((m) => m[1]))

const unused = codes.filter((code) => !new RegExp(`ErrorCode\\.${code}\\b`).test(source))
const untranslated = codes.filter((code) => !unused.includes(code) && !translated.has(code))
const webOnly = [...translated].filter((code) => !codes.includes(code))
console.log(`${codes.length} ErrorCode values, ${translated.size} web translations`)
console.log(`unused: ${unused.join(", ") || "none"}`)
console.log(`untranslated: ${untranslated.join(", ") || "none"}`)
console.log(`web only: ${webOnly.join(", ") || "none"}`)
