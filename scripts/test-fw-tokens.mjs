#!/usr/bin/env node
// Self-test of scripts/design/gen-fw-tokens.mjs: the committed Android outputs are current and the
// conversion rules (alpha rounding, resource names, gradient forms, fail-closed inputs) hold.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { KOTLIN, TOKENS, XML, alphaByte, argb, generateAndroid, kotlinFloat, resourceName } from './design/gen-fw-tokens.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(join(root, TOKENS));
const { kotlin, xml } = generateAndroid(source);
assert.equal(readFileSync(join(root, KOTLIN), 'utf8'), kotlin, 'FwColors.kt is stale: run gen-fw-tokens.mjs --android .');
assert.equal(readFileSync(join(root, XML), 'utf8'), xml, 'fw_colors.xml is stale: run gen-fw-tokens.mjs --android .');

assert.equal(alphaByte(0.72), 184);
assert.equal(alphaByte(0.045), 11);
assert.equal(alphaByte(0), 0);
assert.equal(alphaByte(1), 255);
assert.throws(() => alphaByte(1.2));
assert.equal(argb({ hex: '#151831' }), 'FF151831');
assert.equal(argb({ hex: '#ee0077', alpha: 0.18 }), '2EEE0077');
assert.throws(() => argb({ hex: '151831' }));
assert.equal(kotlinFloat(0), '0f');
assert.equal(kotlinFloat(0.45), '0.45f');
assert.equal(resourceName('surfaceRaised2'), 'fw_surface_raised2');
assert.equal(resourceName('textOnAccent'), 'fw_text_on_accent');

// Every semantic colour reaches both outputs exactly once, in source order.
const tokens = JSON.parse(source.toString('utf8'));
const names = Object.keys(tokens.color.semantic).filter((name) => !name.startsWith('$'));
for (const name of names) {
  assert.equal(kotlin.split(`\n    val ${name}: Color,`).length, 2, name);
  assert.equal(xml.split(`<color name="${resourceName(name)}">`).length, 2, name);
}
assert.ok(kotlin.includes('val Rim = Brush.verticalGradient('), 'rim gradient');
assert.ok(kotlin.includes('val PrimaryFill = Brush.linearGradient('), 'primary fill gradient');

function mutated(edit) {
  const copy = JSON.parse(source.toString('utf8'));
  edit(copy);
  return Buffer.from(JSON.stringify(copy));
}
assert.throws(() => generateAndroid(mutated((t) => { t.gradient.rim.$extensions.fw.angle = 90; })), /unsupported angle/);
assert.throws(() => generateAndroid(mutated((t) => { t.color.highContrast.unknownToken = t.color.highContrast.textSecondary; })), /no semantic token/);
assert.throws(() => generateAndroid(mutated((t) => { t.color.semantic.bgBase.$value.hex = '#12345'; })), /Invalid colour/);
// The header digest binds the outputs to the exact token bytes.
assert.notEqual(generateAndroid(Buffer.concat([source, Buffer.from(' ')])).kotlin, kotlin);
console.log(`gen-fw-tokens self-test passed (${names.length} semantic colours)`);
