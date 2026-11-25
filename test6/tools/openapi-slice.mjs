// Génère les types TypeScript d'UNE brique à partir du contrat OpenAPI publié par le backend.
// Seuls les chemins de la brique (préfixe) et les schémas qu'ils utilisent sont gardés : un
// frontend ne voit ainsi que l'API de son propre backend (brique verticale).
//
//   node tools/openapi-slice.mjs <openapi.json> <préfixe> <sortie.ts>
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import openapiTS, { astToString } from 'openapi-typescript';

const [spec, prefix, out] = process.argv.slice(2);
if (!spec || !prefix || !out) {
  console.error('usage: openapi-slice.mjs <openapi.json> <préfixe> <sortie.ts>');
  process.exit(2);
}
const doc = JSON.parse(readFileSync(spec, 'utf8'));
const paths = Object.fromEntries(Object.entries(doc.paths).filter(([p]) => p === prefix || p.startsWith(`${prefix}/`)));
if (Object.keys(paths).length === 0) throw new Error(`Aucun chemin ${prefix} dans ${spec}`);

// Schémas atteignables depuis ces chemins (références transitives).
const keep = new Set();
const visit = (node) => {
  if (Array.isArray(node)) return node.forEach(visit);
  if (!node || typeof node !== 'object') return;
  const ref = node.$ref;
  if (typeof ref === 'string' && ref.startsWith('#/components/schemas/')) {
    const name = ref.slice('#/components/schemas/'.length);
    if (!keep.has(name)) { keep.add(name); visit(doc.components.schemas[name]); }
  }
  Object.values(node).forEach(visit);
};
visit(paths);
const schemas = Object.fromEntries([...keep].sort().map((n) => [n, doc.components.schemas[n]]));

const sliced = { ...doc, paths, components: { schemas } };
// Les DTO Kotlin ont tous des valeurs par défaut : la réponse contient toujours chaque propriété.
const ast = await openapiTS(sliced, { propertiesRequiredByDefault: true });
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, `// Généré par tools/openapi-slice.mjs depuis ${spec} (${prefix}). Ne pas modifier.\n${astToString(ast)}`);
console.log(`${out} : ${Object.keys(paths).length} chemins, ${keep.size} schémas`);
