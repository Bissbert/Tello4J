package ch.bissbert.command.model;

import org.apache.log4j.Logger;

public interface Command {

    Logger logger = Logger.getLogger(Command.class);

    /**
     * creates and returns the command in its full form
     * @return the command in its full form
     */
    String compose();

    /**
     * sets if its read
     * @param read
     */
    void setRead(boolean read);

    /**
     * if the command is reading something
     * @return where the command is read
     */
    boolean isRead();
}
