# ZEMS

**Zero-dependency Extensible Module Suite** — small, focused Java libraries with no runtime dependencies, built for the
current JDK.

## Libraries

| Library                         | Coordinate           | What it does                                                                                                                                             |
| ------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [value](10_lib/value/README.md) | `dev.zems.lib:value` | Immutable value types with explicit state (null / undefined / unresolved / error) and a wire-format-agnostic marshalling API (binary + JSON). Zero deps. |

## Use it

Requires **JDK 25 or newer**. See the [releases](https://github.com/mzingg/zems-public/releases) for the latest version.

Released versions are published to **GitHub Packages** (Maven Central is planned). GitHub Packages requires
authentication even for public packages, so add the repository and a token-backed server, then declare the dependency:

```xml
<!-- in your pom.xml -->
<repositories>
  <repository>
    <id>zems-public</id>
    <url>https://maven.pkg.github.com/mzingg/zems-public</url>
  </repository>
</repositories>

<dependency>
  <groupId>dev.zems.lib</groupId>
  <artifactId>value</artifactId>
  <version>0.0.1</version>
</dependency>
```

```xml
<!-- in ~/.m2/settings.xml — a GitHub token with read:packages -->
<servers>
  <server>
    <id>zems-public</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_TOKEN</password>
  </server>
</servers>
```

## Build from source

```bash
nix develop --command mvn -B -PreleaseGate verify
```

The build uses a pinned toolchain via Nix (JDK + Maven 4); `nix develop` brings it on PATH.

## Contributing

Contributions are welcome — please read [CONTRIBUTING.md](CONTRIBUTING.md) first. In short: open pull requests against
the **`contributions`** branch (not `main`). `main` is published by the maintainer and is not a merge target.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).

## More

- Project site — <https://zems.dev>
- Changelog — [GitHub Releases](https://github.com/mzingg/zems-public/releases)
