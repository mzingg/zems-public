package dev.zems.lib.value.valueio.descriptor;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Cache for {@link TypeDescriptor#find(Class, String)}; its own class because interface fields would be public. */
final class TypeDescriptorCache {

  // ClassValue ties each entry to its Class, so an unloaded class takes its cache entry with it.
  private static final ClassValue<ConcurrentMap<String, Optional<TypeDescriptor<?>>>> RAW = new ClassValue<>() {
    @SuppressWarnings("NullableProblems") // IntelliJ infers a nullability contract the JDK does not declare here
    @Override
    protected ConcurrentMap<String, Optional<TypeDescriptor<?>>> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  };

  private TypeDescriptorCache() {}

  static ConcurrentMap<String, Optional<TypeDescriptor<?>>> forClass(Class<?> clazz) {
    return RAW.get(clazz);
  }
}
