package io.github.jsbxyyx.jdbcdsl.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Action taken when a result column has no matching bean property or record component.
 */
public enum UnknownColumnBehavior {
    NONE,
    WARNING,
    FAILING;

    private static final Logger log = LoggerFactory.getLogger(UnknownColumnBehavior.class);

    void handle(String label, Class<?> type) {
        String message = "Result column '" + label + "' has no matching property on " + type.getName();
        if (this == FAILING) {
            throw new IllegalStateException(message);
        }
        if (this == WARNING) {
            log.warn("{}", message);
        }
    }
}
