package com.safelogj.simlog.routers;

import android.util.Log;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.collecting.SimListener;

public abstract class RouterManager {
    protected static final String HTTP = "http://";
    protected static final String REFERER = "Referer";
    protected static final String CONNECTION = "Connection";
    protected static final String CLOSE = "close";
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    protected RouterManager() {}

    protected static void setManager(SimCardDataRouter router, CellDataUpdatable manager) {
        router.setRouterManager(manager);
        Log.i(AppController.LOG_TAG, "Сохранена фабрика " + manager + " для роутера = " + router.getAddress());
    }

    protected static void resetManager(SimCardDataRouter router) {
        router.setRouterManager(null);
        Log.i(AppController.LOG_TAG, "Сброс фабрики для роутера = " + router.getAddress());
    }

    protected static void resetData(SimCardDataRouter router) {
        router.setSignalStrength(-1);
        router.setNetworkType(SimListener.TYPE_XG);
    }

    protected static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    protected static int getSignal(String rsrpStr, String rssiStr) {

        int rsrp = parseIntSignal(rsrpStr.trim());
        if (rsrp > -80) return 4;  // displayed as 5
        if (rsrp > -90) return 3;  // displayed as 4
        if (rsrp > -100) return 2;  // displayed as 3
        if (rsrp > -110) return 1;  // displayed as 2
        if (rsrp > -120) return 0;  // displayed as 1


        int rssi = parseIntSignal(rssiStr.trim());
        if (rssi > -65) return 4;  // displayed as 5
        if (rssi > -75) return 3;  // displayed as 4
        if (rssi > -85) return 2;  // displayed as 3
        if (rssi > -95) return 1;  // displayed as 2
        if (rssi > -120) return 0;  // displayed as 1

        return -1;
    }

    private static int parseIntSignal(String str) {
        try {
            int digit = Integer.parseInt(str);
            return digit >= 0 ? -140 : digit;
        } catch (Exception e) {
            return -140;
        }
    }
}
