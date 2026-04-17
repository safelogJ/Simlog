package com.safelogj.simlog.routers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.collecting.SimListener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RouterManager {
    protected static final String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Mobile Safari/537.36";
    protected static final String HTTP = "http://";
    protected static final String HOST = "Host";
    protected static final String USER_AGENT = "User-Agent";
    protected static final String X_REQUESTED_WITH = "X-Requested-With";
    protected static final String XML_HTTP_REQUESTED = "XMLHttpRequest";
    protected static final String BROSWER = "Broswer";
    protected static final String RESPONSE_SOURCE = "_ResponseSource";
    protected static final String REQUEST_VER_TOKEN = "__RequestVerificationToken";
    protected static final String CONTENT_TYPE = "Content-Type";
    protected static final String CONTENT_TYPE_BODY = "application/x-www-form-urlencoded; charset=UTF-8";
    protected static final String REFERER = "Referer";
    protected static final String CONNECTION = "Connection";
    protected static final String CLOSE = "close";
    protected static final String API_DEVICE_SIGNAL = "/api/device/signal";
    protected static final String API_MONITORING_STATUS = "/api/monitoring/status";
    protected static final Pattern PATTERN_HUAWEI_RSRP = Pattern.compile("<rsrp>.*?(-?\\d+).*?</rsrp>");
    protected static final Pattern PATTERN_HUAWEI_RSSI = Pattern.compile("<rssi>.*?(-?\\d+).*?</rssi>");
    protected static final Pattern PATTERN_HUAWEI_MODE = Pattern.compile("<CurrentNetworkType>.*?(\\d+).*?</CurrentNetworkType>");
    protected static final Pattern PATTERN_HUAWEI_MODEEX = Pattern.compile("<CurrentNetworkTypeEx>.*?(\\d+).*?</CurrentNetworkTypeEx>");
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

    protected static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
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

    @NonNull
    protected String getHuaweiNetworkType(@NonNull String type) {
        return switch (type) {
            case "20", "111" -> SimListener.TYPE_5G;
            case "19", "81", "101", "1011" -> SimListener.TYPE_4G;
            case "4", "5", "6", "7", "8", "9", "10", "11", "12", "14", "15", "16", "17", "18",
                 "24", "25", "26", "28", "29", "30", "31", "32", "33", "34", "35", "36",
                 "41", "42", "43", "44", "45", "46", "61", "62", "63", "64", "65" ->
                    SimListener.TYPE_3G;
            case "1", "2", "3", "13", "21", "22", "23", "27" -> SimListener.TYPE_2G;
            default -> SimListener.TYPE_XG;
        };
    }

    @NonNull
    protected String extractXmlTagGroup1Huawei(String xml, Pattern pattern) {
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            String value = matcher.group(1);
            return value != null ? value : AppController.EMPTY_STRING;
        }
        return AppController.EMPTY_STRING;
    }
}
