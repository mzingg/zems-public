{ pkgs, ... }:
# treefmt is the single formatter for the whole repo. One pinned Prettier package (see ./prettier)
# formats every Prettier-supported file type INCLUDING Java and XML; nixfmt handles Nix, shfmt shell,
# ruff Python. There is no second formatter launcher — Spotless was retired and formatting left the
# Maven build.
#
# The Prettier package ships three bins, each loading only the plugin its type needs: a plain
# `prettier` for the common types (identical to stock Prettier, so embedded code in markdown is
# untouched), a `prettier-java` (prettier-plugin-java) for *.java, and a `prettier-xml`
# (@prettier/plugin-xml) for *.xml. All read one shared config in .mvn/parent_common, referenced by
# store path so the formatter is independent of the cwd treefmt runs it from.
let
  prettierPkg = pkgs.callPackage ./prettier { };
  prettierConfig = "${../../.mvn/parent_common/prettier-config.json}";
in
{
  projectRootFile = "flake.nix";

  # Generated TypeScript declaration; build output (target/) is already skipped via .gitignore.
  settings.global.excludes = [ "**/next-env.d.ts" ];

  programs.nixfmt.enable = true;
  # Shell scripts (.sh): 2-space indent to match the existing hooks/run scripts.
  programs.shfmt = {
    enable = true;
    indent_size = 2;
  };
  # Python (.py): the issue-tracker skill scripts.
  programs.ruff-format.enable = true;

  # Every Prettier-supported type EXCEPT Java, via plain Prettier (the upstream default extension set).
  settings.formatter.prettier = {
    command = "${prettierPkg}/bin/prettier";
    options = [
      "--write"
      "--config"
      prettierConfig
    ];
    includes = [
      "*.cjs"
      "*.css"
      "*.html"
      "*.js"
      "*.json"
      "*.json5"
      "*.jsx"
      "*.md"
      "*.mdx"
      "*.mjs"
      "*.scss"
      "*.ts"
      "*.tsx"
      "*.vue"
      "*.yaml"
      "*.yml"
    ];
  };

  # Java only, via Prettier with prettier-plugin-java. *.java is broader than Spotless's old
  # **/src/**/*.java, so keep generated trees out (belt and suspenders — .gitignore already excludes
  # target/ from treefmt's walk).
  settings.formatter.prettier-java = {
    command = "${prettierPkg}/bin/prettier-java";
    options = [
      "--write"
      "--config"
      prettierConfig
    ];
    includes = [ "*.java" ];
    excludes = [
      "**/target/**"
      "**/generated-sources/**"
    ];
  };

  # XML only, via Prettier with @prettier/plugin-xml (Maven POMs, checkstyle/pmd/owasp configs, …).
  settings.formatter.prettier-xml = {
    command = "${prettierPkg}/bin/prettier-xml";
    options = [
      "--write"
      "--config"
      prettierConfig
    ];
    includes = [ "*.xml" ];
    excludes = [
      "**/target/**"
      "**/generated-sources/**"
    ];
  };
}
