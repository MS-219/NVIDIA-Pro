package com.juxin.orin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserEarningsSchemaInitializerTest {

    @Mock
    private JdbcOperations jdbcTemplate;

    @Test
    void initializeAddsMissingUserEarningsColumns() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0, 0);

        new AppUserEarningsSchemaInitializer(jdbcTemplate).initialize();

        verify(jdbcTemplate).execute(AppUserEarningsSchemaInitializer.ADD_MIN_AMOUNT_SQL);
        verify(jdbcTemplate).execute(AppUserEarningsSchemaInitializer.ADD_MAX_AMOUNT_SQL);
    }

    @Test
    void initializeIsRepeatableWhenColumnsAlreadyExist() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(1, 1);

        new AppUserEarningsSchemaInitializer(jdbcTemplate).initialize();

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
