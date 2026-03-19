package com.safelogj.simlog.routers.huawei;

import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.collecting.SimListener;
import com.safelogj.simlog.routers.CellDataUpdatable;
import com.safelogj.simlog.routers.RouterManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

public class HuaweiManagerE3372h320 extends RouterManager implements CellDataUpdatable {


    //    private static final Pattern PATTERN_SESINFO = Pattern.compile("<SesInfo>(.*?)</SesInfo>");
//    private static final Pattern PATTERN_TOKINFO = Pattern.compile("<TokInfo>(.*?)</TokInfo>");
    private static final Pattern PATTERN_RSRP = Pattern.compile("<rsrp>.*?(-?\\d+).*?</rsrp>");
    private static final Pattern PATTERN_RSSI = Pattern.compile("<rssi>.*?(-?\\d+).*?</rssi>");
    private static final Pattern PATTERN_MODE = Pattern.compile("<CurrentNetworkType>.*?(\\d+).*?</CurrentNetworkType>");
    private static final Pattern PATTERN_MODEEX = Pattern.compile("<CurrentNetworkTypeEx>.*?(\\d+).*?</CurrentNetworkTypeEx>");
    private static final Pattern PATTERN_SIGNALICON = Pattern.compile("<SignalIcon>.*?(\\d+).*?</SignalIcon>");
    private static final Pattern PATTERN_SIGNALSTRENGTH = Pattern.compile("<SignalStrength>.*?(\\d+).*?</SignalStrength>");


    @Override
    public boolean setNewDataToSimCard(SimCardDataRouter router) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        try {
            resetData(router);
            router.clearCookie();
            return fetchLteData(router);
        } catch (InterruptedException e) { // Пробрасываем наверх согласно интерфейсу
            throw e;
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка при работе менеджера = HuaweiManagerE3372h320 " + e.getClass().getName());
        }
        router.addError();
        return false;
    }

    private boolean fetchLteData(SimCardDataRouter router) throws Exception {
        String address = router.getAddress();
//        String urlTok = HTTP + address + "/api/webserver/SesTokInfo";
        String urlSignal = HTTP + address + "/api/device/signal";
        String urlStatus = HTTP + address + "/api/monitoring/status";
//        HuaweiAuthParams params;
        int levelSignal;
        String network;
//        String tokensXml = getXmlResponse(router, address, urlTok);
//        if (!tokensXml.isEmpty()) {
//            params = new HuaweiAuthParams(extractXmlTag(tokensXml, PATTERN_SESINFO), extractXmlTag(tokensXml, PATTERN_TOKINFO));
//            Log.i(AppController.LOG_TAG, "токены : " + params);
//        }
        String signalXml = getXmlResponse(router, address, urlSignal);
       // Log.i(AppController.LOG_TAG, "signalXml: " + signalXml);
        levelSignal = getSignal(extractXmlTag(signalXml, PATTERN_RSRP), extractXmlTag(signalXml, PATTERN_RSSI));

        String netStatusXml = getXmlResponse(router, address, urlStatus);
      //  Log.i(AppController.LOG_TAG, "netStatusXml: " + netStatusXml);
        network = getNetworkType(extractXmlTag(netStatusXml, PATTERN_MODE));

        if (network.equals(SimListener.TYPE_XG)) {
            network = getNetworkType(extractXmlTag(netStatusXml, PATTERN_MODEEX));
        }
//        if (levelSignal == -1) {
//            levelSignal = getSignalH(extractXmlTag(netStatusXml, PATTERN_SIGNALICON));
//            if (levelSignal == -1) {
//                levelSignal = getSignalH(extractXmlTag(netStatusXml, PATTERN_SIGNALSTRENGTH));
//            }
//        }

        router.setNetworkType(network);
        router.setSignalStrength(levelSignal);
        Log.i(AppController.LOG_TAG, "Хуавей обновлён Сигнал: " + levelSignal + " | Сеть: " + network);

        if (!network.equals(SimListener.TYPE_XG) && levelSignal != -1) {
            setManager(router, this);
            return true;
        }
        router.addError();
        return false;
    }

    @NonNull
    private String getXmlResponse(SimCardDataRouter router, String address, String url) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url(url)
                .header("Host", address)
                .header(REFERER, HTTP + address + "/html/content.html")
                .header("X-Requested-With", "XMLHttpRequest")
                .header(CONNECTION, CLOSE)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                return response.body().string();
            } else {
                Log.w(AppController.LOG_TAG, "Хуавей 3372 ответ: " + response.code());
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса: " + e.getMessage());
        }
        return AppController.EMPTY_STRING;
    }

    @NonNull
    private String extractXmlTag(String xml, Pattern pattern) {
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            String value = matcher.group(1);
            return value != null ? value : AppController.EMPTY_STRING;
        }
        return AppController.EMPTY_STRING;
    }
    @NonNull
    private String getNetworkType(@NonNull String type) {
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

    private int getSignalH(@NonNull String signal) {
        return switch (signal) {
            case "1" -> 0;
            case "2" -> 1;
            case "3" -> 2;
            case "4" -> 3;
            case "5" -> 4;
            default -> -1;
        };
    }
}
