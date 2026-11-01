package com.aresstack.ollamamanager.swing.section;

import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** The settings file of the askai-java8 model installer. */
final class ModelInstallConfiguration {

    static final String BASE_URL_KEY = "ollama.baseUrl";

    private ModelInstallConfiguration() {
    }

    /** Store the running server as the installer's Ollama URL, keeping every other askai-java8 setting. */
    static void targetServer(File configurationFile, Endpoint endpoint) {
        Properties properties = new Properties();
        if (configurationFile.isFile()) {
            try (InputStream in = new FileInputStream(configurationFile)) {
                properties.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + configurationFile, e);
            }
        }
        properties.setProperty(BASE_URL_KEY, endpoint.baseUrl());
        File directory = configurationFile.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new UncheckedIOException(new IOException("cannot create " + directory));
        }
        try (OutputStream out = new FileOutputStream(configurationFile)) {
            properties.store(out, "Model installer settings (askai-java8)");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + configurationFile, e);
        }
    }
}
