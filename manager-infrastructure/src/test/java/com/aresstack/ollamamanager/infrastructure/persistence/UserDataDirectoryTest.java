package com.aresstack.ollamamanager.infrastructure.persistence;

import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class UserDataDirectoryTest {

    @Test
    public void usesLocalAppDataOnWindows() {
        File directory = UserDataDirectory.resolve("Windows 11",
            Collections.singletonMap("LOCALAPPDATA", "C:\\Users\\a\\AppData\\Local"), "C:\\Users\\a");

        assertEquals(new File(new File("C:\\Users\\a\\AppData\\Local", "AresStack"), "OllamaManager"), directory);
    }

    @Test
    public void usesHiddenHomeFolderElsewhere() {
        assertEquals(new File("/home/a", ".ollama-manager"),
            UserDataDirectory.resolve("Linux", Collections.<String, String>emptyMap(), "/home/a"));
    }
}
