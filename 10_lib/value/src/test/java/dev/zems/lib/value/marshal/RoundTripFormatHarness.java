package dev.zems.lib.value.marshal;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * Test-utility harness that gives round-trip tests a uniform write/read API across binary and JSON wire formats. Each
 * {@link Format} round-trips a single {@code Value<T>} through {@code ValueIo.framed()}.
 *
 * <p>
 * Use {@link #all()} to drive a parameterised test across every format, or {@link #binary()} / {@link #json()} for a
 * single format.
 */
public final class RoundTripFormatHarness {

  private RoundTripFormatHarness() {}

  public static Stream<Format> all() {
    return Stream.of(binary(), json());
  }

  public static Format binary() {
    return new Format() {
      @Override
      public String name() {
        return "binary";
      }

      @Override
      public boolean supportsStreaming() {
        return true;
      }

      @Override
      public <T> byte[] write(Value<T> value, TypeDescriptor<T> descriptor) {
        var bos = new ByteArrayOutputStream();
        try (var w = ValueIo.framed().binaryWriter(bos)) {
          w.write(value, descriptor);
        }
        return bos.toByteArray();
      }

      @Override
      public <T> Value<T> read(byte[] wire, TypeDescriptor<T> descriptor) {
        try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(wire))) {
          return r.read(descriptor);
        }
      }
    };
  }

  public static Format json() {
    return new Format() {
      @Override
      public String name() {
        return "json";
      }

      @Override
      public boolean supportsStreaming() {
        return true;
      }

      @Override
      public <T> byte[] write(Value<T> value, TypeDescriptor<T> descriptor) {
        var sw = new StringWriter();
        try (var w = ValueIo.framed().jsonWriter(sw)) {
          w.write(value, descriptor);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
      }

      @Override
      public <T> Value<T> read(byte[] wire, TypeDescriptor<T> descriptor) {
        try (var r = ValueIo.framed().jsonReader(new StringReader(new String(wire, StandardCharsets.UTF_8)))) {
          return r.read(descriptor);
        }
      }
    };
  }

  /** A single wire-format binding. */
  public interface Format {
    String name();

    boolean supportsStreaming();

    <T> byte[] write(Value<T> value, TypeDescriptor<T> descriptor);

    <T> Value<T> read(byte[] wire, TypeDescriptor<T> descriptor);
  }
}
