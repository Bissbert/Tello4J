package ch.bissbert.command.model;


import ch.bissbert.CommandStrings;

public class BasicCommand extends AbstractCommand{


    public BasicCommand(String command, boolean isRead) {
        super(command, isRead);
    }

    public BasicCommand(CommandStrings command, boolean isRead) {
        super(command, isRead);
    }

    @Override
    public String compose() {
        return super.getCommand();
    }
}
