package io.github.jsbxyyx.jdbcdsl.bean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodHandleAccessorTest {

    @Test
    void usesLambdaMetafactoryForPublicBeanSetters() {
        BeanMappingMeta meta = new BeanMappingMetaFactory().create(PublicBean.class);
        PropertyAccessor accessor = meta.getPropertyAccessor("value");

        MethodHandleAccessor methodHandleAccessor = assertInstanceOf(MethodHandleAccessor.class, accessor);
        assertTrue(methodHandleAccessor.usesLambdaSetter());

        PublicBean bean = new PublicBean();
        accessor.write(bean, "mapped");
        assertEquals("mapped", bean.getValue());
        accessor.write(bean, null);
        assertEquals(null, bean.getValue());

        PropertyAccessor countAccessor = meta.getPropertyAccessor("count");
        assertFalse(((MethodHandleAccessor) countAccessor).usesLambdaSetter());
        countAccessor.write(bean, 42);
        assertEquals(42, bean.getCount());
    }

    @Test
    void usesLambdaMetafactoryForPrivateNestedBeanSetters() {
        BeanMappingMeta meta = new BeanMappingMetaFactory().create(PrivateBean.class);
        PropertyAccessor accessor = meta.getPropertyAccessor("value");

        MethodHandleAccessor methodHandleAccessor = assertInstanceOf(MethodHandleAccessor.class, accessor);
        assertTrue(methodHandleAccessor.usesLambdaSetter());

        PrivateBean bean = (PrivateBean) meta.newInstance();
        accessor.write(bean, "mapped");
        assertEquals("mapped", bean.getValue());
    }

    @Test
    void fallsBackToMethodHandleForBeanTypesOutsideTheAccessorPackage() throws Exception {
        Class<?> beanType = Class.forName("io.github.jsbxyyx.jdbcdsl.bean.fixture.PackagePrivateBean");
        BeanMappingMeta meta = new BeanMappingMetaFactory().create(beanType);
        PropertyAccessor accessor = meta.getPropertyAccessor("value");

        MethodHandleAccessor methodHandleAccessor = assertInstanceOf(MethodHandleAccessor.class, accessor);
        assertFalse(methodHandleAccessor.usesLambdaSetter());

        Object bean = meta.newInstance();
        accessor.write(bean, "fallback");
        var getter = beanType.getMethod("getValue");
        getter.setAccessible(true);
        assertEquals("fallback", getter.invoke(bean));
    }

    public static class PublicBean {
        private String value;
        private int count;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    private static class PrivateBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
