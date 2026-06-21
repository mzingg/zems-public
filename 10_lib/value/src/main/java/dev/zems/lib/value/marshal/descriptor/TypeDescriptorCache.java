package dev.zems.lib.value.marshal.descriptor;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-class cache for {@link TypeDescriptor#find(Class, String)} resolution. Each {@link Class} is associated with a
 * small map keyed by field name (or the default key for the unnamed-lookup path); both hits and misses are cached as
 * {@link Optional} values.
 *
 * <p>
 * Lifetime: piggybacks on {@link ClassValue}, which stores entries in a hidden {@code Class.classValueMap} field on the
 * Class itself. When the Class is unloaded, its cache entry is collected as part of the same memory graph — no leaks
 * for dynamically loaded classes. No {@link java.lang.ref.WeakReference} layer needed.
 *
 * <p>
 * Package-private — exists only to keep {@code TypeDescriptor} an implementation-detail-free sealed interface
 * (interface fields are implicitly {@code public static final}, which would leak the cache into the public API).
 */
final class TypeDescriptorCache {

  private static final ClassValue<ConcurrentMap<String, Optional<TypeDescriptor<?>>>> RAW = new ClassValue<>() {
    @SuppressWarnings("NullableProblems")
    @Override
    protected ConcurrentMap<String, Optional<TypeDescriptor<?>>> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  };

  private TypeDescriptorCache() {}

  /** Returns the per-class entry map (creating it on first access). */
  static ConcurrentMap<String, Optional<TypeDescriptor<?>>> forClass(Class<?> clazz) {
    return RAW.get(clazz);
  }
}
