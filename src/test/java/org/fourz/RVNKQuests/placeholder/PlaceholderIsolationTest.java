package org.fourz.RVNKQuests.placeholder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RVNKQuests loads without PlaceholderAPI (#2214).
 *
 * <p>The test classpath includes PlaceholderAPI (provided scope), so a plain class load proves
 * nothing. These tests load the plugin's classes in a class loader that hides every
 * {@code me.clip} class, the situation on a server without PlaceholderAPI. A control case proves
 * the loader really hides it.</p>
 */
@DisplayName("RVNKQuests loads without PlaceholderAPI (#2214)")
class PlaceholderIsolationTest {

    /** Hides PlaceholderAPI; everything else comes from the test classpath, child-first. */
    private static final class NoPapiLoader extends URLClassLoader {
        NoPapiLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("me.clip.")) {
                throw new ClassNotFoundException(name + " (hidden: no PlaceholderAPI)");
            }
            return super.loadClass(name, resolve);
        }
    }

    private static URL[] testClasspath() throws Exception {
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.isBlank()) cp = System.getProperty("java.class.path");
        List<URL> urls = new ArrayList<>();
        for (String entry : cp.split(File.pathSeparator)) {
            if (!entry.isBlank()) urls.add(new File(entry).toURI().toURL());
        }
        return urls.toArray(new URL[0]);
    }

    @Test
    @DisplayName("control: the hiding loader cannot load the expansion (its superclass is PAPI)")
    void controlExpansionNeedsPapi() throws Exception {
        try (NoPapiLoader loader = new NoPapiLoader(testClasspath())) {
            assertThrows(Throwable.class, () ->
                Class.forName("org.fourz.RVNKQuests.placeholder.RVNKQuestsPlaceholderExpansion", true, loader));
        }
    }

    @Test
    @DisplayName("the main class and the registrar load and link without PlaceholderAPI")
    void mainClassAndRegistrarLoad() throws Exception {
        try (NoPapiLoader loader = new NoPapiLoader(testClasspath())) {
            for (String name : List.of(
                    "org.fourz.RVNKQuests.RVNKQuests",
                    "org.fourz.RVNKQuests.placeholder.PlaceholderRegistrar",
                    "org.fourz.RVNKQuests.placeholder.QuestPlaceholderSource",
                    "org.fourz.RVNKQuests.placeholder.QuestPlaceholderResolver",
                    "org.fourz.RVNKQuests.placeholder.QuestStepModel")) {
                Class<?> c = assertDoesNotThrow(() -> Class.forName(name, true, loader), name);
                // Resolves every method signature type, as Bukkit's reflection would.
                assertDoesNotThrow(c::getDeclaredMethods, name + " method signatures");
                assertDoesNotThrow(c::getDeclaredFields, name + " field types");
            }
        }
    }

    @Test
    @DisplayName("only the expansion class references PlaceholderAPI in its bytecode")
    void onlyExpansionReferencesPapi() throws Exception {
        for (Class<?> c : List.of(org.fourz.RVNKQuests.RVNKQuests.class, PlaceholderRegistrar.class,
                QuestPlaceholderSource.class, QuestPlaceholderResolver.class, QuestStepModel.class)) {
            assertFalse(bytecodeOf(c).contains("me/clip/placeholderapi"),
                c.getSimpleName() + " must not link PlaceholderAPI");
        }
        assertTrue(bytecodeOf(RVNKQuestsPlaceholderExpansion.class).contains("me/clip/placeholderapi"),
            "control: the expansion does reference it");
    }

    private static String bytecodeOf(Class<?> c) throws Exception {
        String resource = c.getName().replace('.', '/') + ".class";
        try (InputStream in = c.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
