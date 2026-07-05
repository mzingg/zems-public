import {render, screen} from '@testing-library/react';
import '@testing-library/jest-dom';
import Home from '@/app/page';
import {PUBLISHED_VERSION} from '@/content/version';

describe('Home', () => {
  beforeEach(() => {
    render(<Home />);
  });

  it('renders the suite heading and tagline', () => {
    expect(screen.getByRole('heading', {level: 1, name: 'ZEMS'})).toBeInTheDocument();
    expect(screen.getByText('Zero-dependency Extensible Module Suite')).toBeInTheDocument();
  });

  it('renders the section headings', () => {
    for (const name of ['Library', 'Use it', 'Source']) {
      expect(screen.getByRole('heading', {level: 2, name})).toBeInTheDocument();
    }
  });

  it('lists the value library with its coordinates', () => {
    expect(screen.getByText('dev.zems.lib:value')).toBeInTheDocument();
  });

  it('shows the dependency snippet with the published version', () => {
    expect(screen.getByTestId('dependency-snippet')).toHaveTextContent(`<version>${PUBLISHED_VERSION}</version>`);
  });

  it('links to Maven Central, the public repo, and the releases page', () => {
    expect(screen.getByRole('link', {name: 'Maven Central'})).toHaveAttribute(
      'href',
      'https://central.sonatype.com/artifact/dev.zems.lib/value'
    );
    expect(screen.getByRole('link', {name: 'github.com/mzingg/zems-public'})).toHaveAttribute(
      'href',
      'https://github.com/mzingg/zems-public'
    );
    expect(screen.getByRole('link', {name: 'GitHub releases'})).toHaveAttribute(
      'href',
      'https://github.com/mzingg/zems-public/releases'
    );
  });

  it('mentions the licence in the footer', () => {
    expect(screen.getByTestId('footer')).toHaveTextContent('Apache License 2.0');
  });
});
