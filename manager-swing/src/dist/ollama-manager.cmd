@echo off
rem Start Ollama Manager with the Java runtime found on PATH (Java 8 or newer).
javaw -jar "%~dp0ollama-manager.jar" %*
