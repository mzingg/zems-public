import type {NextConfig} from 'next';

const nextConfig: NextConfig = {
  // Static export: with output 'export', `next build` writes the deployable site straight to distDir (the
  // intermediate build moves to .next/). target/site is the directory the public repo's pages.yml uploads to
  // GitHub Pages — keep the two paths in step.
  output: 'export',
  distDir: 'target/site',
  outputFileTracingRoot: __dirname,
  // The static export cannot run the image optimiser.
  images: {unoptimized: true},
};

export default nextConfig;
