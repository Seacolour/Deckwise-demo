package com.xiaoyuan.paperpuppybackendintl.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DotenvTestEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesEnvFileSyntaxUsedByTheRepository() throws Exception {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                # comment
                PLAIN=value
                DOUBLE_QUOTED="quoted value"
                SINGLE_QUOTED='literal value'
                export EXPORTED=available
                EMPTY=
                ESCAPED="line\\nnext"
                """);

        Map<String, Object> values = DotenvTestEnvironmentPostProcessor.load(dotenv);

        assertEquals("value", values.get("PLAIN"));
        assertEquals("quoted value", values.get("DOUBLE_QUOTED"));
        assertEquals("literal value", values.get("SINGLE_QUOTED"));
        assertEquals("available", values.get("EXPORTED"));
        assertEquals("", values.get("EMPTY"));
        assertEquals("line\nnext", values.get("ESCAPED"));
    }

    @Test
    void parseFailureReportsOnlyLineNumberAndNeverRawSecret() throws Exception {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, "VALID=value\nBROKEN_SECRET\n");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DotenvTestEnvironmentPostProcessor.load(dotenv));

        assertEquals("Invalid test .env entry at line 2", error.getMessage());
    }
}
