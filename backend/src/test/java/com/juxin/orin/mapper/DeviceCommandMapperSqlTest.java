package com.juxin.orin.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceCommandMapperSqlTest {

    @Test
    void groupQueryAggregatesGroupKeyForOnlyFullGroupBy() throws NoSuchMethodException {
        String sql = selectSql("selectGroupPage", Page.class, String.class, String.class, String.class);

        assertTrue(sql.contains("MIN(" + DeviceCommandMapper.GROUP_KEY_SQL + ") AS groupKey"));
    }

    @Test
    void groupRecordsUseTheSameGroupKeyExpression() throws NoSuchMethodException {
        String sql = selectSql("selectGroupRecords", Page.class, String.class, String.class, String.class);

        assertTrue(sql.contains("WHERE " + DeviceCommandMapper.GROUP_KEY_SQL + " = #{groupKey}"));
    }

    private String selectSql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = DeviceCommandMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }
}
