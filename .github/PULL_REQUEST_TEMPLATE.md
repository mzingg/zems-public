<!--
  Thanks for contributing! Two things before you open this PR:

  1. Base branch must be `contributions`, NOT `main`. `main` is the published branch and is not a merge target.
     If GitHub picked `main` as the base, change it to `contributions` in the box above.
  2. Sign off your commits (DCO): `git commit -s`. See CONTRIBUTING.md.
-->

## What this changes

<!-- A short description of the change and why. Link any related issue (e.g. "Fixes #123"). -->

## Checklist

- [ ] Base branch is `contributions` (not `main`)
- [ ] `nix develop --command mvn -B -PcommitGate verify` passes locally
- [ ] Commits are signed off (`git commit -s`)
- [ ] Tests cover the change

<!--
  Note on how this ships: accepted changes are backported into the upstream monorepo and reach `main` + Maven Central
  with the next release; this PR is then closed as "shipped upstream", with your authorship preserved. See CONTRIBUTING.md.
-->
