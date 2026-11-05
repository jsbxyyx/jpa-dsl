package io.github.jsbxyyx.jdbcdsl.bench;

import jakarta.persistence.Table;

@Table(name = "jpa_dsl_bench_rows")
public class BenchmarkRow {
    private Long id;
    private String name;
    private Integer amount;

    public BenchmarkRow() {}

    public BenchmarkRow(Long id, String name, Integer amount) {
        this.id = id;
        this.name = name;
        this.amount = amount;
    }

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

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }
}
