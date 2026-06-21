{
  description = "ZEMS public — development environment";

  # Public mirror flake. Trimmed from the monorepo's flake.nix: it keeps the JDK / Maven / treefmt toolchain, the
  # pinned Prettier, the formatting pre-commit hook, env loading and IDE symlinks — and drops the monorepo-only bits
  # (the `clean` app, the libGDX/OpenGL runtime libs, Playwright, and Node), none of which the zero-dependency Java
  # libraries here need.

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    treefmt-nix.url = "github:numtide/treefmt-nix";
  };

  outputs =
    {
      self,
      nixpkgs,
      treefmt-nix,
    }:
    let
      supportedSystems = [
        "x86_64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
      treefmtEval =
        system: treefmt-nix.lib.evalModule nixpkgs.legacyPackages.${system} ./.nix/pkgs/treefmt.nix;
      perSystem = forAllSystems (system: rec {
        pkgs = nixpkgs.legacyPackages.${system};
        jdk26 = pkgs.callPackage ./.nix/pkgs/jdk26.nix { nixpkgsSrc = nixpkgs; };
        maven4 = pkgs.callPackage ./.nix/pkgs/maven.nix { inherit jdk26; };
        # Pinned Prettier (+ prettier-plugin-java) — the single formatter treefmt drives. Exposed here so the
        # pre-commit hook can print its version banner; treefmt itself references it by store path internally.
        prettier = pkgs.callPackage ./.nix/pkgs/prettier { };
        treefmt = (treefmtEval system).config.build.wrapper;
      });
    in
    {
      formatter = forAllSystems (system: perSystem.${system}.treefmt);

      checks = forAllSystems (system: {
        formatting = (treefmtEval system).config.build.check self;
      });

      devShells = forAllSystems (
        system:
        let
          inherit (perSystem.${system})
            pkgs
            jdk26
            maven4
            prettier
            treefmt
            ;

          # Wrap the pre-commit hook so IDE-driven commits (IntelliJ, VS Code, etc.) find treefmt / mvn / java even
          # when the calling shell didn't enter `nix develop`. The wrapper exports PATH from pinned Nix paths and
          # execs the real hook script.
          preCommitHook = pkgs.writeShellScript "pre-commit-hook" ''
            export PATH="${treefmt}/bin:${prettier}/bin:${maven4}/bin:${jdk26}/bin:$PATH"
            export JAVA_HOME="${jdk26}"
            exec ${pkgs.bash}/bin/bash ${./.nix/apps/pre-commit-hook.sh} "$@"
          '';
        in
        let
          defaultShell = pkgs.mkShell {
            buildInputs = [
              jdk26
              maven4
              treefmt
            ];

            # Prompt marker (see the monorepo flake for the shared p10k override that reads it).
            NIX_DEV_SHELL_NAME = "zems-public";

            shellHook = ''
              PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

              # Load local-only env vars (gitignored via **/*.local.*). See .nix/env.template.conf for the list;
              # copy it to .nix/env.local.conf and fill in real values.
              if [ -f "$PROJECT_ROOT/.nix/env.local.conf" ]; then
                set -a
                . "$PROJECT_ROOT/.nix/env.local.conf"
                set +a
              fi

              # Stable symlinks for IDE integration.
              mkdir -p "$PROJECT_ROOT/.nix/tools"
              ln -sfn ${maven4} "$PROJECT_ROOT/.nix/tools/maven"
              ln -sfn ${jdk26} "$PROJECT_ROOT/.nix/tools/jdk"

              # Install the formatting pre-commit hook (Nix-wrapped so IDE-driven commits find treefmt / mvn / java
              # on PATH; underlying hook script lives at .nix/apps/pre-commit-hook.sh).
              if [ -d "$PROJECT_ROOT/.git" ]; then
                mkdir -p "$PROJECT_ROOT/.git/hooks"
                ln -sfn ${preCommitHook} "$PROJECT_ROOT/.git/hooks/pre-commit"
              fi

              echo "ZEMS public dev environment"
              echo "Java:   $(java --version | head -1)"
              echo "Maven:  $(mvn --version | head -1)"
            '';
          };
        in
        {
          default = defaultShell;
          # The default environment plus gnupg, for releases only (`nix develop .#release`). maven-gpg-plugin shells
          # out to gpg during signing; keeping it out of the default shell means everyday `nix develop` doesn't pull
          # gnupg in.
          release = defaultShell.overrideAttrs (old: {
            buildInputs = old.buildInputs ++ [ pkgs.gnupg ];
          });
        }
      );
    };
}
