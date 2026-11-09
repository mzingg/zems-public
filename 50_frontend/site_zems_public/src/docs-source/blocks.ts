import type {List, PhrasingContent, Root, RootContent} from 'mdast';
import {SKIP, visit} from 'unist-util-visit';
import {parseMarkdown} from './links.ts';

export type StageField = {name: string; text: string; steps: string[]};
export type Gate = {name: string; checks: string; decision: string};
export type Journey = {states: string[]; gates: Gate[]};

const FIELD = /^([A-Z][A-Za-z ]*?):(?:\s+(.*))?$/;
const STEP = /^\d+\.\s+(.*)$/;
const GATE = /^--\s+(.*)$/;
const GATE_FORM = '"-- gate | checks | human decision"';

function nonEmptyLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trimEnd())
    .filter((line) => line.trim() !== '');
}

/**
 * Parses a `stage` block: `Field: text` lines, each followed by its continuation lines or `1.` numbered steps.
 *
 * @throws Error when the block does not start with a field line or holds none
 */
export function parseStage(text: string, where: string): StageField[] {
  const fields: StageField[] = [];
  for (const line of nonEmptyLines(text)) {
    const field = FIELD.exec(line);
    if (field) {
      fields.push({name: field[1], text: field[2] ?? '', steps: []});
      continue;
    }
    const current = fields.at(-1);
    if (!current) throw new Error(`${where}: a stage block starts with a "Field: text" line, not '${line}'`);
    const step = STEP.exec(line.trim());
    if (step) current.steps.push(step[1]);
    else current.text = `${current.text} ${line.trim()}`.trim();
  }
  if (fields.length === 0) throw new Error(`${where}: a stage block holds no "Field: text" line`);
  return fields;
}

/**
 * Parses a `gates` block: a state line, then alternately a `-- gate | checks | human decision` line and the next state.
 *
 * @throws Error when the lines do not alternate, a gate line lacks its three parts, or the block ends on a gate
 */
export function parseJourney(text: string, where: string): Journey {
  const states: string[] = [];
  const gates: Gate[] = [];
  for (const line of nonEmptyLines(text).map((raw) => raw.trim())) {
    const gate = GATE.exec(line);
    if (gate) {
      if (states.length !== gates.length + 1)
        throw new Error(`${where}: a gate line needs a state before it: '${line}'`);
      const parts = gate[1].split('|').map((part) => part.trim());
      if (parts.length !== 3) throw new Error(`${where}: a gate line is ${GATE_FORM}, not '${line}'`);
      gates.push({name: parts[0], checks: parts[1], decision: parts[2]});
    } else {
      if (states.length !== gates.length)
        throw new Error(`${where}: two states in a row need a ${GATE_FORM} line between`);
      states.push(line);
    }
  }
  if (states.length < 2 || states.length !== gates.length + 1) {
    throw new Error(`${where}: a gates block starts and ends with a state and holds at least one gate`);
  }
  return {states, gates};
}

type Hast = {hName: string; hProperties?: Record<string, unknown>};

function element(data: Hast, children: unknown[]): RootContent {
  return {type: 'block', data, children} as unknown as RootContent;
}

function text(value: string): PhrasingContent {
  return {type: 'text', value};
}

function inline(markdown: string): PhrasingContent[] {
  const first = parseMarkdown(markdown).children[0];
  return first?.type === 'paragraph' ? first.children : [text(markdown)];
}

function orderedList(items: string[]): List {
  return {
    type: 'list',
    ordered: true,
    spread: false,
    children: items.map((item) => ({
      type: 'listItem',
      spread: false,
      children: [{type: 'paragraph', children: inline(item)}],
    })),
  };
}

function stageNode(fields: StageField[]): RootContent {
  const field = ({name, text: value, steps}: StageField) =>
    element({hName: 'div', hProperties: {className: ['stage-field']}}, [
      element({hName: 'dt'}, [text(name)]),
      element({hName: 'dd'}, [...(value ? inline(value) : []), ...(steps.length ? [orderedList(steps)] : [])]),
    ]);
  return element({hName: 'dl', hProperties: {className: ['stage']}}, fields.map(field));
}

function gateNode({name, checks, decision}: Gate): RootContent {
  return element({hName: 'li', hProperties: {className: ['journey-gate']}}, [
    element({hName: 'strong'}, [text(name)]),
    element({hName: 'dl'}, [
      element({hName: 'dt'}, [text('Checks')]),
      element({hName: 'dd'}, [text(checks)]),
      element({hName: 'dt'}, [text('Human decision')]),
      element({hName: 'dd'}, [text(decision)]),
    ]),
  ]);
}

function journeyNode(journey: Journey): RootContent {
  const items = journey.states.flatMap((state, index) => {
    const gate = journey.gates[index];
    return [
      element({hName: 'li', hProperties: {className: ['journey-state']}}, [text(state)]),
      ...(gate ? [gateNode(gate)] : []),
    ];
  });
  return element({hName: 'ol', hProperties: {className: ['journey'], dataJourney: JSON.stringify(journey)}}, items);
}

/**
 * Replaces every `stage` fence by a definition list and every `gates` fence by a journey ladder, in place. Runs before
 * the links are resolved, so a link inside a stage field is checked and pointed at its page like any other link.
 *
 * @throws Error when a block does not follow its form; `where` names the source in the message
 */
export function expandBlocks(tree: Root, where: string): void {
  visit(tree, 'code', (node, index, parent) => {
    if (!parent || index === undefined) return;
    let replacement: RootContent;
    if (node.lang === 'stage') replacement = stageNode(parseStage(node.value, where));
    else if (node.lang === 'gates') replacement = journeyNode(parseJourney(node.value, where));
    else return;
    (parent.children as RootContent[]).splice(index, 1, replacement);
    return [SKIP, index + 1];
  });
}
