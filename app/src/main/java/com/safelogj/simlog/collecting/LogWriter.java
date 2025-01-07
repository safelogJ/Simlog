package com.safelogj.simlog.collecting;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class LogWriter implements Runnable {

    private final List<SimCard> mCheckedSims;

    public LogWriter(List<SimCard> sims) {
        mCheckedSims = sims;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                writeData();
                TimeUnit.SECONDS.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stopWriters();
            mCheckedSims.clear();
        }
    }


    private void writeData() {
        if (mCheckedSims != null && !mCheckedSims.isEmpty()) {
            for (SimCard simCard : mCheckedSims) {
                simCard.writeLine();
            }
        }
    }

    private void stopWriters() {
        if (mCheckedSims != null && !mCheckedSims.isEmpty()) {
            for (SimCard simCard : mCheckedSims) {
                simCard.stopWriter();
            }
        }
    }
}
