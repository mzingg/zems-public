# Contributing to ZEMS

Thanks for your interest in contributing. Please read this first — the workflow here is a little different from a normal
GitHub project, because this repository is a **public mirror** of a larger private monorepo.

## How releases and contributions flow

- `main` is the **published** branch. It is written only by the maintainer's release tooling — one commit per release,
  plus the `release/<version>` tags. **Do not open pull requests against `main`; it is not a merge target.**
- Open your pull requests against the **`contributions`** branch.
- When a change is accepted, the maintainer **backports** it into the upstream monorepo. It then reaches `main` (and
  Maven Central) with the next release, and your pull request is closed as "shipped upstream". Your authorship is kept
  via a co-author trailer on the upstream commit and a mention in the release notes.

So your code does land — it just travels through the maintainer's monorepo on the way to `main`, rather than being
merged into `main` directly. This keeps the published history clean and lets the same change ship to Maven Central.

## Build and test

The build uses a pinned toolchain (JDK + Maven 4) provided through Nix:

```bash
nix develop --command mvn -B -PcommitGate verify
```

`nix develop` puts the right JDK and Maven on your PATH. The minimum JDK is **Java 25**.

If you do not use Nix, install JDK 25+ and Maven 4 yourself and run `mvn -B -PcommitGate verify`. The same checks
(checkstyle, PMD, tests) run in CI on every pull request.

## Formatting

Formatting is automatic. A pre-commit hook (installed by `nix develop`) runs **treefmt**, which formats Java, XML, JSON,
Markdown and more. You do not need to format by hand — just commit, and the hook tidies the files. To format manually,
run `treefmt` (or `nix fmt`).

## Reporting issues

Open a GitHub issue. A short, self-contained reproduction (a failing test or a minimal snippet) helps a lot.

## Licensing and sign-off

By contributing, you agree that your contribution is licensed under the project's [Apache License 2.0](LICENSE).

Please sign off your commits to certify the [Developer Certificate of Origin](https://developercertificate.org/) — add a
`Signed-off-by` line with `git commit -s`:

```
Signed-off-by: Your Name <you@example.com>
```

There is no separate Contributor License Agreement (CLA) to sign.
