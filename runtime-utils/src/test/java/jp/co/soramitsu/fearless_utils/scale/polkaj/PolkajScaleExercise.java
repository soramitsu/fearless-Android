package jp.co.soramitsu.fearless_utils.scale.polkaj;

import io.emeraldpay.polkaj.scale.CompactMode;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleExtract;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.UnionValue;
import io.emeraldpay.polkaj.scale.reader.BoolOptionalReader;
import io.emeraldpay.polkaj.scale.reader.BoolReader;
import io.emeraldpay.polkaj.scale.reader.CompactBigIntReader;
import io.emeraldpay.polkaj.scale.reader.CompactUIntReader;
import io.emeraldpay.polkaj.scale.reader.EnumReader;
import io.emeraldpay.polkaj.scale.reader.Int32Reader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.LongReader;
import io.emeraldpay.polkaj.scale.reader.ShortReader;
import io.emeraldpay.polkaj.scale.reader.StringReader;
import io.emeraldpay.polkaj.scale.reader.UByteReader;
import io.emeraldpay.polkaj.scale.reader.UInt16Reader;
import io.emeraldpay.polkaj.scale.reader.UInt32Reader;
import io.emeraldpay.polkaj.scale.reader.UnionReader;
import io.emeraldpay.polkaj.scale.writer.BoolOptionalWriter;
import io.emeraldpay.polkaj.scale.writer.BoolWriter;
import io.emeraldpay.polkaj.scale.writer.CompactBigIntWriter;
import io.emeraldpay.polkaj.scale.writer.CompactUIntWriter;
import io.emeraldpay.polkaj.scale.writer.CompactULongWriter;
import io.emeraldpay.polkaj.scale.writer.ListWriter;
import io.emeraldpay.polkaj.scale.writer.LongWriter;
import io.emeraldpay.polkaj.scale.writer.ShortWriter;
import io.emeraldpay.polkaj.scale.writer.StringWriter;
import io.emeraldpay.polkaj.scale.writer.UByteWriter;
import io.emeraldpay.polkaj.scale.writer.UInt16Writer;
import io.emeraldpay.polkaj.scale.writer.UInt32Writer;
import io.emeraldpay.polkaj.scale.writer.ULong32Writer;
import io.emeraldpay.polkaj.scale.writer.UnionWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Function;

/**
 * Drives every polkaj-scale reader, writer and helper over deterministic pseudo-random input and returns one
 * transcript line per operation: the input and either the output or the exception class.
 *
 * <p>It depends on nothing but the JDK and polkaj-scale, so the same compiled class can run against the old
 * polkaj-scale-0.2.3.jar (in an isolated class loader) and against the sources in runtime-utils.
 */
public final class PolkajScaleExercise {

    public enum Colour { RED, GREEN, BLUE }

    private static final long[] EDGE_LONGS = {
        0L, 1L, 2L, 0x3fL, 0x40L, 0xffL, 0x100L, 0x3fffL, 0x4000L, 0x7fffL, 0x8000L, 0xffffL, 0x10000L,
        0x3fffffffL, 0x40000000L, 0x7fffffffL, 0x80000000L, 0xffffffffL, 0x100000000L, 0xffffffffffL,
        0x7fffffffffffffffL, -1L, -2L, -0x40L, Integer.MIN_VALUE, Long.MIN_VALUE, Short.MAX_VALUE, Short.MIN_VALUE,
    };

    private final SplittableRandom random;
    private final List<String> out = new ArrayList<>();

