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
class DeviceEnrollmentSchemaInitializerTest {

    @Mock
    private JdbcOperations jdbcTemplate;

    @Test
    void initializeAddsOnlyMissingEnrollmentColumnsAndIndexes() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0, 0, 0, 0, 0, 0, 0);

        new DeviceEnrollmentSchemaInitializer(jdbcTemplate).initialize();

        verify(jdbcTemplate).execute(DeviceEnrollmentSchemaInitializer.ADD_TOKEN_HASH_SQL);
        verify(jdbcTemplate).execute(DeviceEnrollmentSchemaInitializer.ADD_TOKEN_SEED_SQL);
        verify(jdbcTemplate).execute(DeviceEnrollmentSchemaInitializer.ADD_FINGERPRINT_SQL);
        verify(jdbcTemplate).execute(DeviceEnrollmentSchemaInitializer.ADD_ENROLLED_AT_SQL);
        verify(jdbcTemplate).execute(DeviceEnrollmentSchemaInitializer.ADD_TOKEN_INDEX_SQL);
        verify(jdbcTemplate).execute(DeviceEnrollmentSchemaInitializer.ADD_FINGERPRINT_INDEX_SQL);
        verify(jdbcTemplate, never()).update(DeviceEnrollmentSchemaInitializer.BACKFILL_FINGERPRINT_SQL);
    }

    @Test
    void initializeIsRepeatableWhenSchemaAlreadyExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(1, 1, 1, 1, 1, 1, 1);

        new DeviceEnrollmentSchemaInitializer(jdbcTemplate).initialize();

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate).update(DeviceEnrollmentSchemaInitializer.BACKFILL_FINGERPRINT_SQL);
    }
}
