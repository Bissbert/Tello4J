package ch.bissbert.command.model;

import ch.bissbert.CommandStrings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandModelTest {

    @Test
    void basicCommandComposesToItsText() {
        assertEquals("up", new BasicCommand("up", false).compose());
    }

    @Test
    void enumBackedCommandComposesToItsSdkWord() {
        assertEquals("takeoff", new BasicCommand(CommandStrings.TAKE_OFF, false).compose());
    }

    @Test
    void setWifiKeepsThePlaceholderWord() {
        assertEquals("wifi ssid", new BasicCommand(CommandStrings.SET_WIFI, false).compose());
    }

    @Test
    void readFlagIsKeptAndCanBeChanged() {
        BasicCommand c = new BasicCommand("battery?", true);
        assertTrue(c.isRead());
        c.setRead(false);
        assertFalse(c.isRead());
    }

    @Test
    void complexCommandWithoutParamsIsTheBareWord() {
        assertEquals("forward", new ComplexCommand("forward", false).compose());
    }

    @Test
    void complexCommandJoinsParamsWithSpaces() {
        ComplexCommand c = new ComplexCommand("go", false);
        assertTrue(c.addParam(20));
        assertTrue(c.addParam(30));
        assertTrue(c.addParam(40));
        assertTrue(c.addParam(50));
        assertEquals("go 20 30 40 50", c.compose());
    }

    @Test
    void nullParamIsIgnored() {
        ComplexCommand c = new ComplexCommand("forward", false);
        assertFalse(c.addParam(null));
        c.addParam(20);
        assertEquals("forward 20", c.compose());
    }

    @Test
    void eachComplexCommandHasItsOwnParams() {
        ComplexCommand a = new ComplexCommand("forward", false);
        ComplexCommand b = new ComplexCommand("forward", false);
        a.addParam(20);
        assertEquals("forward", b.compose());
    }

    @Test
    void complexCommandCopiesABasicCommand() {
        ComplexCommand c = new ComplexCommand(new BasicCommand("cw", true));
        c.addParam(90);
        assertEquals("cw 90", c.compose());
        assertTrue(c.isRead());
    }

    @Test
    void everyCommandStringIsNonEmpty() {
        for (CommandStrings s : CommandStrings.values()) {
            assertFalse(s.getCommand() == null || s.getCommand().isBlank(), s.name());
        }
    }
}
