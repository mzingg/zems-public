import type {NavGroup} from './navigation.ts';
import {rawUrl, SITE_NAME} from './sources.ts';

/** The llms.txt index (https://llmstxt.org): every navigation page as its raw markdown twin, external items left out. */
export function llmsTxt(site: string, groups: NavGroup[], summary: string): string {
  const lines = [`# ${SITE_NAME}`, '', `> ${summary}`, ''];
  for (const group of groups) {
    lines.push(`## ${group.label}`, '');
    for (const item of group.items.filter((navItem) => !navItem.external)) {
      lines.push(`- [${item.label}](${site}${rawUrl(item.href.slice(1))})`);
    }
    lines.push('');
  }
  return lines.join('\n');
}
