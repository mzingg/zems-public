# Value Library — Known Issues and Gotchas

Read on demand (odd behaviour, deep debugging, review/audit) — not loaded into agent sessions. Contracts and design
decisions live in `CLAUDE.md`.

## CBOR `hasField` false positive at the top level (FRAMED mode)

`BinaryStateReader.doHasField(id, name)` has three regimes. Inside a map frame it peeks the next key (correct). At the
**top level** (no enclosing frame) it returns `cursor.hasRemaining()` — and in FRAMED mode the envelope terminator bytes
always remain, so `hasField` reports **true for an absent trailing slot**. The following `read*` then consumes the
terminator ("expected integer but got MAP").

Consequences:

- `read*Or(id, name, default)` is NOT usable for an optional trailing top-level slot under the binary format. JSON is
  fine — its probe is a real object lookup.
- Workaround (used by the tree library for the `order` slot): **always write** the slot; keep `read*Or` only for
  tolerance towards hand-authored JSON.
- A possible source fix (make the top-level branch peek like map frames do) needs care — a failed peek may consume bytes
  and desync the wire. Raise a value-lib issue before attempting it.

## A record with a `byte[]` component does not marshal

Record synthesis (`RecordSynthesis`) fails on a `byte[]` component ("Cannot resolve component type [B") even though
`SlotKind` knows BYTES at the wire layer. Large binary payloads stay off the value wire by design (the 5 MiB
`maxStringLength` default would block them anyway) — write such data as raw bytes beside the tree/value data (see the
game engine's `overview.bin`). If a `byte[]`-carrying `Value` is ever genuinely needed, the `Base64Value` subtask of
`expand-built-in-values` is the path.
