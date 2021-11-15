package ch.bissbert.command.model;


import ch.bissbert.CommandStrings;

public abstract class AbstractCommand implements Command {

    private boolean isRead;
    private String command;

    public AbstractCommand(String command, boolean isRead) {
        this.command = command;
        this.isRead = isRead;
    }

    public AbstractCommand(CommandStrings command, boolean isRead) {
        this.command = command.getCommand();
        this.isRead = isRead;
    }

    protected String getCommand() {
        return command;
    }

    protected void setCommand(String command) {
        this.command = command;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
