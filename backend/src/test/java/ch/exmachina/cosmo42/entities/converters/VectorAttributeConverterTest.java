package ch.exmachina.cosmo42.entities.converters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class VectorAttributeConverterTest {

    private final VectorAttributeConverter converter = new VectorAttributeConverter();

    @Test
    void convertToDatabaseColumnReturnsNullForNullInput() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumnReturnsEmptyArrayForEmptyVector() {
        byte[] result = converter.convertToDatabaseColumn(new float[0]);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void convertToEntityAttributeReturnsNullForNullInput() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttributeReturnsNullForEmptyInput() {
        assertThat(converter.convertToEntityAttribute(new byte[0])).isNull();
    }

    @Test
    void encodesFloatsInLittleEndianIeee754() {
        // 1.0f in IEEE 754 is 0x3F800000; little-endian bytes: 00 00 80 3F
        byte[] result = converter.convertToDatabaseColumn(new float[]{1.0f});

        assertThat(result).containsExactly(0x00, 0x00, 0x80, 0x3F);
    }

    @Test
    void encodesNegativeOneInLittleEndianIeee754() {
        // -1.0f is 0xBF800000; little-endian: 00 00 80 BF
        byte[] result = converter.convertToDatabaseColumn(new float[]{-1.0f});

        assertThat(result).containsExactly(0x00, 0x00, 0x80, 0xBF);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 16, 64, 1024})
    void byteLengthIsExactlyFourTimesFloatCount(int dim) {
        float[] vector = new float[dim];

        byte[] result = converter.convertToDatabaseColumn(vector);

        assertThat(result).hasSize(dim * 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 16, 64, 1024})
    void roundTripPreservesValuesExactly(int dim) {
        float[] original = randomVector(dim, 42L);

        float[] roundTripped = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(original));

        assertThat(roundTripped).containsExactly(original);
    }

    @Test
    void roundTripPreservesSpecialIeee754Values() {
        float[] original = {
                0.0f,
                -0.0f,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.MIN_VALUE,
                Float.MAX_VALUE,
                Float.MIN_NORMAL
        };

        float[] roundTripped = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(original));

        assertThat(roundTripped).hasSize(original.length);
        assertThat(Float.floatToRawIntBits(roundTripped[0])).isEqualTo(Float.floatToRawIntBits(0.0f));
        assertThat(Float.floatToRawIntBits(roundTripped[1])).isEqualTo(Float.floatToRawIntBits(-0.0f));
        assertThat(Float.isNaN(roundTripped[2])).isTrue();
        assertThat(roundTripped[3]).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(roundTripped[4]).isEqualTo(Float.NEGATIVE_INFINITY);
        assertThat(roundTripped[5]).isEqualTo(Float.MIN_VALUE);
        assertThat(roundTripped[6]).isEqualTo(Float.MAX_VALUE);
        assertThat(roundTripped[7]).isEqualTo(Float.MIN_NORMAL);
    }

    @Test
    void productionSize1024VectorRoundTripsLosslessly() {
        float[] original = randomVector(1024, 7L);

        byte[] bytes = converter.convertToDatabaseColumn(original);
        float[] roundTripped = converter.convertToEntityAttribute(bytes);

        assertThat(bytes).hasSize(4096);
        assertThat(roundTripped).containsExactly(original);
    }

    @Test
    void nonMultipleOfFourBytesTruncatesToFloorFloats() {
        // The DB column is declared vector(1024) so this input shape should never appear in
        // production. This test pins the current truncation behavior so a future refactor
        // notices the silent-data-loss path.
        byte[] malformed = new byte[5];
        malformed[0] = 0x00;
        malformed[1] = 0x00;
        malformed[2] = (byte) 0x80;
        malformed[3] = 0x3F;
        malformed[4] = (byte) 0xAA;

        float[] result = converter.convertToEntityAttribute(malformed);

        assertThat(result).containsExactly(1.0f);
    }

    private float[] randomVector(int dim, long seed) {
        Random rng = new Random(seed);
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rng.nextFloat() * 2.0f - 1.0f;
        }
        return v;
    }
}
