#!/usr/bin/env node
// Generates the Android colour outputs of design/fw.tokens.json (FwColors.kt and fw_colors.xml).
//
//   node scripts/design/gen-fw-tokens.mjs --android <checkout>          write both files
//   node scripts/design/gen-fw-tokens.mjs --android <checkout> --check  fail when either file is stale
//
// The token file is the only source. Never edit the generated files by hand.
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const TOKENS = 'design/fw.tokens.json';
export const KOTLIN = 'common/src/main/java/jp/co/soramitsu/common/compose/theme/fw/FwColors.kt';
export const XML = 'common/src/main/res/values/fw_colors.xml';

/** 0..1 alpha to the 8-bit channel Compose and Android resources store. */
export function alphaByte(alpha) {
  if (typeof alpha !== 'number' || !(alpha >= 0 && alpha <= 1)) throw new Error(`Invalid alpha ${alpha}`);
  return Math.round(alpha * 255);
}

/** A colour token value to AARRGGBB, upper case. */
export function argb(value) {
  const hex = /^#([0-9A-Fa-f]{6})$/.exec(value?.hex ?? '');
  if (!hex) throw new Error(`Invalid colour ${JSON.stringify(value)}`);
  const alpha = alphaByte(value.alpha ?? 1).toString(16).padStart(2, '0');
  return (alpha + hex[1]).toUpperCase();
}

/** Kotlin float literal without a trailing zero: 0 -> 0f, 0.42 -> 0.42f, 1 -> 1f. */
export function kotlinFloat(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error(`Invalid number ${value}`);
  return `${value}f`;
}

export function resourceName(name) {
  return 'fw_' + name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
}

function entries(group) {
  return Object.entries(group ?? {}).filter(([name]) => !name.startsWith('$'));
}

function brush(name, token) {
  const stops = token.$value;
  const angle = token.$extensions?.fw?.angle;
  if (!Array.isArray(stops) || stops.length < 2) throw new Error(`Gradient ${name} needs two stops`);
  const colours = stops.map((stop) => `Color(0x${argb(stop.color)})`);
  const simple = stops.length === 2 && stops[0].position === 0 && stops[1].position === 1;
  const pairs = stops.map((stop, index) => `${kotlinFloat(stop.position)} to ${colours[index]}`).join(', ');
  if (angle === 180) {
    return simple ? `Brush.verticalGradient(listOf(${colours.join(', ')}))` : `Brush.verticalGradient(${pairs})`;
  }
  if (angle === 135) return `Brush.linearGradient(${pairs})`;
  throw new Error(`Gradient ${name}: unsupported angle ${angle}`);
}

export function generateAndroid(source) {
  const sha = createHash('sha256').update(source).digest('hex');
  const tokens = JSON.parse(source.toString('utf8'));
  const semantic = entries(tokens.color.semantic);
  const highContrast = entries(tokens.color.highContrast);
  for (const [name] of highContrast) {
    if (!semantic.some(([candidate]) => candidate === name)) throw new Error(`High-contrast ${name} has no semantic token`);
  }
  const containerDefault = tokens.color.statusContainerAlpha.default.$value;
  const containerInfo = tokens.color.statusContainerAlpha.info.$value;
  const gradients = entries(tokens.gradient).filter(([, token]) => token.$extensions?.fw?.android);

  const kotlin = [
    `// FwColors.kt: GENERATED from ${TOKENS}; do not edit.`,
    `// sha256 ${sha}`,
    '// Regenerate: node scripts/design/gen-fw-tokens.mjs --android <checkout>',
    'package jp.co.soramitsu.common.compose.theme.fw',
    '',
    'import androidx.compose.runtime.Immutable',
    'import androidx.compose.ui.graphics.Brush',
    'import androidx.compose.ui.graphics.Color',
    '',
    '@Immutable',
    'data class FwColors(',
    ...semantic.map(([name]) => `    val ${name}: Color,`),
    ')',
    '',
    'val FwStandard = FwColors(',
    ...semantic.map(([name, token]) => `    ${name} = Color(0x${argb(token.$value)}),`),
    ')',
    '',
    'val FwHighContrast = FwStandard.copy(',
    ...highContrast.map(([name, token]) => `    ${name} = Color(0x${argb(token.$value)}),`),
    ')',
    '',
    `/** status.*.container: the status colour at ${Math.round(containerDefault * 100)} % over the current surface (info: FW_INFO_CONTAINER_ALPHA). */`,
    `fun Color.container(alpha: Float = ${kotlinFloat(containerDefault)}) = copy(alpha = alpha)`,
    '',
    `const val FW_INFO_CONTAINER_ALPHA = ${kotlinFloat(containerInfo)}`,
    '',
    'object FwBrush {',
    ...gradients.map(([name, token]) => `    val ${token.$extensions.fw.android} = ${brush(name, token)}`),
    '}',
    ''
  ].join('\n');

  const xml = [
    '<?xml version="1.0" encoding="utf-8"?>',
    `<!-- fw_colors.xml: GENERATED from ${TOKENS}; do not edit. -->`,
    `<!-- sha256 ${sha} -->`,
    '<!-- Regenerate with the Android target of scripts/design/gen-fw-tokens.mjs. -->',
    '<resources>',
    ...semantic.map(([name, token]) => `    <color name="${resourceName(name)}">#${argb(token.$value)}</color>`),
    '</resources>',
    ''
  ].join('\n');
  return { kotlin, xml };
}

function main(argv) {
  const index = argv.indexOf('--android');
  if (index < 0 || !argv[index + 1]) {
    console.error('usage: gen-fw-tokens.mjs --android <checkout> [--check]');
    return 2;
  }
  const root = resolve(argv[index + 1]);
  const { kotlin, xml } = generateAndroid(readFileSync(join(root, TOKENS)));
  const outputs = [[KOTLIN, kotlin], [XML, xml]];
  if (argv.includes('--check')) {
    const stale = outputs.filter(([path, text]) => readFileSync(join(root, path), 'utf8') !== text).map(([path]) => path);
    if (stale.length) {
      console.error(`Stale generated token files: ${stale.join(', ')}`);
      return 1;
    }
    console.log('Android token outputs are current');
    return 0;
  }
  for (const [path, text] of outputs) writeFileSync(join(root, path), text);
  console.log(`Wrote ${outputs.map(([path]) => path).join(', ')}`);
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
