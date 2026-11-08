import type {Metadata} from 'next';
import {DocPage} from '@/app/doc-page';
import {pageMetadata} from '@/app/site-content';

export function generateMetadata(): Metadata {
  return pageMetadata('');
}

export default function Home() {
  return <DocPage slug="" />;
}
