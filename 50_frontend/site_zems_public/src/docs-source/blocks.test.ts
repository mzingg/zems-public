/** @jest-environment node */
import rehypeStringify from 'rehype-stringify';
import remarkParse from 'remark-parse';
import remarkRehype from 'remark-rehype';
import {unified} from 'unified';
import {expandBlocks, parseJourney, parseStage} from '@/docs-source/blocks';

const WHERE = 'docs/page.md';

function toHtml(markdown: string): string {
  const processor = unified().use(remarkParse).use(remarkRehype).use(rehypeStringify);
  const tree = processor.parse(markdown);
  expandBlocks(tree, WHERE);
  return processor.stringify(processor.runSync(tree));
}

describe('parseStage', () => {
  it('reads fields, continuation lines and numbered steps', () => {
    const fields = parseStage('Purpose: one\n  sentence\nSteps:\n1. first\n2. second\nGate: none\n', WHERE);

    expect(fields).toEqual([
      {name: 'Purpose', text: 'one sentence', steps: []},
      {name: 'Steps', text: '', steps: ['first', 'second']},
      {name: 'Gate', text: 'none', steps: []},
    ]);
  });

  it('keeps a colon inside a field value', () => {
    expect(parseStage('Who: Engineer: the one who runs it\n', WHERE)).toEqual([
      {name: 'Who', text: 'Engineer: the one who runs it', steps: []},
    ]);
  });

  it('rejects a block that does not start with a field line', () => {
    expect(() => parseStage('just text\nPurpose: x\n', WHERE)).toThrow(
      `${WHERE}: a stage block starts with a "Field: text" line, not 'just text'`
    );
  });

  it('rejects an empty block', () => {
    expect(() => parseStage('\n\n', WHERE)).toThrow(`${WHERE}: a stage block holds no "Field: text" line`);
  });
});

describe('parseJourney', () => {
  it('reads alternating states and gates', () => {
    const journey = parseJourney(
      'Idea\n-- Jot Gate | none | none\nBacklog\n-- Spec Gate | linter | owner accepts\nOpen\n',
      WHERE
    );

    expect(journey).toEqual({
      states: ['Idea', 'Backlog', 'Open'],
      gates: [
        {name: 'Jot Gate', checks: 'none', decision: 'none'},
        {name: 'Spec Gate', checks: 'linter', decision: 'owner accepts'},
      ],
    });
  });

  it.each([
    ['-- Gate | a | b\nIdea\n', 'a gate line needs a state before it'],
    ['Idea\nBacklog\n', 'two states in a row need a'],
    ['Idea\n-- Gate | a\nBacklog\n', 'a gate line is "-- gate | checks | human decision", not'],
    ['Idea\n-- Gate | a | b\n', 'a gates block starts and ends with a state and holds at least one gate'],
    ['Idea\n', 'a gates block starts and ends with a state and holds at least one gate'],
  ])('rejects %p', (text, message) => {
    expect(() => parseJourney(text, WHERE)).toThrow(`${WHERE}: ${message}`);
  });
});

describe('expandBlocks', () => {
  it('turns a stage fence into a definition list with inline markdown in the values', () => {
    const html = toHtml('```stage\nPurpose: keep an *idea*\nSteps:\n1. write `it`\n```\n');

    expect(html).toBe(
      '<dl class="stage"><div class="stage-field"><dt>Purpose</dt><dd>keep an <em>idea</em></dd></div>' +
        '<div class="stage-field"><dt>Steps</dt><dd><ol>\n<li>write <code>it</code></li>\n</ol></dd></div></dl>'
    );
  });

  it('turns a gates fence into a ladder that carries the journey as data', () => {
    const html = toHtml('```gates\nIdea\n-- Jot Gate | none | none\nBacklog\n```\n');

    expect(html).toContain(
      '<ol class="journey" data-journey="{&#x22;states&#x22;:[&#x22;Idea&#x22;,&#x22;Backlog&#x22;]'
    );
    expect(html).toContain(
      '<li class="journey-state">Idea</li><li class="journey-gate"><strong>Jot Gate</strong>' +
        '<dl><dt>Checks</dt><dd>none</dd><dt>Human decision</dt><dd>none</dd></dl></li><li class="journey-state">Backlog</li></ol>'
    );
  });

  it('leaves every other fence and the surrounding markdown untouched', () => {
    const markdown = 'Text.\n\n```java\nint x = 1;\n```\n\n```mermaid\nflowchart LR\n```\n';

    expect(toHtml(markdown)).toBe(
      String(unified().use(remarkParse).use(remarkRehype).use(rehypeStringify).processSync(markdown))
    );
  });

  it('expands two blocks in one document', () => {
    const html = toHtml('```stage\nPurpose: a\n```\n\n```stage\nPurpose: b\n```\n');

    expect(html.match(/<dl class="stage">/g)).toHaveLength(2);
  });
});
