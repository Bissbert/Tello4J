package ch.bissbert;

import java.io.Serializable;
import java.util.stream.IntStream;

public enum CommandStrings implements CharSequence, Serializable {

    //TODO get all commands from SDK and add comments

    COMMAND("command"),
    TAKE_OFF("takeoff"),
    LAND("land"),
    STREAM_ON("streamon"),
    STREAM_OFF("streamoff"),
    EMERGENCY("emergency"),
    FLY_UP("up"),
    FLY_DOWN("down"),
    FLY_LEFT("left"),
    FLY_RIGHT("right"),
    FLY_FORWARD("forward"),
    FLY_BACK("back"),
    /**
     * degree in 3600 instead of 360
     */
    TURN_CLOCKWISE("cw"),
    /**
     * degree in 3600 instead of 360
     */
    TURN_COUNTERCLOCKWISE("ccw"),
    GO("go"),
    SET_SPEED("speed"),
    SET_WIFI("wifi ssid"),
    GET_BATTERY("battery?");


    String command;

    CommandStrings(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public int length() {
        return this.command.length();
    }

    @Override
    public char charAt(int index) {
        return this.command.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return this.command.subSequence(start, end);
    }

    @Override
    public IntStream chars() {
        return this.command.chars();
    }

    @Override
    public IntStream codePoints() {
        return this.command.codePoints();
    }
}
