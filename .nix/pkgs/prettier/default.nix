# Pinned Prettier + prettier-plugin-java, the single formatter engine treefmt drives for this repo
# (Java and every other Prettier-supported file type). Versions live in package.json and are bumped
# deliberately (Playwright-style); the resolved versions are printed by the `prettier-banner` bin.
#
# Bump procedure:
#   1. edit the two versions in package.json
#   2. (in this dir) npm install --package-lock-only --no-audit --no-fund
#   3. nix run nixpkgs#prefetch-npm-deps -- package-lock.json   # paste the hash into npmDepsHash below
#   4. rebuild and review the reformat diff before committing
{
  buildNpmPackage,
  makeWrapper,
  nodejs_24,
  lib,
}:
let
  pkgJson = lib.importJSON ./package.json;
  prettierVersion = pkgJson.dependencies.prettier;
  pluginVersion = pkgJson.dependencies."prettier-plugin-java";
  xmlVersion = pkgJson.dependencies."@prettier/plugin-xml";
in
buildNpmPackage {
  pname = "prettier-with-java";
  version = prettierVersion;
  src = ./.;

  npmDepsHash = "sha256-uvgY3tP6N1PLyzk+LVdAb9fmvGi7Mji58eY9sLPBZFI=";

  nodejs = nodejs_24;
  nativeBuildInputs = [ makeWrapper ];

  # Deps-only: there is no project build step, and the package ships no bin of its own.
  dontNpmBuild = true;

  # buildNpmPackage installs the package (with its node_modules) under $out/lib/node_modules/<pname>.
  # We expose three bins from this one pinned package, each loading only the plugins it needs so a
  # file type is never touched by a plugin meant for another:
  #   * prettier      — plain upstream Prettier (no plugins). Handles every type below and, crucially,
  #                     treats embedded code in markdown exactly as stock Prettier does (so it does NOT
  #                     reach into ```java / ```xml fences — curated snippets stay as written).
  #   * prettier-java — the same binary with prettier-plugin-java loaded, for *.java only.
  #   * prettier-xml  — the same binary with @prettier/plugin-xml loaded, for *.xml only.
  # Prettier 3 loads plugins via ESM `import()`, which ignores NODE_PATH and rejects bare/directory
  # specifiers from an unrelated cwd, so we resolve each plugin's real entry file at build time and
  # bake an absolute `--plugin` into its bin. Plus a banner bin that prints the pinned versions.
  postInstall = ''
    mkdir -p $out/bin
    topdir=$out/lib/node_modules/prettier-with-java
    prettiercjs="$topdir/node_modules/prettier/bin/prettier.cjs"
    resolve() { ${nodejs_24}/bin/node -e 'process.stdout.write(require.resolve(process.argv[2],{paths:[process.argv[1]]}))' "$topdir" "$1"; }
    makeWrapper ${nodejs_24}/bin/node $out/bin/prettier \
      --add-flags "$prettiercjs"
    makeWrapper ${nodejs_24}/bin/node $out/bin/prettier-java \
      --add-flags "$prettiercjs" \
      --add-flags "--plugin=$(resolve prettier-plugin-java)"
    makeWrapper ${nodejs_24}/bin/node $out/bin/prettier-xml \
      --add-flags "$prettiercjs" \
      --add-flags "--plugin=$(resolve @prettier/plugin-xml)"
    cat > $out/bin/prettier-banner <<EOF
    #!/bin/sh
    printf 'prettier %s (prettier-plugin-java %s, @prettier/plugin-xml %s)\n' '${prettierVersion}' '${pluginVersion}' '${xmlVersion}'
    EOF
    chmod +x $out/bin/prettier-banner
  '';

  passthru = { inherit prettierVersion pluginVersion xmlVersion; };

  meta = {
    description = "Pinned Prettier ${prettierVersion} + prettier-plugin-java ${pluginVersion} + @prettier/plugin-xml ${xmlVersion} for treefmt";
    homepage = "https://prettier.io/";
    license = lib.licenses.mit;
    mainProgram = "prettier";
  };
}