    private PolkajScaleExercise(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /** The transcript of [rounds] random cases per codec. Same seed and rounds give the same transcript. */
    public static List<String> run(long seed, int rounds) {
        PolkajScaleExercise exercise = new PolkajScaleExercise(seed);
        exercise.edgeCases();
        for (int round = 0; round < rounds; round++) {
            exercise.randomRound(round);
        }
        return exercise.out;
    }

    private void edgeCases() {
        for (long value : EDGE_LONGS) {
            numberWriters("edge", value);
        }
        for (int bits = 0; bits <= 540; bits += 7) {
            BigInteger value = BigInteger.ONE.shiftLeft(bits);
            write("edge CompactBigIntWriter 2^" + bits, new CompactBigIntWriter(), value);
            write("edge CompactBigIntWriter 2^" + bits + "-1", new CompactBigIntWriter(), value.subtract(BigInteger.ONE));
            compactModes("edge 2^" + bits, value);
        }
        write("edge CompactBigIntWriter negative", new CompactBigIntWriter(), BigInteger.valueOf(-5));
        for (int b = -1; b <= 4; b++) {
            out.add("edge CompactMode.byValue " + b + " -> " + CompactMode.byValue((byte) b));
        }
        for (int index : new int[]{-1, 0, 1, 255, 256}) {
            try {
                UnionValue<String> value = new UnionValue<>(index, "x");
                out.add("edge UnionValue " + index + " -> " + value.getIndex() + "," + value.getValue());
            } catch (Throwable error) {
                out.add("edge UnionValue " + index + " -> " + error(error));
            }
        }
        try {
            ScaleExtract.fromBytes(null);
            out.add("edge ScaleExtract null -> ok");
        } catch (Throwable error) {
            out.add("edge ScaleExtract null -> " + error(error));
        }
        try {
            new EnumReader<>(new Colour[0]);
            out.add("edge EnumReader empty -> ok");
        } catch (Throwable error) {
            out.add("edge EnumReader empty -> " + error(error));
        }
        for (int len : new int[]{0, 31, 32, 33}) {
            byte[] value = bytes(len);
            writeWith("edge writeUint256 " + hex(value), w -> w.writeUint256(value));
        }
        writeWith("edge writeOptional(Bool, null)", w -> w.writeOptional(new BoolWriter(), (Boolean) null));
        writeWith("edge writeOptional(BoolOpt, null)", w -> w.writeOptional(new BoolOptionalWriter(), (Optional<Boolean>) null));
        writeWith("edge writeOptional(UInt16, null)", w -> w.writeOptional(new UInt16Writer(), (Integer) null));
        writeWith("edge writeOptional(UInt16, empty)", w -> w.writeOptional(new UInt16Writer(), Optional.<Integer>empty()));
        for (Boolean value : new Boolean[]{true, false}) {
            writeWith("edge writeOptional(Bool, " + value + ")", w -> w.writeOptional(new BoolWriter(), value));
            writeWith("edge writeOptional(Bool, Optional " + value + ")", w -> w.writeOptional(new BoolWriter(), Optional.of(value)));
            writeWith("edge writeOptional(BoolOpt, Optional " + value + ")",
                w -> w.writeOptional(new BoolOptionalWriter(), Optional.of(value)));
        }
        writeWith("edge writeOptional(BoolOpt, empty)", w -> w.writeOptional(new BoolOptionalWriter(), Optional.<Boolean>empty()));
        cursor("edge cursor", bytes(6));
    }

    private void randomRound(int round) {
        String tag = "r" + round;
        long value = randomLong();
        numberWriters(tag, value);
        compactModes(tag, BigInteger.valueOf(value));
        BigInteger big = new BigInteger(random.nextInt(560), new java.util.Random(random.nextLong()));
        write(tag + " CompactBigIntWriter " + big, new CompactBigIntWriter(), big);
        compactModes(tag + " big", big);

        String text = randomString();
        write(tag + " StringWriter " + escape(text), new StringWriter(), text);
        writeWith(tag + " writeString " + escape(text), w -> w.writeString(text));
        byte[] array = bytes(random.nextInt(70));
        writeWith(tag + " writeByteArray " + hex(array), w -> w.writeByteArray(array));
        writeWith(tag + " writeByte(int) " + value, w -> w.writeByte((int) value));
        writeWith(tag + " writeByte(byte) " + (byte) value, w -> w.writeByte((byte) value));
        writeWith(tag + " directWrite slice " + hex(array), w -> w.directWrite(array, 0, array.length / 2));
        writeWith(tag + " writeOptional(UInt16) " + value, w -> w.writeOptional(new UInt16Writer(), (int) value));
        writeWith(tag + " writeOptional(String) " + escape(text), w -> w.writeOptional(new StringWriter(), Optional.of(text)));

        List<Integer> list = new ArrayList<>();
        int size = random.nextInt(5);
        for (int i = 0; i < size; i++) {
            list.add(random.nextInt(0x20000) - 0x100);
        }
        write(tag + " ListWriter(UInt16) " + list, new ListWriter<>(new UInt16Writer()), list);
        int unionIndex = random.nextInt(4);
        Object unionValue = unionIndex == 0 ? (Object) (int) (value & 0xffff) : unionIndex == 1 ? (Object) random.nextBoolean() : text;
        writeUnion(tag + " UnionWriter " + unionIndex, unionIndex, unionValue);

        byte[] input = bytes(random.nextInt(11));
        readers(tag, input);
        readers(tag + " compact", compactPrefixed());
        readers(tag + " string", stringEncoding());
        cursor(tag + " cursor", input);
    }

    private void numberWriters(String tag, long value) {
        int asInt = (int) value;
        write(tag + " UByteWriter " + asInt, new UByteWriter(), asInt);
        write(tag + " UInt16Writer " + asInt, new UInt16Writer(), asInt);
        write(tag + " UInt32Writer " + asInt, new UInt32Writer(), asInt);
        write(tag + " ULong32Writer " + value, new ULong32Writer(), value);
        write(tag + " CompactUIntWriter " + asInt, new CompactUIntWriter(), asInt);
        write(tag + " CompactULongWriter " + value, new CompactULongWriter(), value);
        write(tag + " BoolWriter " + (value % 2 == 0), new BoolWriter(), value % 2 == 0);
        write(tag + " ShortWriter " + (short) value, new ShortWriter(), (short) value);
        write(tag + " LongWriter " + value, new LongWriter(), value);
        writeWith(tag + " writeUint16 " + asInt, w -> w.writeUint16(asInt));
        writeWith(tag + " writeUint32(int) " + asInt, w -> w.writeUint32(asInt));
        writeWith(tag + " writeUint32(long) " + value, w -> w.writeUint32(value));
        writeWith(tag + " writeCompact " + asInt, w -> w.writeCompact(asInt));
        writeWith(tag + " writeShort " + (short) value, w -> w.writeShort((short) value));
        writeWith(tag + " writeLong " + value, w -> w.writeLong(value));
        writeWith(tag + " directWrite " + asInt, w -> w.directWrite(asInt));
        Optional<Boolean> optional = value % 3 == 0 ? Optional.<Boolean>empty() : Optional.of(value % 3 == 1);
        write(tag + " BoolOptionalWriter " + optional, new BoolOptionalWriter(), optional);
    }

    private void compactModes(String tag, BigInteger value) {
        try {
            out.add(tag + " CompactMode.forNumber(BigInteger) " + value + " -> " + CompactMode.forNumber(value));
        } catch (Throwable error) {
            out.add(tag + " CompactMode.forNumber(BigInteger) " + value + " -> " + error(error));
        }
        long asLong = value.longValue();
        try {
            out.add(tag + " CompactMode.forNumber(long) " + asLong + " -> " + CompactMode.forNumber(asLong));
        } catch (Throwable error) {
            out.add(tag + " CompactMode.forNumber(long) " + asLong + " -> " + error(error));
        }
        int asInt = value.intValue();
        try {
            out.add(tag + " CompactMode.forNumber(int) " + asInt + " -> " + CompactMode.forNumber(asInt));
        } catch (Throwable error) {
            out.add(tag + " CompactMode.forNumber(int) " + asInt + " -> " + error(error));
        }
    }

    private void readers(String tag, byte[] input) {
        String in = tag + " " + hex(input);
        read(in + " UByteReader", input, new UByteReader());
        read(in + " UInt16Reader", input, new UInt16Reader());
        read(in + " UInt32Reader", input, new UInt32Reader());
        read(in + " Int32Reader", input, new Int32Reader());
        read(in + " CompactUIntReader", input, new CompactUIntReader());
        read(in + " CompactBigIntReader", input, new CompactBigIntReader());
        read(in + " BoolReader", input, new BoolReader());
        read(in + " BoolOptionalReader", input, new BoolOptionalReader());
        read(in + " ShortReader", input, new ShortReader());
        read(in + " LongReader", input, new LongReader());
        read(in + " EnumReader", input, new EnumReader<>(Colour.values()));
        read(in + " ScaleExtract(UInt32)", input, null);
        readWith(in + " readUint256", input, ScaleCodecReader::readUint256);
        int len = random.nextInt(12) - 2;
        readWith(in + " readByteArray(" + len + ")", input, r -> r.readByteArray(len));
        // A compact length prefix allocates before it reads. Lengths beyond the input could only end in an
        // OutOfMemoryError or an index error, depending on the heap, so they are recorded without running.
        if (compactAt(input, 0) <= input.length) {
            read(in + " StringReader", input, new StringReader());
            read(in + " ListReader(UInt16)", input, new ListReader<>(new UInt16Reader()));
            readWith(in + " readByteArray", input, ScaleCodecReader::readByteArray);
            readWith(in + " readString", input, ScaleCodecReader::readString);
        } else {
            out.add(in + " length-prefixed readers -> skipped, prefix exceeds input");
        }
        if (input.length == 0 || (input[0] & 0xff) != 2 || compactAt(input, 1) <= input.length) {
            read(in + " UnionReader", input, new UnionReader<>(new UInt16Reader(), new BoolReader(), new StringReader()));
        } else {
            out.add(in + " UnionReader -> skipped, string prefix exceeds input");
        }
        readWith(in + " readCompactInt", input, ScaleCodecReader::readCompactInt);
        readWith(in + " readUint32", input, ScaleCodecReader::readUint32);
        readWith(in + " readUint16", input, ScaleCodecReader::readUint16);
        readWith(in + " readUByte", input, ScaleCodecReader::readUByte);
        readWith(in + " readByte", input, ScaleCodecReader::readByte);
        readWith(in + " readBoolean", input, ScaleCodecReader::readBoolean);
        readWith(in + " readShort", input, ScaleCodecReader::readShort);
        readWith(in + " readLong", input, ScaleCodecReader::readLong);
        readWith(in + " readOptional(UInt16)", input, r -> r.readOptional(new UInt16Reader()));
        readWith(in + " readOptional(Bool)", input, r -> r.readOptional(new BoolReader()));
        readWith(in + " readOptional(BoolOpt)", input, r -> r.readOptional(new BoolOptionalReader()));
    }

    private void cursor(String tag, byte[] input) {
        ScaleCodecReader reader = new ScaleCodecReader(input);
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < 6; step++) {
            int op = random.nextInt(3);
            int amount = random.nextInt(9) - 3;
            try {
                if (op == 0) {
                    reader.skip(amount);
                    trace.append(" skip(").append(amount).append(")");
                } else if (op == 1) {
                    reader.seek(amount);
                    trace.append(" seek(").append(amount).append(")");
                } else {
                    trace.append(" byte=").append(reader.readByte());
                }
            } catch (Throwable error) {
                trace.append(" ").append(error(error));
            }
            trace.append(" next=").append(reader.hasNext());
        }
        out.add(tag + " " + hex(input) + " ->" + trace);
    }

