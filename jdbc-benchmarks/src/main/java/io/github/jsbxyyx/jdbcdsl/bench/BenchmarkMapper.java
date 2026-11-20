package io.github.jsbxyyx.jdbcdsl.bench;

import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface BenchmarkMapper {
    @SelectProvider(type = BenchmarkMapperSql.class, method = "selectRows")
    List<BenchmarkRow> selectRows(@Param("table") String table, @Param("max") int max);

    @SelectProvider(type = BenchmarkMapperSql.class, method = "selectRows")
    @Results(
            id = "benchmarkRowResultMap",
            value = {
                @Result(column = "id", property = "id", id = true),
                @Result(column = "name", property = "name"),
                @Result(column = "amount", property = "amount")
            })
    List<BenchmarkRow> selectRowsResultMap(@Param("table") String table, @Param("max") int max);

    @InsertProvider(type = BenchmarkMapperSql.class, method = "insertWrite")
    int insertWrite(@Param("table") String table, @Param("row") BenchmarkWrite row);
}
