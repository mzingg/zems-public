package dev.zems.lib.value.marshal.format.binary;

import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * Pinned big-endian {@link ValueLayout}s for binary cursor primitives. The on-wire format is BIG_ENDIAN to match the
 * historical {@link java.nio.ByteBuffer} default; FFM access uses unaligned variants because cursors can land on
 * arbitrary byte offsets.
 */
final class BinaryLayouts {

  static final ValueLayout.OfShort I16 = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
  static final ValueLayout.OfInt I32 = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
  static final ValueLayout.OfLong I64 = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
  static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
  static final ValueLayout.OfDouble F64 = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

  private BinaryLayouts() {}
}
