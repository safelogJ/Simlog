package com.safelogj.simlog.displaying;

public class LogLine {
    private final int time;
    private final String type;
    private final int level;

    public LogLine(int time, String type, int level) {
        this.time = time;
        this.type = type;
        this.level = level;
    }

    public int getTime() {
        return time;
    }

    public String getType() {
        return type;
    }

    public int getLevel() {
        return level;
    }
}
