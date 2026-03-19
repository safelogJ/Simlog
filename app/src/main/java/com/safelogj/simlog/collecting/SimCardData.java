package com.safelogj.simlog.collecting;

import com.safelogj.simlog.AppController;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class SimCardData {

    protected static File mExternalFileDir;
    private static final String FILE_NAME_PATTERN = "SIM_%d_%s_%s.txt";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd_MM_yyyy", Locale.US);
    private static final String LOGLINE_CONCATENATOR = ",";
    private static final String START_STRING = "start";
    private final int slot;
    private final String operator;
    private final String checkBoxText;
    private final int subscriptionId;
    private BufferedWriter mBWriter;
    private volatile String mNetworkType = START_STRING;
    private volatile int mSignalStrength = -1;
    private volatile int mLines;
    private final AtomicInteger mErrors = new AtomicInteger(0);
    protected String mDate = AppController.EMPTY_STRING;

    public static void setExternalFileDir(File externalFileDir) {
        mExternalFileDir = externalFileDir;
    }

    public SimCardData(int slot, String operator, int subscriptionId, String checkBoxText) {
        this.slot = slot;
        this.operator = operator;
        this.subscriptionId = subscriptionId;
        this.checkBoxText = checkBoxText;
    }

    public int getSlot() {
        return slot;
    }

    public String getOperator() {
        return operator;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public void setNetworkType(String mNetworkType) {
        this.mNetworkType = mNetworkType;
    }

    public void setSignalStrength(int mSignalStrength) {
        this.mSignalStrength = mSignalStrength;
    }

    public void addError() {
        mErrors.incrementAndGet();
    }

    public int getWriteErrors() {
        return mErrors.get();
    }

    public int getWriteLines() {
        return mLines;
    }

    public boolean isRouter() {
        return false;
    }

    public String getCheckBoxText() {
        return checkBoxText;
    }

    public synchronized void writeLine() {
        String currentDate = getCurrentDate();
        if (mBWriter == null || !currentDate.equals(mDate)) {
            mDate = currentDate;
            stopWriter();
            mBWriter = createWriter();
        }

        if (mBWriter != null) {
            String line = getMinutesInDay() + LOGLINE_CONCATENATOR + mNetworkType + LOGLINE_CONCATENATOR + mSignalStrength;
            try {
                mBWriter.write(line);
                mBWriter.newLine();
                mBWriter.flush();
                mLines++ ;
            } catch (IOException e) {
                mErrors.incrementAndGet();
                stopWriter();
            }
        }
    }

    public synchronized void stopWriter() {
        try {
            if (mBWriter != null) {
                mBWriter.close();
            }
        } catch (IOException e) {
            mErrors.incrementAndGet();
        }
        mBWriter = null;
    }

    private BufferedWriter createWriter() {
        File file = new File(mExternalFileDir, getFileName());
        try {
            return new BufferedWriter(new FileWriter(file, true));
        } catch (IOException e) {
            mErrors.incrementAndGet();
            return null;
        }
    }

    protected String getFileName() {
        return String.format(Locale.US, FILE_NAME_PATTERN, getSlot(), getOperator(), mDate);
    }

    private String getCurrentDate() {
        return LocalDate.now().format(DATE_TIME_FORMATTER);
    }

    private int getMinutesInDay() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return now.getHour() * 60 + now.getMinute();
    }
}
