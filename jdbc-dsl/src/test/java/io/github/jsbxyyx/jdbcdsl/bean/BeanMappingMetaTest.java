package io.github.jsbxyyx.jdbcdsl.bean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BeanMappingMeta implementations.
 */
class BeanMappingMetaTest {

    // Test JavaBean with standard getters/setters
    public static class SimpleBean {
        private Long id;
        private String name;
        private Integer age;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    // Test JavaBean with public fields (no setters)
    public static class FieldBean {
        public Long id;
        public String name;
        private String email; // private field with no setter

        public String getEmail() {
            return email;
        }
    }

    // Test Record type (Java 14+)
    public record SimpleRecord(Long id, String name, Integer age) {}

    private record PrivateRecord(String name) {}

    private record AmbiguousRecord(String name, String NAME) {}

    private record WidePrimitiveRecord(long value) {}

    private record AllPrimitivesRecord(boolean a, byte b, short c, char d, int e, long f, float g, double h) {}

    @Test
    void testJavaBeanMapping() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        // Test newInstance
        Object instance = meta.newInstance();
        assertNotNull(instance);
        assertTrue(instance instanceof SimpleBean);

        // Test setProperty
        meta.setProperty(instance, "id", 1L);
        meta.setProperty(instance, "name", "John");
        meta.setProperty(instance, "age", 30);

        SimpleBean bean = (SimpleBean) instance;
        assertEquals(1L, bean.getId());
        assertEquals("John", bean.getName());
        assertEquals(30, bean.getAge());
    }

    @Test
    void testCaseInsensitivePropertyMatching() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        Object instance = meta.newInstance();

        // Test various case combinations
        meta.setProperty(instance, "ID", 1L);
        meta.setProperty(instance, "NAME", "John");
        meta.setProperty(instance, "AGE", 30);

