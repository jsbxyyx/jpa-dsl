package io.github.jsbxyyx.jdbcdsl.bench;

import jakarta.persistence.Table;

@Table(name = "jpa_dsl_bench_writes")
public class BenchmarkWrite extends BenchmarkRow {
    public BenchmarkWrite() {}

    public BenchmarkWrite(Long id, String name, Integer amount) {
        super(id, name, amount);
    }
}
