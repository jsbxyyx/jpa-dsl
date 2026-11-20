package io.github.jsbxyyx.jdbcdsl.bench;

/** POJO with the same mapped shape as BenchmarkRow, without JPA annotations. */
public class JooqBenchmarkRow {
    private Long id;
    private String name;
    private Integer amount;

    public JooqBenchmarkRow() {}

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
