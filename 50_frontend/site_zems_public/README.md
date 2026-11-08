# Public Site (zems.dev)

The source of the project website, <https://zems.dev> — a next.js app that renders the repo's public markdown, exported
to static files and served by GitHub Pages from the public mirror
([zems-public](https://github.com/mzingg/zems-public)). Every page is also served as raw markdown at its page URL plus
`.md`, and `llms.txt` indexes those twins.

## Run it

```bash
npm install
npm run dev        # dev server at http://localhost:40201 (pages only, no markdown twins)
```

To preview the deployable static export:

```bash
npm run build      # writes the export, the markdown twins and llms.txt to target/site
npm run start      # serves target/site at http://localhost:40201
```

## Editing content

The site holds no content of its own. Edit the root `README.md` and `docs/*.md` for the suite pages, and a library's
`README.md` and `docs/*.md` for its area; a library appears on the site once its POM is marked
`<zems.public>true</zems.public>`. A relative link that points at nothing published fails the build.
