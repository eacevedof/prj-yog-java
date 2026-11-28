package testcase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

import modules.shared.domain.enums.AppEnvKeyEnum;
import modules.shared.infrastructure.factories.DbFactory;

import core.config.Global;

import modules.devops.application.runmigrations.RunMigrationsService;

/**
 * Como en task-java: aserciones con nombre de PHPUnit y una BD propia por clase de test. JAVAYOG_HOME apunta a
 * target/test-home/<clase>: se borra y se migra desde cero en cada clase (nunca toca ~/.javayog).
 */
public abstract class AbstractTestCase {

    protected static void bootFreshDatabase(String testName) {
        DbFactory.getInstance().closeAll();
        Path testHome = Path.of("target", "test-home", testName).toAbsolutePath();
        try {
            Files.deleteIfExists(testHome.resolve("javayog.sqlite"));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Global.putenv(AppEnvKeyEnum.JAVAYOG_HOME, testHome.toString());
        RunMigrationsService.getInstance().invoke();
    }

    protected void assertEquals(Object expected, Object actual) {
        Assertions.assertEquals(expected, actual);
    }

    protected void assertNotEquals(Object notExpected, Object actual) {
        Assertions.assertNotEquals(notExpected, actual);
    }

    protected void assertTrue(boolean condition) {
        Assertions.assertTrue(condition);
    }

    protected void assertFalse(boolean condition) {
        Assertions.assertFalse(condition);
    }

    protected <T extends Throwable> T assertThrows(Class<T> expectedClass, Executable executable) {
        return Assertions.assertThrows(expectedClass, executable);
    }
}
