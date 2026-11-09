# ZEMS

**Zero-dependency Extensible Module Suite**: small, focused Java libraries with no third-party runtime dependencies,
built for the current JDK and released on Maven Central.

| Library                           | What it does                                                                                                                                                                                                                                                     | Documentation                                        |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| [common](10_lib/common/README.md) | ZEMS Common holds the helpers the ZEMS libraries are built and tested with: JUnit tags that sort tests into contract and journey tests, and a per-thread allocation measurement.                                                                                 | [common documentation](10_lib/common/docs/README.md) |
| [value](10_lib/value/README.md)   | Immutable value types for Java, built to become value classes when Project Valhalla lands. Each value carries its own state, such as null or an error, so bad input does not throw where it is parsed: it travels with the data and is handled where it is used. | [value documentation](10_lib/value/docs/README.md)   |

- [Setup](docs/setup.md) — the JDK floor and the coordinates of each library.
- [Contributing](https://github.com/mzingg/zems-public/blob/main/CONTRIBUTING.md) — how to contribute, the licence, and
  forking.
- [Self-Reinforcing Agentic Delivery](docs/agentic-delivery/README.md) — an essay on how a delivery process run by
  coding agents could improve itself.
