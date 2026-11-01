package com.aresstack.ollamamanager.infrastructure.runtime;

import com.aresstack.ollamamanager.application.port.ExternalServerControlPort;
import com.aresstack.ollamamanager.infrastructure.hardware.ProcessCommandRunner;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Selects the {@link ExternalServerControlPort} adapter for the current operating system.
 */
public final class ExternalServerControls {

    private ExternalServerControls() {
    }

    /** @return the adapter, or {@code null} on systems without one; external servers stay uncontrolled there */
    public static ExternalServerControlPort forCurrentSystem() {
        String os = String.valueOf(System.getProperty("os.name")).toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return new WindowsExternalServerControl(new ProcessCommandRunner(Charset.forName("UTF-8")));
        }
        if (os.startsWith("linux")) {
            return new LinuxExternalServerControl(new ProcessCommandRunner(Charset.defaultCharset()));
        }
        return null;
    }
}
