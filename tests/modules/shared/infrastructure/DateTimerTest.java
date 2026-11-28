package modules.shared.infrastructure;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.shared.infrastructure.components.date.DateTimer;

/**
 * La fecha-hora compacta de los nombres de fichero exportados (history-yyMMddHHmmss.sql).
 */
final class DateTimerTest extends AbstractTestCase {

    @Test
    void testCompactDateTimeHasTwelveDigitsAndMatchesTheLongOne() {
        DateTimer dateTimer = DateTimer.getInstance();
        String longDateTime = dateTimer.getNowDt();
        String compactDateTime = dateTimer.getNowCompactDt();

        assertTrue(compactDateTime.matches("[0-9]{12}"));
        assertEquals(longDateTime.substring(2, 10).replace("-", ""), compactDateTime.substring(0, 6));
    }
}
