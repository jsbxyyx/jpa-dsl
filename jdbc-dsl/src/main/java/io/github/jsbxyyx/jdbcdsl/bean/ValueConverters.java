package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.core.convert.ConversionService;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Factory for creating {@link ValueConverter} instances.
 *
 * <p>Provides optimized converters for common scenarios:
 * <ul>
 *   <li>{@link #identity()} - no-op converter when types match</li>
 *   <li>{@link #fromConversionService} - delegates to Spring's ConversionService</li>
 * </ul>
 */
public final class ValueConverters {

    private ValueConverters() {
        // utility class
    }

    /**
     * Returns a no-op converter that returns values unchanged.
     *
     * <p>Used when the source type already matches the target type,
     * avoiding unnecessary type checks and conversions.
     *
     * @return an identity converter
     */
    public static ValueConverter identity() {
        return IdentityConverter.INSTANCE;
    }

    /**
     * Creates a numeric converter based on the target Java type.
     *
     * <p>JDBC drivers are not consistent about numeric return types. For example,
     * SMALLINT may be returned as Integer. The conversion therefore must follow
     * the bean property type instead of the JDBC runtime value type.
     */
    public static ValueConverter numeric(Class<?> targetType) {
        if (targetType == Byte.class) {
            return BYTE_WRAPPER_CONVERTER;
        }
        if (targetType == byte.class) {
            return BYTE_PRIMITIVE_CONVERTER;
        }
        if (targetType == Short.class) {
            return SHORT_WRAPPER_CONVERTER;
        }
        if (targetType == short.class) {
            return SHORT_PRIMITIVE_CONVERTER;
        }
        if (targetType == Integer.class) {
            return INTEGER_WRAPPER_CONVERTER;
        }
        if (targetType == int.class) {
            return INTEGER_PRIMITIVE_CONVERTER;
        }
        if (targetType == Long.class) {
            return LONG_WRAPPER_CONVERTER;
        }
        if (targetType == long.class) {
            return LONG_PRIMITIVE_CONVERTER;
        }
        return null;
    }

    private static Object convertNumber(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType == byte.class) {
                return (byte) 0;
            }
            if (targetType == short.class) {
                return (short) 0;
            }
            if (targetType == int.class) {
                return 0;
            }
            if (targetType == long.class) {
                return 0L;
            }
            return null;
        }
        if (!(value instanceof Number number)) {
            return value;
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return number.byteValue();
        }
        if (targetType == Short.class || targetType == short.class) {
            return number.shortValue();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return number.intValue();
        }
        if (targetType == Long.class || targetType == long.class) {
            return number.longValue();
        }
        return value;
    }

    private static final ValueConverter BYTE_WRAPPER_CONVERTER = value -> convertNumber(value, Byte.class);
    private static final ValueConverter BYTE_PRIMITIVE_CONVERTER = value -> convertNumber(value, byte.class);
    private static final ValueConverter SHORT_WRAPPER_CONVERTER = value -> convertNumber(value, Short.class);
    private static final ValueConverter SHORT_PRIMITIVE_CONVERTER = value -> convertNumber(value, short.class);
    private static final ValueConverter INTEGER_WRAPPER_CONVERTER = value -> convertNumber(value, Integer.class);
    private static final ValueConverter INTEGER_PRIMITIVE_CONVERTER = value -> convertNumber(value, int.class);
    private static final ValueConverter LONG_WRAPPER_CONVERTER = value -> convertNumber(value, Long.class);
    private static final ValueConverter LONG_PRIMITIVE_CONVERTER = value -> convertNumber(value, long.class);

    /**
     * Creates a Timestamp to LocalDateTime converter.
     */
    public static ValueConverter timestampToLocalDateTime() {
        return value -> {
            if (value == null) {
                return null;
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
            return value;
        };
    }

    /**
     * Creates a SQL Date to LocalDate converter.
     */
    public static ValueConverter sqlDateToLocalDate() {
        return value -> {
            if (value == null) {
                return null;
            }
            if (value instanceof Date date) {
                return date.toLocalDate();
            }
            return value;
        };
    }

    /**
     * Creates a converter that delegates to a {@link ConversionService}.
     *
     * <p>This converter checks if conversion is supported and applies it.
     * If conversion is not supported, returns the value unchanged.
     *
     * @param conversionService the conversion service to use
     * @param sourceType the source type
     * @param targetType the target type
     * @return a converter backed by the conversion service
     */
    public static ValueConverter fromConversionService(
            ConversionService conversionService, Class<?> sourceType, Class<?> targetType) {
        if (conversionService.canConvert(sourceType, targetType)) {
            return new ConversionServiceConverter(conversionService, targetType);
        }
        return identity();
    }

    /**
     * Creates a converter that resolves the appropriate converter at runtime
     * based on the actual value type.
     *
     * <p>This is used when the source type is not known at property metadata
     * creation time (e.g., Object or polymorphic types).
     *
     * @param conversionService the conversion service to use
     * @param targetType the target type
     * @return a dynamic converter
     */
    public static ValueConverter dynamic(ConversionService conversionService, Class<?> targetType) {
        return new DynamicConverter(conversionService, targetType);
    }

    /**
     * Identity converter that returns values unchanged.
     */
    private static final class IdentityConverter implements ValueConverter {
        static final IdentityConverter INSTANCE = new IdentityConverter();

        @Override
        public Object convert(Object value) {
            return value;
        }
    }

    /**
     * Converter that delegates to Spring's ConversionService.
     *
     * <p>Pre-validated to support the conversion, so no runtime checks needed.
     */
    private static final class ConversionServiceConverter implements ValueConverter {
        private final ConversionService conversionService;
        private final Class<?> targetType;

        ConversionServiceConverter(ConversionService conversionService, Class<?> targetType) {
            this.conversionService = conversionService;
            this.targetType = targetType;
        }

        @Override
        public Object convert(Object value) {
            if (value == null) {
                return null;
            }
            if (targetType.isInstance(value)) {
                return value;
            }
            return conversionService.convert(value, targetType);
        }
    }

    /**
     * Dynamic converter that checks type compatibility at runtime.
     *
     * <p>Used when the source type is not known at metadata creation time.
     */
    private static final class DynamicConverter implements ValueConverter {
        private final ConversionService conversionService;
        private final Class<?> targetType;

        DynamicConverter(ConversionService conversionService, Class<?> targetType) {
            this.conversionService = conversionService;
            this.targetType = targetType;
        }

        @Override
        public Object convert(Object value) {
            if (value == null) {
                return null;
            }
            if (targetType.isInstance(value)) {
                return value;
            }
            if (conversionService.canConvert(value.getClass(), targetType)) {
                return conversionService.convert(value, targetType);
            }
            return value;
        }
    }
}
