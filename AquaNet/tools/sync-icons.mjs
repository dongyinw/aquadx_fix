// Regenerates src/libs/icons/collections.json from the icons referenced in src/.
// Icon sets are downloaded from the sources listed in src/libs/icons/licenses.json.
//
//   node tools/sync-icons.mjs
//
// Icon names must appear as plain string literals ("prefix:name") to be detected.
import { readFileSync, writeFileSync, readdirSync, statSync, mkdirSync, existsSync } from "node:fs"
import { join, extname, dirname } from "node:path"
import { fileURLToPath } from "node:url"

const root = join(dirname(fileURLToPath(import.meta.url)), "..")
const iconsDir = join(root, "src/libs/icons")
const cacheDir = join(root, "node_modules/.cache/iconify")
const licenses = JSON.parse(readFileSync(join(iconsDir, "licenses.json"), "utf8"))

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) walk(path, out)
    else if ([".svelte", ".ts", ".js"].includes(extname(path))) out.push(path)
  }
  return out
}

// Collect "prefix:name" literals for known icon sets.
const used = new Map()
const pattern = /["'`]([a-z0-9]+(?:-[a-z0-9]+)*):([a-z0-9]+(?:-[a-z0-9]+)*)["'`]/g
for (const file of walk(join(root, "src"))) {
  for (const [, prefix, name] of readFileSync(file, "utf8").matchAll(pattern)) {
    if (!licenses[prefix]) continue
    if (!used.has(prefix)) used.set(prefix, new Set())
    used.get(prefix).add(name)
  }
}

async function loadSet(prefix) {
  mkdirSync(cacheDir, { recursive: true })
  const cached = join(cacheDir, `${prefix}.json`)
  if (existsSync(cached)) return JSON.parse(readFileSync(cached, "utf8"))
  const response = await fetch(licenses[prefix].source)
  if (!response.ok) throw new Error(`Failed to download ${prefix}: ${response.status}`)
  const text = await response.text()
  writeFileSync(cached, text)
  return JSON.parse(text)
}

const collections = []
let missing = 0
for (const prefix of [...used.keys()].sort()) {
  const set = await loadSet(prefix)
  const icons = {}, aliases = {}
  for (const name of [...used.get(prefix)].sort()) {
    // Resolve alias chains down to a real icon.
    let current = name
    while (set.aliases?.[current] && !set.icons[current]) {
      aliases[current] = set.aliases[current]
      current = set.aliases[current].parent
    }
    if (set.icons[current]) icons[current] = set.icons[current]
    else {
      missing++
      console.error(`Missing icon: ${prefix}:${name}`)
    }
  }
  collections.push({
    prefix, info: set.info, lastModified: set.lastModified,
    width: set.width, height: set.height, icons, aliases,
  })
  console.log(`${prefix}: ${Object.keys(icons).length} icons`)
}

writeFileSync(join(iconsDir, "collections.json"), JSON.stringify(collections))
if (missing) process.exitCode = 1
