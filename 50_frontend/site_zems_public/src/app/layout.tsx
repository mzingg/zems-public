import type {Metadata} from 'next';
import './globals.css';
import {SITE_DESCRIPTION, SITE_TITLE} from '@/content/meta';

// No next/font here on purpose: the page uses the system-ui font stack (parity with the static page this module
// replaced), and a Google-font download at build time would add a network dependency to the public Pages build.

export const metadata: Metadata = {
  title: SITE_TITLE,
  description: SITE_DESCRIPTION,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
