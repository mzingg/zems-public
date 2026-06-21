package dev.zems.lib.value;

/**
 * Non-sealed extension point for domain types that want first-class Value semantics. A type that
 * {@code implements CoreValue<T>} <em>is</em> a {@link Value Value&lt;T&gt;}: it slots directly into pattern-match
 * cases, flows through {@link Value}-typed APIs without wrapping, and skips the {@link BoxedValue} layer entirely.
 *
 * <p>
 * <b>CoreValue vs BoxedValue — when to use which:</b>
 *
 * <p>
 * Any non-built-in type {@code T} can already participate in the Value hierarchy via
 * {@link Value#of(Object) Value.of(t)}, which produces a {@code BoxedValue<T>} wrapping the raw instance. That covers
 * most cases — declaring {@code CoreValue} is unnecessary for incidental use. {@code CoreValue} is the deliberate
 * opt-in for types where being a {@code Value} is part of the domain model, with three concrete benefits:
 *
 * <ul>
 * <li><b>Pattern matching is direct.</b> A switch over {@code Value<?>} can write
 * {@code case PathReference pr -> ...} instead of
 * {@code case BoxedValue<?> bv when bv.inner() instanceof PathReference pr -> ...}.
 * <li><b>Stream APIs accept the type directly.</b>
 * {@code Stream<SourceNode>} flows into {@code ValueIo.streaming().binaryWriteAll(out, descriptor, stream)}
 * without an intermediate {@code .map(Value::of)}, because {@code SourceNode} <em>is</em>
 * {@code Value<SourceNode>}.
 * <li><b>One object per instance.</b> A {@code BoxedValue<X>(x)} pair allocates two objects
 * (the wrapper and the inner). For hot paths streaming millions of elements (tree traversals,
 * transaction logs), {@code CoreValue} keeps it to one object.
 * </ul>
 *
 * <p>
 * <b>Choose {@code CoreValue} when:</b> the type is a domain value (it appears in
 * {@code Map<String, Value<?>>} attribute slots; it's pattern-matched in user-facing switches;
 * it streams through marshalling at scale).
 *
 * <p>
 * <b>Choose {@code BoxedValue} (no declaration) when:</b> the type is incidental marshalling
 * payload (a record carried inside another structure; rarely matched by name; not a hot-path
 * stream). Just call {@code Value.of(myInstance)}.
 *
 * <p>
 * <b>Examples in the codebase:</b>
 *
 * <pre>
 * // Domain values — first-class participation in the Value hierarchy:
 * public record SourceNode(...) implements CoreValue&lt;SourceNode&gt; { ... }
 * public interface NodeValue extends CoreValue&lt;NodeValue&gt; { ... }
 * public final class PathReference implements CoreValue&lt;PathReference&gt; { ... }
 *
 * // Incidental — just use Value.of(...) without declaring anything:
 * record Point(int x, int y) {}
 * Value&lt;Point&gt; v = Value.of(new Point(1, 2)); // → BoxedValue&lt;Point&gt;
 * </pre>
 *
 * @param <T> the type parameter of the surrounding {@code Value<T>} — the value's own payload type. Because
 *            {@link Value#unbox(Value)} returns the {@code CoreValue} instance itself, {@code T} is the implementing
 *            type ({@code SourceNode implements CoreValue<SourceNode>}, {@code PathReference implements
 *            CoreValue<PathReference>}). A {@code CoreValue<SomeOtherType>} would make {@code unbox} hand back the
 *            instance typed as {@code SomeOtherType} and break on the first cast.
 */
public non-sealed interface CoreValue<T> extends Value<T> {}
