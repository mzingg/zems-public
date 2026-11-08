package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@ContractTest
@DisplayName("SegmentCursors")
class SegmentCursorsTest {

  @Test
  @DisplayName("mmapReadOnly(empty file) returns an empty cursor without invoking mmap")
  void mmapReadOnlyEmptyFile(@TempDir Path dir) throws IOException {
    Path empty = Files.createFile(dir.resolve("empty.bin"));

    try (SegmentReadCursor c = SegmentCursors.mmapReadOnly(empty)) {
      assertThat(c.hasRemaining()).isFalse();
      assertThat(c.position()).isZero();
    }
  }

  @Test
  @DisplayName("mmapReadOnly(non-empty file) maps the bytes and reads them sequentially")
  void mmapReadOnlyNonEmptyFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("data.bin");
    Files.write(file, new byte[] { 1, 2, 3, 4 });

    try (SegmentReadCursor c = SegmentCursors.mmapReadOnly(file)) {
      assertThat(c.hasRemaining()).isTrue();
      assertThat(c.get()).isEqualTo((byte) 1);
      assertThat(c.get()).isEqualTo((byte) 2);
      assertThat(c.get()).isEqualTo((byte) 3);
      assertThat(c.get()).isEqualTo((byte) 4);
      assertThat(c.hasRemaining()).isFalse();
    }
  }
}
