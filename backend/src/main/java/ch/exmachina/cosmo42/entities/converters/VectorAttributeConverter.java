package ch.exmachina.cosmo42.entities.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
@Converter
public class VectorAttributeConverter implements AttributeConverter<float[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }

        // Convert float[] to byte[] (4 bytes for float, little-endian)
        ByteBuffer buffer = ByteBuffer.allocate(attribute.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (float value : attribute) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }

    @Override
    public float[] convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return null;
        }

        // Convert byte[] to float[]
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        float[] result = new float[dbData.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getFloat();
        }

        return result;
    }
}
