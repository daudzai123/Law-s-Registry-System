package com.mcit.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BackupProcessRunnerTest {
    @Test void missingToolExplainsHowToFixConfiguration() {
        BackupProcessRunner runner = new BackupProcessRunner();
        ReflectionTestUtils.setField(runner, "password", "test-only");
        ReflectionTestUtils.setField(runner, "timeoutSeconds", 5L);
        IOException error = assertThrows(IOException.class,
                () -> runner.run(List.of("nonexistent-lawmis-postgres-tool-837452")));
        assertTrue(error.getMessage().contains("PG_DUMP_PATH"));
        assertFalse(error.getMessage().contains("test-only"));
    }
}
