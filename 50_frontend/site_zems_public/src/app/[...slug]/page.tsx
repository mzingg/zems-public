import type {Metadata} from 'next';
import {DocPage} from '@/app/doc-page';
import {docSite, pageMetadata} from '@/app/site-content';

type Props = {params: Promise<{slug: string[]}>};

export const dynamicParams = false;

export function generateStaticParams(): {slug: string[]}[] {
  return docSite()
    .sources.filter((source) => source.slug !== '')
    .map((source) => ({slug: source.slug.split('/')}));
}

export async function generateMetadata({params}: Props): Promise<Metadata> {
  const {slug} = await params;
  return pageMetadata(slug.join('/'));
}

export default async function Page({params}: Props) {
  const {slug} = await params;
  return <DocPage slug={slug.join('/')} />;
}
