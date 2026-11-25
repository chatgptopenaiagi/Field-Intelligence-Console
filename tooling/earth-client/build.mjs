import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
const dir = fileURLToPath(new URL('.', import.meta.url));
await build({
  absWorkingDir: dir, entryPoints: ['engine.js'], bundle: true, format: 'iife', globalName: 'FicEarth',
  target: 'es2020', minify: true, legalComments: 'inline', loader: {'.jpg':'dataurl'},
  footer: {js:"if(typeof module==='object' && module.exports) module.exports=FicEarth;"},
  outfile: '../../python/src/field_intelligence_console/earth_client/core.js'
});
