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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HuaweiManagerB593s extends RouterManager implements CellDataUpdatable {

    private static final Pattern PATTERN_MODE = Pattern.compile("<Mode>(.*?)</Mode>");
    private static final Pattern PATTERN_SIG = Pattern.compile("<SIG>(.*?)</SIG>");
    private final AtomicInteger ridAtomic = new AtomicInteger(100);

    @Override
    public boolean setNewDataToSimCard(SimCardDataRouter router) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        try {
            resetData(router);
            return fetchLteData(router);
        } catch (InterruptedException e) { // Пробрасываем наверх согласно интерфейсу
            throw e;
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка при работе менеджера = HuaweiManagerB593s " + e.getClass().getName());
        }
        router.addError();
        return false;
    }

    private boolean fetchLteData(SimCardDataRouter router) throws Exception {
        int rid = ridAtomic.incrementAndGet();
        String url = HTTP + router.getAddress() + "/index/getStatusByAjax.cgi?rid=" + rid;
        RequestBody formBody = new FormBody.Builder()
                .add("rid", String.valueOf(rid))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .header("Host", "homerouter.cpe")
                .header(REFERER, "http://homerouter.cpe/html/status/overview.asp")
                .header(CONNECTION, CLOSE)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                String xml = response.body().string();
                Log.i(AppController.LOG_TAG, "Хуавей ответ 593s: " + xml);

                String network = getNetworkType(extractXmlTag(xml, PATTERN_MODE));
                router.setNetworkType(network);
                int levelSignal = getSignalH(extractXmlTag(xml, PATTERN_SIG));
                router.setSignalStrength(levelSignal);

                Log.i(AppController.LOG_TAG, "Хуавей обновлён Сигнал: " + levelSignal + " | Сеть: " + network);

                if (!network.equals(SimListener.TYPE_XG) && levelSignal != -1) {
                    setManager(router, this);
                    return true;
                } else {
                    resetManager(router);
                }
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса: " + e.getMessage());
        }
        router.addError();
        return false;
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


    private String getNetworkType(@NonNull String type) {
        return switch (type) {
            case "30", "62" -> SimListener.TYPE_4G;  // ожидание, передача
            case "29", "61" -> SimListener.TYPE_3G;
            case "28", "60" -> SimListener.TYPE_2G;
            default -> SimListener.TYPE_XG;
        };
    }

    private int getSignalH(@NonNull String signal) {
        return switch (signal) {
            case "19" -> 0;
            case "20" -> 1;
            case "21" -> 2;
            case "22" -> 3;
            case "23" -> 4;
            default -> -1;
        };
    }
}
