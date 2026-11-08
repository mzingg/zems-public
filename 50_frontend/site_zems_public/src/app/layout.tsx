import './globals.css';

// No next/font here on purpose: the page uses the system-ui font stack, and a Google-font download at build time would
// add a network dependency to the public Pages build.
// No metadataBase either: with one, next turns the markdown twin's alternate link into an absolute URL.

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        {children}
        <footer data-testid="footer">
          Apache License 2.0. Released versions are listed on the{' '}
          <a href="https://github.com/mzingg/zems-public/releases">GitHub releases</a> page.
        </footer>
      </body>
    </html>
  );
}
