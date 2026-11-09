import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

// Built in a temp directory instead of committed as files: promote.sh greps every pom.xml for the public marker, so a
// committed fixture POM would be promoted as a module.

export const MARKED_POM =
  '<project>\n  <properties>\n    <zems.public>true</zems.public>\n  </properties>\n</project>\n';
export const UNMARKED_POM = '<project>\n  <properties>\n  </properties>\n</project>\n';

export const SUITE_README = `# ZEMS

Small, focused libraries for the \`fixture\` suite.

| Library                           | Description         |
| --------------------------------- | ------------------- |
| [alpha](10_lib/alpha/README.md)   | The alpha library.  |

- [Guide](docs/guide.md) — how to use the suite.
`;

export const SUITE_GUIDE = `# Guide

The suite guide, see [Alpha][alpha] and [the overview](../README.md).

[alpha]: ../10_lib/alpha/README.md
`;

export const ALPHA_README = `# ZEMS Alpha

ZEMS Alpha does the alpha thing.

Read the [documentation](docs/README.md).
`;

export const ALPHA_DOCS_README = `# ZEMS Alpha Documentation

Start with [setup](setup.md), then read the [diagram](diagram.md#a-heading).

Back to [setup](setup.md#setup-heading) once more, or visit [the site](https://zems.dev).

\`\`\`md
[not a link](missing.md)
\`\`\`
`;

export const ALPHA_SETUP = `# Setup

Add the dependency.

## Setup heading

\`\`\`xml
<dependency>
  <groupId>dev.zems.lib</groupId>
  <artifactId>alpha</artifactId>
  <version>1.2.3</version>
</dependency>
\`\`\`
`;

export const ALPHA_DIAGRAM = `# Diagram

How it flows.

\`\`\`mermaid
flowchart LR
  A --> B
\`\`\`

## A heading

Text under the heading, see [itself](#a-heading).
`;

export const ALPHA_UNLINKED = `# Unlinked

Nobody links here.
`;

export const ESSAY_README = `# Essay

A multi-page essay, read [the second page](second.md) first, then [the first](first.md).
`;

export const ESSAY_FIRST = `# First

A stage card with a link into the suite.

\`\`\`stage
Purpose: show a stage card
Who: the reader; see the [guide](../guide.md)
Steps:
1. read the \`card\`
2. follow the link
Way back: none
\`\`\`
`;

export const ESSAY_SECOND = `# Second

A journey ladder.

\`\`\`gates
Idea
-- Jot Gate | none | none
Backlog
-- Specification Gate | work item linter | the owner accepts
Open
\`\`\`
`;

export const BETA_README = `# ZEMS Beta

ZEMS Beta is not published.
`;

export type FixtureRepo = {root: string; write: (relativePath: string, content: string) => void; remove: () => void};

export function createFixtureRepo(): FixtureRepo {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'zems-docs-'));
  const write = (relativePath: string, content: string) => {
    const file = path.join(root, relativePath);
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, content);
  };
  write('README.md', SUITE_README);
  write('docs/guide.md', SUITE_GUIDE);
  write('docs/essay/README.md', ESSAY_README);
  write('docs/essay/first.md', ESSAY_FIRST);
  write('docs/essay/second.md', ESSAY_SECOND);
  write('10_lib/alpha/pom.xml', MARKED_POM);
  write('10_lib/alpha/CLAUDE.md', '# Alpha internals\n');
  write('10_lib/alpha/README.md', ALPHA_README);
  write('10_lib/alpha/docs/README.md', ALPHA_DOCS_README);
  write('10_lib/alpha/docs/setup.md', ALPHA_SETUP);
  write('10_lib/alpha/docs/diagram.md', ALPHA_DIAGRAM);
  write('10_lib/alpha/docs/unlinked.md', ALPHA_UNLINKED);
  write('10_lib/beta/pom.xml', UNMARKED_POM);
  write('10_lib/beta/README.md', BETA_README);
  write('10_lib/beta/docs/hidden.md', '# Hidden\n\nNot published.\n');
  return {root, write, remove: () => fs.rmSync(root, {recursive: true, force: true})};
}
