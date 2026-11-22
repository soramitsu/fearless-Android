import { pathToFileURL, fileURLToPath } from 'node:url';
import { existsSync } from 'node:fs';
import path from 'node:path';
const root = path.dirname(fileURLToPath(import.meta.url));
const map = (specifier) => {
  if (specifier.startsWith('@/')) return path.join(root, 'src', specifier.slice(2));
  if (specifier.startsWith('@extension-base/')) {
    const rest = specifier.slice('@extension-base/'.length);
    if (rest.startsWith('services/iroha-connect-service')) return path.join(root, 'ext/svc', rest.slice('services/iroha-connect-service'.length) || 'index');
    return path.join(root, 'ext/stubs', rest);
  }
  return null;
};
const withTs = (file) => {
  for (const candidate of [file, `${file}.ts`, path.join(file, 'index.ts')]) if (existsSync(candidate) && !candidate.endsWith(path.sep)) {
    try { if (!candidate.endsWith('.ts') && !candidate.endsWith('.js') && !candidate.endsWith('.mjs')) continue; } catch {}
    return candidate;
  }
  return null;
};
export async function resolve(specifier, context, next) {
  if ((specifier === './native.js' || specifier === '../native.js') && context.parentURL?.includes('/@iroha/iroha-js/dist/')) {
    return { url: pathToFileURL(path.join(root, 'src/util/irohaSdkBrowserBinding.ts')).href, shortCircuit: true };
  }
  const mapped = map(specifier);
  if (mapped) {
    const file = withTs(mapped);
    if (!file) throw new Error(`harness: cannot resolve ${specifier} -> ${mapped}`);
    return { url: pathToFileURL(file).href, shortCircuit: true };
  }
  if ((specifier.startsWith('./') || specifier.startsWith('../')) && context.parentURL?.startsWith('file:')) {
    const base = path.dirname(fileURLToPath(context.parentURL));
    const file = withTs(path.resolve(base, specifier));
    if (file) return { url: pathToFileURL(file).href, shortCircuit: true };
  }
  return next(specifier, context);
}
import { readFileSync } from 'node:fs';
import { transformSync } from 'esbuild';
export async function load(url, context, next) {
  if (url.startsWith('file:') && url.endsWith('.ts')) {
    const source = readFileSync(fileURLToPath(url), 'utf8');
    const { code } = transformSync(source, { loader: 'ts', format: 'esm', target: 'es2022', sourcefile: fileURLToPath(url) });
    return { format: 'module', source: code, shortCircuit: true };
  }
  return next(url, context);
}
