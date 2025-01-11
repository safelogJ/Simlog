package com.safelogj.simlog.collecting;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SimCard extends AppCompatCheckBox {
    private static final String FILE_NAME_PATTERN = "SIM_%d_%s_%s.txt";
    private static final String CURRENT_DATE_PATTERN = "dd_MM_yyyy";
    private static File mExternalFileDir;
    private int slot;
    private String operator;
    private int subscriptionId;
    private BufferedWriter mBWriter;
    private volatile String mNetworkType = "start";
    private volatile int mSignalStrength = -1;
    private volatile int mLines;
    private volatile int mErrors;
    private String mDate = "";


    public SimCard(@NonNull Context context, int slot, String operator, int subscriptionId) {
        super(context);
        this.slot = slot;
        this.operator = operator;
        this.subscriptionId = subscriptionId;
    }


    public SimCard(@NonNull Context context) {
        super(context);
    }

    public SimCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SimCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public static void setExternalFileDir(File externalFileDir) {
        mExternalFileDir = externalFileDir;
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

    public int getWriteErrors() {
        return mErrors;
    }

    public int getWriteLines() {
        return mLines;
    }

    public void writeLine() {
        String currentDate = getCurrentDate();
        if (mBWriter == null || !currentDate.equals(mDate)) {
            mDate = currentDate;
            stopWriter();
            mBWriter = createWriter();
        }
        String line = getMinutesInDay() + "," + mNetworkType + "," + mSignalStrength;
        try {
            mBWriter.write(line);
            mBWriter.newLine();
            mBWriter.flush();
            mLines++;
        } catch (IOException e) {
            mDate = "try_again";
            mErrors ++;
        }
    }

    public void stopWriter() {
        try {
            if (mBWriter != null) {
                mBWriter.close();
            }
        } catch (IOException e) {
            mErrors ++;
            mBWriter = null;
        }

    }

    private BufferedWriter createWriter() {
        File file = new File(mExternalFileDir, getFileName());
        try {
            return new BufferedWriter(new FileWriter(file, true));
        } catch (IOException e) {
            mErrors ++;
            return null;
        }
    }

    private String getFileName() {
        return String.format(Locale.US, FILE_NAME_PATTERN, getSlot(), getOperator(), mDate);
    }

    private String getCurrentDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(CURRENT_DATE_PATTERN, Locale.US);
        return LocalDate.now().format(formatter);
    }

    private int getMinutesInDay() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return now.getHour() * 60 + now.getMinute();
    }
}
