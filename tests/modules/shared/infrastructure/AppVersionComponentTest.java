package modules.shared.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.shared.infrastructure.components.appversion.AppVersionComponent;

/**
 * El pie ensena la version del pom.xml: Maven la filtra en app-version.properties al compilar.
 */
final class AppVersionComponentTest extends AbstractTestCase {

    private static final Pattern POM_VERSION_PATTERN = Pattern.compile("<version>([^<]+)</version>");

    @Test
    void testVersionIsTheOneInThePom() throws IOException {
        Matcher pomVersionMatcher = POM_VERSION_PATTERN.matcher(Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8));
        assertTrue(pomVersionMatcher.find());

        assertEquals(pomVersionMatcher.group(1).trim(), AppVersionComponent.getInstance().getVersion());
    }
}
