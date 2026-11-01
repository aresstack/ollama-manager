package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WindowsUserEnvironmentTest {

    @Test
    public void scriptSetsUserVariablesWithQuotedLiterals() {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("OLLAMA_MODELS", "X:\\it's $(evil)");
        variables.put("OLLAMA_NUM_PARALLEL", "4");

        String script = WindowsUserEnvironment.script(variables);

        assertTrue(script.contains("[Environment]::SetEnvironmentVariable('OLLAMA_MODELS','X:\\it''s $(evil)','User');"));
        assertTrue(script.contains("[Environment]::SetEnvironmentVariable('OLLAMA_NUM_PARALLEL','4','User');"));
    }

    @Test
    public void writeRunsPowerShellWithTheEncodedScript() throws Exception {
        final List<List<String>> commands = new ArrayList<List<String>>();
        WindowsUserEnvironment environment = new WindowsUserEnvironment((command, timeout, cancellation) -> {
            commands.add(command);
            return CommandResult.completed(0, "");
        }, true);

        environment.write(Collections.singletonMap("OLLAMA_NUM_PARALLEL", "4"), CancellationToken.none());

        List<String> command = commands.get(0);
        assertEquals("powershell.exe", command.get(0));
        String script = new String(Base64.getDecoder().decode(command.get(command.size() - 1)),
            Charset.forName("UTF-16LE"));
        assertTrue(script.contains("'OLLAMA_NUM_PARALLEL','4','User'"));
    }

    @Test
    public void failureIsReported() {
        WindowsUserEnvironment environment = new WindowsUserEnvironment(
            (command, timeout, cancellation) -> CommandResult.completed(1, "denied"), true);
        try {
            environment.write(Collections.singletonMap("OLLAMA_NUM_PARALLEL", "4"), CancellationToken.none());
            fail();
        } catch (OperationException e) {
            assertTrue(e.getMessage().contains("denied"));
        }
    }

    @Test
    public void otherSystemsAreUnsupported() {
        assertFalse(new WindowsUserEnvironment((command, timeout, cancellation) -> {
            throw new AssertionError("must not run");
        }, false).isSupported());
    }
}