    private <T> void write(String label, ScaleWriter<T> writer, T value) {
        writeWith(label, w -> w.write(writer, value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeUnion(String label, int index, Object value) {
        UnionWriter writer = new UnionWriter(new UInt16Writer(), new BoolWriter(), new StringWriter());
        writeWith(label, w -> w.write(writer, new UnionValue(index, value)));
    }

    private void writeWith(String label, WriterAction action) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ScaleCodecWriter writer = new ScaleCodecWriter(bytes);
        try {
            action.run(writer);
            out.add(label + " -> " + hex(bytes.toByteArray()));
        } catch (Throwable error) {
            out.add(label + " -> " + error(error) + " after " + hex(bytes.toByteArray()));
        }
    }

    private <T> void read(String label, byte[] input, ScaleReader<T> reader) {
        if (reader == null) {
            readWith(label, input, r -> ScaleExtract.fromBytes(new UInt32Reader()).apply(input));
        } else {
            readWith(label, input, r -> r.read(reader));
        }
    }

    private void readWith(String label, byte[] input, Function<ScaleCodecReader, Object> action) {
        ScaleCodecReader reader = new ScaleCodecReader(input);
        String result;
        try {
            result = repr(action.apply(reader));
        } catch (Throwable error) {
            result = error(error);
        }
        int rest = 0;
        try {
            while (reader.hasNext()) {
                reader.readByte();
                rest++;
            }
        } catch (Throwable error) {
            result += " " + error(error);
        }
        out.add(label + " -> " + result + " rest=" + rest);
    }

    // The compact-encoded unsigned value at [offset], 0 when there are not enough bytes to say.
    private static long compactAt(byte[] input, int offset) {
        if (input.length <= offset) {
            return 0;
        }
        int first = input[offset] & 0xff;
        int mode = first & 3;
        int width = mode == 0 ? 1 : mode == 1 ? 2 : mode == 2 ? 4 : 0;
        if (width == 0) {
            return Long.MAX_VALUE;
        }
        long value = 0;
        for (int i = 0; i < width; i++) {
            if (input.length <= offset + i) {
                return 0;
            }
            value |= (long) (input[offset + i] & 0xff) << (8 * i);
        }
        return value >>> 2;
    }

    private byte[] compactPrefixed() {
        int mode = random.nextInt(4);
        int extra = mode == 3 ? random.nextInt(6) : random.nextInt(4);
        byte[] value = bytes(1 + extra);
        value[0] = (byte) ((value[0] & ~3) | mode);
        return value;
    }

    private byte[] stringEncoding() {
        String text = randomString();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            new ScaleCodecWriter(bytes).writeString(text);
        } catch (Throwable error) {
            return new byte[0];
        }
        byte[] encoded = bytes.toByteArray();
        return random.nextInt(4) == 0 ? Arrays.copyOf(encoded, Math.max(0, encoded.length - 1)) : encoded;
    }

    private long randomLong() {
        int kind = random.nextInt(4);
        if (kind == 0) {
            return EDGE_LONGS[random.nextInt(EDGE_LONGS.length)] + random.nextInt(3) - 1;
        }
        int bits = random.nextInt(65);
        long value = bits == 0 ? 0 : random.nextLong() >>> (64 - bits);
        return kind == 3 ? -value : value;
    }

    private String randomString() {
        int length = random.nextInt(12);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int kind = random.nextInt(6);
            if (kind == 0) {
                text.append((char) (0x20 + random.nextInt(0x5f)));
            } else if (kind == 1) {
                text.append((char) (0xa0 + random.nextInt(0x700)));
            } else if (kind == 2) {
                text.append((char) (0x3040 + random.nextInt(0x100)));
            } else if (kind == 3) {
                text.appendCodePoint(0x1f300 + random.nextInt(0x300));
            } else if (kind == 4) {
                text.append((char) (0xd800 + random.nextInt(0x800)));
            } else {
                text.append((char) random.nextInt(0x20));
            }
        }
        return text.toString();
    }

