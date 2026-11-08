import type {Config} from 'jest';
import nextJest from 'next/jest.js';

const createJestConfig = nextJest({
  dir: './',
});

const config: Config = {
  collectCoverage: true,
  coverageDirectory: 'target/jest-coverage',
  coverageProvider: 'v8',
  testEnvironment: 'jsdom',
  testMatch: ['**/?(*.)+(test).[tj]s?(x)'],
};

// The markdown stack (unified, remark, rehype and their helpers) ships ESM only; next/jest leaves node_modules
// untransformed, so these packages must be let through or jest fails on their `import` statements.
const ESM_PACKAGES = [
  'unified',
  'bail',
  'is-plain-obj',
  'trough',
  'vfile.*',
  'unist-.*',
  'mdast-.*',
  'micromark.*',
  'remark-.*',
  'rehype-.*',
  'hast-.*',
  'hastscript',
  'property-information',
  'space-separated-tokens',
  'comma-separated-tokens',
  'web-namespaces',
  'zwitch',
  'html-void-elements',
  'ccount',
  'character-entities.*',
  'decode-named-character-reference',
  'devlop',
  'escape-string-regexp',
  'markdown-table',
  'longest-streak',
  'trim-lines',
  'github-slugger',
  'stringify-entities',
  'html-url-attributes',
];

export default async function jestConfig(): Promise<Config> {
  return {
    ...(await createJestConfig(config)()),
    transformIgnorePatterns: [`/node_modules/(?!(${ESM_PACKAGES.join('|')})/)`],
  };
}
