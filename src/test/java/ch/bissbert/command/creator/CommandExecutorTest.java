package ch.bissbert.command.creator;

import ch.bissbert.CommandStrings;
import ch.bissbert.connection.Connection;
import ch.bissbert.testutil.FakeDrone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandExecutorTest {

    private FakeDrone drone;

    @BeforeEach
    void start() throws Exception {
        drone = new FakeDrone();
        CommandExecutor.connection = new Connection(FakeDrone.HOST, drone.port(), 2000);
    }

    @AfterEach
    void stop() throws Exception {
        CommandExecutor.closeConnection();
        CommandExecutor.connection = null;
        drone.close();
    }

    @Test
    void runSendsTheCommand() throws Exception {
        CommandExecutor.execute(CommandStrings.COMMAND).run();
        assertEquals(List.of("command"), drone.awaitReceived(1));
    }

    @Test
    void runWithoutAConnectionThrows() {
        CommandExecutor.connection = null;
        assertThrows(NullPointerException.class, () -> CommandExecutor.execute("command").run());
    }

    @Test
    void chainRunsInOrderAndStopsAtTheEnd() throws Exception {
        CommandExecutor.execute(CommandStrings.COMMAND)
                .andThen(CommandExecutor.execute(CommandStrings.TAKE_OFF)
                        .andThen(CommandExecutor.execute(CommandStrings.LAND)))
                .run();
        assertEquals(List.of("command", "takeoff", "land"), drone.awaitReceived(3));
    }

    @Test
    void readStoresTheReplyInTheReference() throws Exception {
        drone.reply("battery?", "87");
        Reference<String> battery = new Reference<>();
        CommandExecutor.execute("battery?").withReadAndSaveIn(battery).run();
        assertEquals("87", battery.getReference());
    }

    // Found while writing these tests: ComplexExecutor shadowed the command
    // field, so run() on an executor with parameters threw NullPointerException.

    @Test
    void withParamSendsTheParameters() throws Exception {
        CommandExecutor.execute("forward").withParam(20).run();
        assertEquals(List.of("forward 20"), drone.awaitReceived(1));
    }

    @Test
    void withParamTakesSeveralParameters() throws Exception {
        CommandExecutor.execute("go").withParam(20).withParam(30).withParam(40).withParam(50).run();
        assertEquals(List.of("go 20 30 40 50"), drone.awaitReceived(1));
    }

    @Test
    void withParamCanRead() throws Exception {
        drone.reply("cw 90", "ok");
        Reference<String> reply = new Reference<>();
        CommandExecutor.execute("cw").withParam(90).withReadAndSaveIn(reply).run();
        assertEquals("ok", reply.getReference());
    }

    @Test
    void withParamKeepsTheSuccessor() throws Exception {
        CommandExecutor.execute("forward")
                .andThen(CommandExecutor.execute(CommandStrings.LAND))
                .withParam(20)
                .run();
        assertEquals(List.of("forward 20", "land"), drone.awaitReceived(2));
    }

    @Test
    void withParamInsideAChain() throws Exception {
        CommandExecutor.execute(CommandStrings.TAKE_OFF)
                .andThen(CommandExecutor.execute("up").withParam(50)
                        .andThen(CommandExecutor.execute(CommandStrings.LAND)))
                .run();
        assertEquals(List.of("takeoff", "up 50", "land"), drone.awaitReceived(3));
    }

    @Test
    void closeConnectionWithoutAConnectionIsANoOp() {
        CommandExecutor.connection = null;
        assertDoesNotThrow(CommandExecutor::closeConnection);
    }
}