    private byte[] bytes(int length) {
        byte[] value = new byte[length];
        for (int i = 0; i < length; i++) {
            value[i] = (byte) random.nextInt(256);
        }
        return value;
    }

    private static String repr(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[]) {
            return "bytes:" + hex((byte[]) value);
        }
        if (value instanceof String) {
            return "string:" + escape((String) value);
        }
        if (value instanceof UnionValue) {
            UnionValue<?> union = (UnionValue<?>) value;
            return "union:" + union.getIndex() + ":" + repr(union.getValue());
        }
        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            return optional.isPresent() ? "some:" + repr(optional.get()) : "none";
        }
        if (value instanceof List) {
            StringBuilder items = new StringBuilder("list:[");
            for (Object item : (List<?>) value) {
                items.append(repr(item)).append(';');
            }
            return items.append(']').toString();
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    private static String error(Throwable error) {
        return "!" + error.getClass().getName();
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x20 && c < 0x7f && c != '\\') {
                escaped.append(c);
            } else {
                escaped.append("\\u").append(hexDigits(c, 4));
            }
        }
        return escaped.toString();
    }

    private static String hex(byte[] value) {
        StringBuilder text = new StringBuilder("0x");
        for (byte b : value) {
            text.append(hexDigits(b & 0xff, 2));
        }
        return text.toString();
    }

    private static String hexDigits(int value, int width) {
        String digits = Integer.toHexString(value);
        StringBuilder padded = new StringBuilder();
        for (int i = digits.length(); i < width; i++) {
            padded.append('0');
        }
        return padded.append(digits).toString();
    }

    private interface WriterAction {
        void run(ScaleCodecWriter writer) throws Exception;
    }
}
