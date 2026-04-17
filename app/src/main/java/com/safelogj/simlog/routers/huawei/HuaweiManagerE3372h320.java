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

import okhttp3.Request;
import okhttp3.Response;

public class HuaweiManagerE3372h320 extends RouterManager implements CellDataUpdatable {


    //    private static final Pattern PATTERN_SESINFO = Pattern.compile("<SesInfo>(.*?)</SesInfo>");
//    private static final Pattern PATTERN_TOKINFO = Pattern.compile("<TokInfo>(.*?)</TokInfo>");
//    private static final Pattern PATTERN_SIGNALICON = Pattern.compile("<SignalIcon>.*?(\\d+).*?</SignalIcon>");
//    private static final Pattern PATTERN_SIGNALSTRENGTH = Pattern.compile("<SignalStrength>.*?(\\d+).*?</SignalStrength>");


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
        //  String address = router.getAddress();
//        String urlTok = HTTP + address + "/api/webserver/SesTokInfo";
        String urlSignal = HTTP + router.getAddress() + API_DEVICE_SIGNAL;
        String urlStatus = HTTP + router.getAddress() + API_MONITORING_STATUS;
//        HuaweiAuthParams params;
        int levelSignal;
        String network;
//        String tokensXml = getXmlResponse(router, address, urlTok);
//        if (!tokensXml.isEmpty()) {
//            params = new HuaweiAuthParams(extractXmlTag(tokensXml, PATTERN_SESINFO), extractXmlTag(tokensXml, PATTERN_TOKINFO));
//            Log.i(AppController.LOG_TAG, "токены : " + params);
//        }
        String signalXml = getXmlResponse(router, urlSignal);
        // Log.i(AppController.LOG_TAG, "signalXml: " + signalXml);
        levelSignal = getSignal(extractXmlTagGroup1Huawei(signalXml, PATTERN_HUAWEI_RSRP), extractXmlTagGroup1Huawei(signalXml, PATTERN_HUAWEI_RSSI));

        String netStatusXml = getXmlResponse(router, urlStatus);
        //  Log.i(AppController.LOG_TAG, "netStatusXml: " + netStatusXml);
        network = getHuaweiNetworkType(extractXmlTagGroup1Huawei(netStatusXml, PATTERN_HUAWEI_MODE));

        if (network.equals(SimListener.TYPE_XG)) {
            network = getHuaweiNetworkType(extractXmlTagGroup1Huawei(netStatusXml, PATTERN_HUAWEI_MODEEX));
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
    private String getXmlResponse(SimCardDataRouter router, String url) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url(url)
                .header(HOST, router.getAddress())
                .header(REFERER, HTTP + router.getAddress() + "/html/content.html")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUESTED)
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
