package org.fourz.RVNKQuests.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every quest component that advances state must carry a {@link PartyBeatContext} (#1986).
 *
 * <p>This is a source scan rather than a behavioural test, and that is deliberate. The failure it
 * guards is <b>an omission in code that does not yet exist</b>: someone adds a thirteenth component,
 * calls the two-argument {@code advanceStateForPlayer}, and party sharing silently does not apply to
 * it. Nothing fails. No test goes red. The quest works perfectly for the player who fired it, and
 * the party feature is quietly absent for that one beat.</p>
 *
 * <p>That is exactly the shape of the original #1982 gap — the context reached 4 of 12 components
 * and the other 8 looked fine. A behavioural test can only cover components it knows about; this one
 * covers the ones nobody has written yet.</p>
 *
 * <p>If this test fails on a new component, the fix is to pass a context describing where the beat
 * happened — not to add the file to an exclusion list.</p>
 */
@DisplayName("Party context coverage (#1986)")
class PartyContextCoverageTest {

    /** Component packages whose classes advance quest state on a player's behalf. */
    private static final List<String> COMPONENT_PACKAGES = List.of(
        "src/main/java/org/fourz/RVNKQuests/objective/generic",
        "src/main/java/org/fourz/RVNKQuests/trigger/generic",
        "src/main/java/org/fourz/RVNKQuests/trigger/lectern");

    /**
     * A two-argument {@code advanceStateForPlayer(x, y)} call — the context-less overload.
     *
     * <p>Matches a call whose argument list contains no top-level comma beyond the first, which is
     * what distinguishes {@code (uuid, state)} from {@code (uuid, state, ctx)}. Written to tolerate
     * the argument spanning lines, since several call sites wrap.</p>
     */
    private static final Pattern CONTEXT_LESS_ADVANCE =
        Pattern.compile("advanceStateForPlayer\\s*\\(\\s*[^,()]+,\\s*[^,()]+\\)");

    private static List<Path> componentSources() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        List<Path> out = new ArrayList<>();
        for (String pkg : COMPONENT_PACKAGES) {
            Path dir = moduleRoot.resolve(pkg);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            }
        }
        return out;
    }

    @Test
    @DisplayName("the component packages are actually found — guards against a silent zero-file pass")
    void sourcesAreDiscovered() throws IOException {
        List<Path> sources = componentSources();

        // Without this, a moved package would make every assertion below vacuously true: the scan
        // would find nothing, iterate nothing, and report success. 12 is the count at the time of
        // #1986; the assertion is >= so adding components does not fail it.
        assertTrue(sources.size() >= 12,
            "expected at least 12 component sources, found " + sources.size()
                + " — did a package move? A zero-file scan passes every other test in this class.");
    }

    @Test
    @DisplayName("no component advances state without a party context")
    void everyComponentPassesAContext() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path source : componentSources()) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            Matcher m = CONTEXT_LESS_ADVANCE.matcher(body);
            while (m.find()) {
                offenders.add(source.getFileName() + ": " + m.group().replaceAll("\\s+", " "));
            }
        }

        assertTrue(offenders.isEmpty(),
            "These components advance quest state without a PartyBeatContext, so party members "
                + "will not share the beat and nothing will report it (#1986):\n  "
                + String.join("\n  ", offenders)
                + "\n\nPass a context describing where the beat happened. A location-less beat is "
                + "legal: use the player's location with radius 0 and let the service's "
                + "min_share_radius floor govern, the same way a kill does.");
    }

    @Test
    @DisplayName("every component references PartyBeatContext at all")
    void everyComponentImportsTheContext() throws IOException {
        List<String> missing = new ArrayList<>();

        for (Path source : componentSources()) {
            String body = Files.readString(source, StandardCharsets.UTF_8);
            // Only components that advance state need it. A component that never advances (a pure
            // listener or helper) is legitimately exempt.
            if (!body.contains("advanceStateForPlayer")) {
                continue;
            }
            if (!body.contains("PartyBeatContext")) {
                missing.add(source.getFileName().toString());
            }
        }

        assertTrue(missing.isEmpty(),
            "These components advance state but never mention PartyBeatContext: " + missing);
    }
}
