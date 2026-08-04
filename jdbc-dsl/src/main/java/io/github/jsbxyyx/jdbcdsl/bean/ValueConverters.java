package io.github.jsbxyyx.jdbcdsl.bean;

import org.springframework.core.convert.ConversionService;

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
