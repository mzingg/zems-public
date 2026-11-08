// npm audit with an allowlist: the frontend counterpart of owasp-suppressions.xml in parent_java.
//
// Runs `npm audit --json` in the current module and fails on any advisory at or above the given level, except those
// whose GitHub advisory (GHSA) ID is listed in npm-audit-allowlist.json next to this script. npm has no native way to
// ignore one advisory, and a third-party audit wrapper would add a dependency for a short filter.
//
// Usage (from the module directory, as the npm_audit execution in pom.xml runs it):
//   npm exec -- node <repo>/.mvn/parent_nodejs/npm-audit.mjs --audit-level=moderate

import {spawnSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

const SEVERITIES = ['info', 'low', 'moderate', 'high', 'critical'];

const levelArg = process.argv.find((a) => a.startsWith('--audit-level='));
const level = levelArg ? levelArg.split('=')[1] : 'moderate';
if (!SEVERITIES.includes(level)) {
  console.error(`npm-audit: unknown audit level '${level}'`);
  process.exit(2);
}

const allowlist = JSON.parse(readFileSync(new URL('npm-audit-allowlist.json', import.meta.url), 'utf8')).advisories;
const allowed = new Set(allowlist.map((a) => a.id));

// Under `npm exec`, npm_execpath points at the npm that started us; fall back to npm on PATH.
const npm = process.env.npm_execpath
  ? [process.execPath, [process.env.npm_execpath, 'audit', '--json']]
  : ['npm', ['audit', '--json']];
const run = spawnSync(npm[0], npm[1], {encoding: 'utf8', maxBuffer: 64 * 1024 * 1024});
let report;
try {
  report = JSON.parse(run.stdout);
} catch {
  console.error(`npm-audit: npm audit produced no JSON report (exit ${run.status})\n${run.stderr}`);
  process.exit(2);
}
if (report.error) {
  console.error(`npm-audit: npm audit failed: ${report.error.summary ?? JSON.stringify(report.error)}`);
  process.exit(2);
}

// Only object entries in `via` are advisories; string entries just name the vulnerable dependency they come through.
const advisories = new Map();
for (const vuln of Object.values(report.vulnerabilities ?? {})) {
  for (const via of vuln.via) {
    if (typeof via === 'object') {
      const id = via.url.split('/').pop();
      advisories.set(id, {id, name: via.name, severity: via.severity, title: via.title, url: via.url});
    }
  }
}

const relevant = [...advisories.values()].filter((a) => SEVERITIES.indexOf(a.severity) >= SEVERITIES.indexOf(level));
const ignored = relevant.filter((a) => allowed.has(a.id));
const failing = relevant.filter((a) => !allowed.has(a.id));

const line = (a) => `  ${a.severity.padEnd(8)} ${a.name}  ${a.id}  ${a.title}`;
if (ignored.length > 0) {
  console.log(`npm-audit: ${ignored.length} allowlisted advisories (see npm-audit-allowlist.json):`);
  ignored.forEach((a) => console.log(line(a)));
}
if (failing.length > 0) {
  console.error(`npm-audit: ${failing.length} advisories at level '${level}' or above:`);
  failing.forEach((a) => console.error(`${line(a)}\n           ${a.url}`));
  process.exit(1);
}
console.log(`npm-audit: no advisories at level '${level}' or above outside the allowlist`);
