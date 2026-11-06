package io.github.jsbxyyx.jdbcdsl.dialect;

public class TestDialectProvider implements DialectProvider {
    @Override
    public boolean supports(String databaseProductName) {
        return "MySQL SPI override".equals(databaseProductName);
    }

    @Override
    public Dialect create() {
        return new H2Dialect();
    }
}
