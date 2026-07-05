# Public Site (zems.dev)

The source of the project website, <https://zems.dev> — a next.js app exported to static files and served by GitHub
Pages from the public mirror ([zems-public](https://github.com/mzingg/zems-public)).

## Run it

```bash
npm install
npm run dev        # dev server at http://localhost:40201
```

To preview the deployable static export:

```bash
npm run build      # writes the export to target/site
npm run start      # serves target/site at http://localhost:40201
```

## Editing content

All page text lives in `src/content/` — `src/app/` is only the rendering shell:

- `sections.tsx` — the prose of each section
- `libraries.tsx` — the list of published libraries (one entry per library)
- `meta.ts` — page title and meta description
- `version.ts` — the released version shown in the dependency snippet; the promote pipeline stamps the real version into
  it when the public tree is assembled, so the source keeps a placeholder

A content edit never needs a change in `src/app/`.
