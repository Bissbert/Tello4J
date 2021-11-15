package ch.bissbert.command.creator;


import ch.bissbert.CommandStrings;
import ch.bissbert.command.model.BasicCommand;
import ch.bissbert.command.model.Command;
import ch.bissbert.command.model.ComplexCommand;
import ch.bissbert.connection.Connection;
import ch.bissbert.connection.Drone;

import java.io.IOException;

public abstract class CommandExecutor {

    protected static Connection connection;

    protected Command command;

    protected CommandExecutor after;

    protected Reference<String> reference;

    public static CommandExecutor execute(CommandStrings command) {
        return new BasicExecutor(command);
    }

    public static CommandExecutor execute(String command) {
        return new BasicExecutor(command);
    }

    public static void closeConnection(){
        if (connection != null){
            connection.close();
        }
    }

    public abstract CommandExecutor withParam(Object object);

    public CommandExecutor withReadAndSaveIn(Reference<String> reference) {
        command.setRead(true);
        this.reference = reference;
        return this;
    }

    public void createConnection() {
        if (connection == null) {
            connection = new Connection(Drone.host, Drone.port);
        }
    }


    public void run() throws IOException {
        if (command.isRead()) {
            String answer = connection.sendCommandAndFetchData(command);
            reference.setReference(answer);
        }else {
            connection.sendCommand(command);
        }
        after.run();
    }

    public CommandExecutor andThen(CommandExecutor executor) {
        this.after = executor;
        return this;
    }

    private static class BasicExecutor extends CommandExecutor {
        private BasicExecutor(String command) {
            this.command = new BasicCommand(command, false);
        }

        private BasicExecutor(CommandStrings commandStrings) {
            this(commandStrings.getCommand());
        }

        @Override
        public CommandExecutor withParam(Object object) {
            CommandExecutor executor = new ComplexExecutor(this);
            executor.withParam(object);
            return executor;
        }

        @Override
        public void run() throws IOException {
            super.run();
        }
    }

    private static class ComplexExecutor extends CommandExecutor {
        private ComplexCommand command;

        public ComplexExecutor(BasicExecutor basicExecutor) {
            this.command = new ComplexCommand((BasicCommand) basicExecutor.command);
        }

        @Override
        public CommandExecutor withParam(Object object) {
            command.addParam(object);
            return this;
        }

        @Override
        public void run() throws IOException {
            super.run();
        }
    }
}
