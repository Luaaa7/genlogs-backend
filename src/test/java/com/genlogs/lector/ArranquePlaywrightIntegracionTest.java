package com.genlogs.lector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Una JVM nueva evita que el driver ya inicializado o una caché poblada oculten descargas. */
@EnabledIfSystemProperty(named = "lector.integration", matches = "true")
class ArranquePlaywrightIntegracionTest {
    @TempDir Path temporary;

    @Test void arranqueConCacheVaciaNoDescargaNavegadoresSinVariablesExternas() throws Exception {
        Path cache = Files.createDirectory(temporary.resolve("browsers"));
        Path log = temporary.resolve("driver.log");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(javaExecutable, "-cp", classpath, Proceso.class.getName());
        builder.environment().remove("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD");
        builder.environment().remove("SELENIUM_REMOTE_URL");
        builder.environment().put("PLAYWRIGHT_BROWSERS_PATH", cache.toString());
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "El arranque no debe esperar instalaciones de navegadores");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("DRIVER_LISTO"), output);
            assertFalse(output.contains("Downloading"), output);
            try (var files = Files.list(cache)) {
                assertTrue(files.allMatch(p -> p.getFileName().toString().equals(".links")),
                        "La inicialización no debe instalar Chromium, Firefox ni WebKit");
            }
        } finally {
            process.descendants().forEach(child -> child.destroyForcibly());
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    public static class Proceso {
        public static void main(String[] args) {
            try (var playwright = LectorChromium.crearPlaywright()) {
                System.out.println("DRIVER_LISTO");
            }
        }
    }
}
