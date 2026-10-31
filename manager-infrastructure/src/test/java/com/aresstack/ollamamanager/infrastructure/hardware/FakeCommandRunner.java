package com.aresstack.ollamamanager.infrastructure.hardware;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Returns canned results per executable; unknown executables are "not found". */
final class FakeCommandRunner implements CommandRunner {

    private final Map<String, Object> responses = new HashMap<String, Object>();
    final List<List<String>> calls = new ArrayList<List<String>>();

    FakeCommandRunner respond(String executable, String queryPrefix, CommandResult result) {
        responses.put(executable + " " + queryPrefix, result);
        return this;
    }

    FakeCommandRunner timeout(String executable, String queryPrefix) {
        responses.put(executable + " " + queryPrefix, ErrorKind.TIMEOUT);
        return this;
    }

    /** Match exactly, or by prefix when the key ends with '='. */
    private static boolean matches(String argument, String key) {
        return argument.equals(key) || (key.endsWith("=") && argument.startsWith(key));
    }

    @Override
    public CommandResult run(List<String> command, long timeoutMillis, CancellationToken cancellation)
        throws OperationException {
        cancellation.throwIfCancelled();
        calls.add(command);
        for (Map.Entry<String, Object> entry : responses.entrySet()) {
            String[] key = entry.getKey().split(" ", 2);
            if (command.get(0).equals(key[0]) && matches(command.get(1), key[1])) {
                if (entry.getValue() == ErrorKind.TIMEOUT) {
                    throw new OperationException(ErrorKind.TIMEOUT, "timed out");
                }
                return (CommandResult) entry.getValue();
            }
        }
        return CommandResult.notFound();
    }
}
