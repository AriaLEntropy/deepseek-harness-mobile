// Generates DshKatexAssets.kt from a KaTeX dist directory.
//
// Usage:
//   npm pack katex && tar -xzf katex-*.tgz
//   node tools/gen_katex_assets.js <katex-dist-dir> <output-kt-path>
//
// The generated Kotlin file embeds katex.min.js and katex.min.css (woff2 fonts
// inlined as base64) as chunked string constants, so the WebView can be built
// with htmlContent() without any network or file access.

const fs = require('fs');
const path = require('path');

const dist = process.argv[2];
const out = process.argv[3];
if (!dist || !out) {
  console.error('usage: node gen_katex_assets.js <katex-dist-dir> <output-kt-path>');
  process.exit(1);
}

const js = fs.readFileSync(path.join(dist, 'katex.min.js'), 'utf8');
let css = fs.readFileSync(path.join(dist, 'katex.min.css'), 'utf8');

const fontsDir = path.join(dist, 'fonts');
const fontB64 = {};
for (const f of fs.readdirSync(fontsDir)) {
  if (f.endsWith('.woff2')) fontB64[f] = fs.readFileSync(path.join(fontsDir, f)).toString('base64');
}
css = css.replace(/url\(fonts\/([^)]+\.woff2)\)/g, (m, name) => {
  const b64 = fontB64[name];
  if (!b64) throw new Error('missing font ' + name);
  return `url(data:font/woff2;base64,${b64})`;
});
css = css.replace(/,url\(fonts\/[^)]+\.woff\) format\("woff"\)/g, '');
css = css.replace(/,url\(fonts\/[^)]+\.ttf\) format\("truetype"\)/g, '');

if (js.includes('</script')) throw new Error('js contains </script');
if (css.includes('</style')) throw new Error('css contains </style');

function esc(s) {
  return s
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\$/g, '\\$')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t');
}

const CHUNK = 8000;
function chunks(name, s) {
  const parts = [];
  for (let i = 0; i < s.length; i += CHUNK) parts.push(s.slice(i, i + CHUNK));
  return parts.map((p, i) => `    private const val ${name}${i} = "${esc(p)}"`).join('\n');
}
function appendAll(name, n) {
  return Array.from({ length: n }, (_, i) => `        append(${name}${i})`).join('\n');
}

const jsChunks = Math.ceil(js.length / CHUNK);
const cssChunks = Math.ceil(css.length / CHUNK);

const content = `package com.example.dsh.rendering

// GENERATED FILE - do not edit by hand. Regenerate with tools/gen_katex_assets.js.
// Source: katex@0.18.7 dist/katex.min.js + katex.min.css (woff2 fonts inlined as base64).

internal object DshKatexAssets {

    val js: String by lazy {
        buildString(${js.length}) {
${appendAll('JS', jsChunks)}
        }
    }

    val css: String by lazy {
        buildString(${css.length}) {
${appendAll('CSS', cssChunks)}
        }
    }

${chunks('JS', js)}

${chunks('CSS', css)}
}
`;

fs.writeFileSync(out, content, 'utf8');
console.log('wrote', out, 'js', js.length, 'css', css.length, 'jsChunks', jsChunks, 'cssChunks', cssChunks);
