package io.github.jsbxyyx.jdbcdsl.bench;

import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface BenchmarkMapper {
    @SelectProvider(type = BenchmarkMapperSql.class, method = "selectRows")
    List<BenchmarkRow> selectRows(@Param("table") String table, @Param("max") int max);

    @InsertProvider(type = BenchmarkMapperSql.class, method = "insertWrite")
    int insertWrite(@Param("table") String table, @Param("row") BenchmarkWrite row);
}
