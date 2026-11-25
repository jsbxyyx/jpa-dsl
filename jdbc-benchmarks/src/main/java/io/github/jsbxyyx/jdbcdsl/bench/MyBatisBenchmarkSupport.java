package io.github.jsbxyyx.jdbcdsl.bench;

import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.List;

final class MyBatisBenchmarkSupport {
    private final SqlSessionFactory sessionFactory;
    private final String rowsTable;
    private final String writesTable;

    MyBatisBenchmarkSupport(DataSource dataSource, String rowsTable, String writesTable) {
        this.rowsTable = rowsTable;
        this.writesTable = writesTable;
        Environment environment = new Environment("benchmark", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(BenchmarkMapper.class);
        sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    List<BenchmarkRow> selectRows(int max) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return session.getMapper(BenchmarkMapper.class).selectRows(rowsTable, max);
        }
    }

    List<BenchmarkRow> selectRowsResultMap(int max) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            return session.getMapper(BenchmarkMapper.class).selectRowsResultMap(rowsTable, max);
        }
    }

    int[] insertBatch(List<BenchmarkWrite> rows) {
        return insertBatch(rows, true);
    }

    int[] insertBatchTransactional(List<BenchmarkWrite> rows) {
        return insertBatch(rows, false);
    }

    private int[] insertBatch(List<BenchmarkWrite> rows, boolean autoCommit) {
        try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, autoCommit)) {
            BenchmarkMapper mapper = session.getMapper(BenchmarkMapper.class);
            for (BenchmarkWrite row : rows) {
                mapper.insertWrite(writesTable, row);
            }
            List<BatchResult> results = session.flushStatements();
            if (!autoCommit) {
                session.commit();
            }
            List<Integer> updateCounts = new ArrayList<>(rows.size());
            for (BatchResult result : results) {
                for (int updateCount : result.getUpdateCounts()) {
                    updateCounts.add(updateCount);
                }
            }
            return updateCounts.stream().mapToInt(Integer::intValue).toArray();
        }
    }
}
