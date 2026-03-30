package com.jpadsl.core;

import java.util.Collection;

/**
 * Encapsulates query condition parameters.
 */
public class Criteria {
    private final String field;
    private final CriteriaType type;
    private final Object value;
    private final Object secondValue;
    private final Collection<?> values;

    public Criteria(String field, CriteriaType type, Object value) {
        this.field = field;
        this.type = type;
        this.value = value;
        this.secondValue = null;
        this.values = null;
    }

    public Criteria(String field, CriteriaType type, Object value, Object secondValue) {
        this.field = field;
        this.type = type;
        this.value = value;
        this.secondValue = secondValue;
        this.values = null;
    }

    public Criteria(String field, CriteriaType type, Collection<?> values) {
        this.field = field;
        this.type = type;
        this.value = null;
        this.secondValue = null;
        this.values = values;
    }

    public Criteria(String field, CriteriaType type) {
        this.field = field;
        this.type = type;
        this.value = null;
        this.secondValue = null;
        this.values = null;
    }

    public String getField() { return field; }
    public CriteriaType getType() { return type; }
    public Object getValue() { return value; }
    public Object getSecondValue() { return secondValue; }
    public Collection<?> getValues() { return values; }
}
