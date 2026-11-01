package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.port.ExternalServerProcess;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandResult;
import com.aresstack.ollamamanager.infrastructure.hardware.CommandRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExternalServerControlTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void parsesWindowsListenerWithTrayAppAsSupervisor() {
        ExternalServerProcess process = WindowsExternalServerControl.parse("{\"pid\":5120,\"name\":\"ollama.exe\","
            + "\"path\":\"C:\\\\Users\\\\a\\\\AppData\\\\Local\\\\Programs\\\\Ollama\\\\ollama.exe\",\"ppid\":4100,"
            + "\"pname\":\"ollama app.exe\"}");

        assertEquals(5120, process.pid());
        assertTrue(process.isOllama());
        assertTrue(process.hasOllamaSupervisor());
        assertEquals(4100, process.supervisorPid());
        assertTrue(process.executable().getPath().endsWith("ollama.exe"));
    }

    @Test
    public void windowsWithoutListenerOrForeignParentIsReportedAsSuch() {
        assertNull(WindowsExternalServerControl.parse("{}"));
        assertNull(WindowsExternalServerControl.parse("not json"));
        ExternalServerProcess manual = WindowsExternalServerControl.parse(
            "{\"pid\":10,\"name\":\"ollama.exe\",\"path\":null,\"ppid\":9,\"pname\":\"cmd.exe\"}");
        assertFalse(manual.hasOllamaSupervisor());
        assertNull(manual.executable());
    }

    @Test
    public void findScriptQueriesOnlyTheGivenPort() {
        assertTrue(WindowsExternalServerControl.findScript(11434).contains("-LocalPort 11434 "));
    }

    @Test
    public void linuxFindsListenerFromSsAndProc() throws Exception {
        File proc = folder.newFolder("proc");
        File entry = new File(proc, "321");
        assertTrue(entry.mkdir());
        Files.write(new File(entry, "comm").toPath(), "ollama\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(entry, "stat").toPath(),
            "321 (ollama (x)) S 77 321 321 0 -1".getBytes(StandardCharsets.UTF_8));
        File parent = new File(proc, "77");
        assertTrue(parent.mkdir());
        Files.write(new File(parent, "comm").toPath(), "bash\n".getBytes(StandardCharsets.UTF_8));
        FakeRunner runner = new FakeRunner(CommandResult.completed(0,
            "LISTEN 0 4096 127.0.0.1:11434 0.0.0.0:* users:((\"ollama\",pid=321,fd=3))\n"));

        ExternalServerProcess process = new LinuxExternalServerControl(runner, proc)
            .find(com.aresstack.ollamamanager.domain.server.Endpoint.DEFAULT, CancellationToken.none());

        assertEquals(321, process.pid());
        assertEquals("ollama", process.name());
        assertEquals(77, process.supervisorPid());
        assertFalse(process.hasOllamaSupervisor());
        assertTrue(runner.commands.get(0).contains("sport = :11434"));
    }

    @Test
    public void linuxFallsBackToLsofAndReportsNothingWithoutListener() throws Exception {
        File proc = folder.newFolder("proc2");
        FakeRunner empty = new FakeRunner(CommandResult.notFound());
        assertNull(new LinuxExternalServerControl(empty, proc)
            .find(com.aresstack.ollamamanager.domain.server.Endpoint.DEFAULT, CancellationToken.none()));
        assertEquals(2, empty.commands.size());

        FakeRunner lsof = new FakeRunner(CommandResult.completed(1, ""), CommandResult.completed(0, "555\n"));
        assertEquals(555, new LinuxExternalServerControl(lsof, proc).listenerPid(11434, CancellationToken.none()));
    }

    private static final class FakeRunner implements CommandRunner {
        final List<List<String>> commands = new ArrayList<List<String>>();
        private final CommandResult[] results;

        FakeRunner(CommandResult... results) {
            this.results = results;
        }

        @Override
        public CommandResult run(List<String> command, long timeoutMillis, CancellationToken cancellation) {
            commands.add(command);
            return results[Math.min(commands.size() - 1, results.length - 1)];
        }
    }
}