        SimpleBean bean = (SimpleBean) instance;
        assertEquals(1L, bean.getId());
        assertEquals("John", bean.getName());
        assertEquals(30, bean.getAge());
    }

    @Test
    void testFieldFallback() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(FieldBean.class);

        Object instance = meta.newInstance();

        // Set public fields directly
        meta.setProperty(instance, "id", 1L);
        meta.setProperty(instance, "name", "John");

        FieldBean bean = (FieldBean) instance;
        assertEquals(1L, bean.id);
        assertEquals("John", bean.name);
    }

    @Test
    void testUnknownPropertyIgnored() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        Object instance = meta.newInstance();

        // Unknown properties should be silently ignored
        assertDoesNotThrow(() -> meta.setProperty(instance, "unknownProperty", "value"));
    }

    @Test
    void testTypeMismatchIsNotConvertedByBeanMeta() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        Object instance = meta.newInstance();

        // BeanMappingMeta assigns values but does not perform type conversion.
        assertThrows(RuntimeException.class, () -> meta.setProperty(instance, "id", 1));
    }

    @Test
    void testRecordMappingMetaDirectly() {
        BeanMappingMeta meta = new RecordMappingMeta(SimpleRecord.class);

        assertTrue(meta instanceof RecordMappingMeta);
        assertEquals(SimpleRecord.class, meta.getType());

        // Test hasProperty
        assertTrue(meta.hasProperty("id"));
        assertTrue(meta.hasProperty("name"));
        assertTrue(meta.hasProperty("age"));
        assertFalse(meta.hasProperty("unknown"));
    }

    @Test
    void testRecordInstantiation() {
        RecordMappingMeta meta = new RecordMappingMeta(SimpleRecord.class);
        assertThrows(UnsupportedOperationException.class, meta::newInstance);
        assertThrows(IllegalArgumentException.class, () -> meta.newInstance(Map.of("id", 1L)));

        Map<String, Object> values = new HashMap<>();
        values.put("id", 1L);
        values.put("name", "John");
        values.put("age", 30);

        Object instance = meta.newInstance(values);
        assertNotNull(instance);
        assertTrue(instance instanceof SimpleRecord);

        SimpleRecord record = (SimpleRecord) instance;
        assertEquals(1L, record.id());
        assertEquals("John", record.name());
        assertEquals(30, record.age());
    }

    @Test
    void recordInstantiationFromMapPreservesReflectionPrimitiveWidening() {
        RecordMappingMeta meta = new RecordMappingMeta(WidePrimitiveRecord.class);
        assertEquals(new WidePrimitiveRecord(7L), meta.newInstance(Map.of("value", 7)));
    }

    @Test
    void mappedRecordConstructorUnboxesEveryPrimitiveType() {
        RecordMappingMeta meta = new RecordMappingMeta(AllPrimitivesRecord.class);
        assertEquals(
                new AllPrimitivesRecord(true, (byte) 1, (short) 2, 'x', 3, 4L, 5F, 6D),
                meta.newMappedInstance(new Object[] {true, (byte) 1, (short) 2, 'x', 3, 4L, 5F, 6D}));
    }

    @Test
    void mappedConstructorSupportsMaximumArityRecords(@TempDir Path dir) throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            boolean wide = variant != 0;
            String name = "WideRecord" + variant;
            int count = variant == 0 ? 254 : variant == 1 ? 128 : 127;
            String parameters = IntStream.range(0, count)
                    .mapToObj(i -> (wide && i < 126 ? "long" : "int") + " p" + i)
                    .collect(Collectors.joining(","));
            Path source = dir.resolve(name + ".java");
            Files.writeString(source, "public record " + name + "(" + parameters + ") {}");
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, source.toString()));
            try (URLClassLoader loader =
                    new URLClassLoader(new URL[] {dir.toUri().toURL()})) {
                Class<?> recordType = loader.loadClass(name);
                RecordMappingMeta meta = new RecordMappingMeta(recordType);
                Object[] values = new Object[count];
                Arrays.fill(values, 7);
                if (wide) {
                    Arrays.fill(values, 0, 126, 7L);
                }
                Object record = meta.newMappedInstance(values);
                assertEquals(recordType, record.getClass());
                if (wide) {
                    assertEquals(7L, meta.getProperty(record, "p0"));
                } else {
                    assertEquals(7, meta.getProperty(record, "p0"));
                }
            }
        }
    }

    @Test
    void testRecordCaseInsensitive() {
        RecordMappingMeta meta = new RecordMappingMeta(SimpleRecord.class);

        Map<String, Object> values = new HashMap<>();
        values.put("ID", 1L);
        values.put("NAME", "John");
        values.put("AGE", 30);

        Object instance = meta.newInstance(values);
        SimpleRecord record = (SimpleRecord) instance;
        assertEquals(1L, record.id());
        assertEquals("John", record.name());
        assertEquals(30, record.age());
    }

    @Test
    void testRecordSetPropertyThrows() {
        BeanMappingMeta meta = new RecordMappingMeta(SimpleRecord.class);

        Map<String, Object> values = new HashMap<>();
        values.put("id", 1L);
        values.put("name", "John");
        values.put("age", 30);

        RecordMappingMeta recordMeta = (RecordMappingMeta) meta;
        Object instance = recordMeta.newInstance(values);

        // Records are immutable, setProperty should throw
        assertThrows(UnsupportedOperationException.class, () -> meta.setProperty(instance, "name", "Jane"));
    }

    @Test
    void testHasProperty() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        assertTrue(meta.hasProperty("id"));
        assertTrue(meta.hasProperty("name"));
        assertTrue(meta.hasProperty("age"));
        assertFalse(meta.hasProperty("unknown"));

        // Case insensitive
        assertTrue(meta.hasProperty("ID"));
        assertTrue(meta.hasProperty("NAME"));
    }

    @Test
    void testFactorySupportsRecord() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        assertInstanceOf(RecordMappingMeta.class, factory.create(SimpleRecord.class));
        BeanMappingMeta privateMeta = factory.create(PrivateRecord.class);
        assertEquals("Alice", privateMeta.getProperty(new PrivateRecord("Alice"), "NAME"));
        assertThrows(IllegalArgumentException.class, () -> factory.create(AmbiguousRecord.class));
    }

    @Test
    void testGetType() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();

        BeanMappingMeta beanMeta = factory.create(SimpleBean.class);
        assertEquals(SimpleBean.class, beanMeta.getType());

        BeanMappingMeta recordMeta = new RecordMappingMeta(SimpleRecord.class);
        assertEquals(SimpleRecord.class, recordMeta.getType());
    }

    @Test
    void testNullValues() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        Object instance = meta.newInstance();

        // Null values should be handled gracefully
        assertDoesNotThrow(() -> {
            meta.setProperty(instance, "id", null);
            meta.setProperty(instance, "name", null);
            meta.setProperty(instance, "age", null);
        });

        SimpleBean bean = (SimpleBean) instance;
        assertNull(bean.getId());
        assertNull(bean.getName());
        assertNull(bean.getAge());
    }
}
