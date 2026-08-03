package io.github.jsbxyyx.jdbcdsl.bean;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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
    void testTypeConversion() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleBean.class);

        Object instance = meta.newInstance();

        // Test Integer to Long conversion
        meta.setProperty(instance, "id", 1); // Integer instead of Long

        SimpleBean bean = (SimpleBean) instance;
        assertEquals(1L, bean.getId());
    }

    @Test
    void testRecordMapping() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleRecord.class);

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
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        RecordMappingMeta meta = (RecordMappingMeta) factory.create(SimpleRecord.class);

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
    void testRecordCaseInsensitive() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        RecordMappingMeta meta = (RecordMappingMeta) factory.create(SimpleRecord.class);

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
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();
        BeanMappingMeta meta = factory.create(SimpleRecord.class);

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
    void testGetType() {
        BeanMappingMetaFactory factory = new BeanMappingMetaFactory();

        BeanMappingMeta beanMeta = factory.create(SimpleBean.class);
        assertEquals(SimpleBean.class, beanMeta.getType());

        BeanMappingMeta recordMeta = factory.create(SimpleRecord.class);
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
