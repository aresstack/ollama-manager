package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner(Charset.forName("UTF-8"));

    @Test
    public void missingExecutableIsNotFound() throws Exception {
        assertFalse(runner.run(Arrays.asList("definitely-not-a-tool-4711"), 1000, CancellationToken.none())
            .isFound());
    }

    @Test
    public void capturesOutputOfRealProcess() throws Exception {
        CommandResult result = runner.run(javaCommand("-version"), 30000, CancellationToken.none());
        assertTrue(result.isSuccess());
    }

    @Test
    public void timesOutAndDestroysProcess() throws Exception {
        boolean windows = File.separatorChar == '\\';
        Assume.assumeTrue(windows || new File("/bin/sleep").exists());
        try {
            runner.run(windows ? Arrays.asList("ping", "-n", "30", "127.0.0.1") : Arrays.asList("/bin/sleep", "30"),
                300, CancellationToken.none());
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.TIMEOUT, e.kind());
        }
    }

    private static java.util.List<String> javaCommand(String argument) {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        return Arrays.asList(java, argument);
    }
}
