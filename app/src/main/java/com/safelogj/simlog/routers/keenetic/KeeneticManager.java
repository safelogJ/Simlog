package com.safelogj.simlog.routers.keenetic;

import android.util.Log;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.collecting.SimListener;
import com.safelogj.simlog.routers.CellDataUpdatable;
import com.safelogj.simlog.routers.RouterManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KeeneticManager extends RouterManager implements CellDataUpdatable {

    private static final String AUTH_PATH = "/auth";
    private MessageDigest mMessageDigestMd5;
    private MessageDigest mMessageDigestSha256;

    public KeeneticManager() {
        try {
            mMessageDigestMd5 = MessageDigest.getInstance("MD5");
            mMessageDigestSha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.d(AppController.LOG_TAG, "Ошибка в супер конструкторе KeeneticManager " + e.getClass().getName());
        }
    }

    @Override
    public boolean setNewDataToSimCard(SimCardDataRouter router) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        try {
            resetData(router);
            return fetchLteData(router, true); // ОПТИМИСТИЧНЫЙ ШАГ: Сразу пробуем забрать данные (используя куки из httpClient)
        } catch (InterruptedException e) { // Пробрасываем наверх согласно интерфейсу
            throw e;
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка при работе менеджера = KeeneticManager " + e.getClass().getName());
        }
        router.addError();
        return false;
    }

    protected String getDataCommand(SimCardDataRouter router) {
        return AppController.EMPTY_STRING;
    }

    /**
     * @param isFirstAttempt Если true, то при 401 ошибке пойдем на авторизацию.
     *                       Если false, значит мы уже после авторизации и 401 - это финал.
     */

    private boolean fetchLteData(SimCardDataRouter router, boolean isFirstAttempt) throws IllegalArgumentException, InterruptedException {
        Log.d(AppController.LOG_TAG, "Команда..." + HTTP + router.getAddress() + getDataCommand(router));
        Request request = new Request.Builder().url(HTTP + router.getAddress() + getDataCommand(router)).build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                String result = response.body().string();
                return parseResult(router, result); // Парсим и проверяем, есть ли полезный сигнал

            } else if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED && isFirstAttempt) {
                Log.d(AppController.LOG_TAG, "Сессия Keenetic истекла, обновляем...");
                return startAuthStep1(router);

            } else if (response.code() == HttpURLConnection.HTTP_FORBIDDEN) { // кука протухла 403
                Log.d(AppController.LOG_TAG, "кука протухла, чистим " + response.code());
                router.clearCookie();

            } else if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) { // нет данных 404
                resetManager(router);
                Log.d(AppController.LOG_TAG, "Ошибка получения данных " + response.code());
                router.addError();

            } else { // другие ошибки в ответе
                Log.d(AppController.LOG_TAG, "Ошибка  " + response.code());
                router.addError();
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка сети: при первом запросе " + e.getMessage());
            router.addError();
        }
        return false;
    }

    // --- ЦЕПОЧКА АВТОРИЗАЦИИ ---
    private boolean startAuthStep1(SimCardDataRouter router) throws IllegalArgumentException, InterruptedException {
        Request request = new Request.Builder().url(HTTP + router.getAddress() + AUTH_PATH).build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            String challenge = response.header("X-NDM-Challenge");
            String realm = response.header("X-NDM-Realm");

            if (challenge != null && realm != null) {
                return performPostAuth(challenge, realm, router);
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка сети: " + e.getMessage());
        }
        router.addError();
        return false;
    }

    private boolean performPostAuth(String challenge, String realm, SimCardDataRouter router) throws InterruptedException {
        String hash1 = md5(router.getLogin() + ":" + realm + ":" + router.getPass());
        String finalHash = sha256(challenge + hash1);
        JSONObject json = new JSONObject();

        try {
            json.put("login", router.getLogin());
            json.put("password", finalHash);
            return secondFetchLteData(router, json);

        } catch (JSONException e) {
            router.addError();
            return false;
        }
    }

    private boolean secondFetchLteData(SimCardDataRouter router, JSONObject json) throws IllegalArgumentException, InterruptedException {
        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + AUTH_PATH)
                .post(body)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                return fetchLteData(router, false); // Сразу запрашиваем данные ПОВТОРНО (уже с новой кукой)
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка логина: " + response.code());
            }

        } catch (IOException | IllegalStateException e) {
            Log.d(AppController.LOG_TAG, "Ошибка сети: при втором запросе " + e.getMessage());
        }
        router.addError();
        return false;
    }

    // --- ПАРСИНГ И УТИЛИТЫ ---
    private boolean parseResult(SimCardDataRouter router, String result) {

        try {
            JSONObject json = new JSONObject(result);
            String rsrp = json.optString("rsrp", "-140");
            String rssi = json.optString("rssi", "-140");
            String net = json.optString("mobile", SimListener.TYPE_XG);
            String signal = json.optString("signal-level", "-1");

            String network = getNetworkType(net);
            router.setNetworkType(network);
            int levelSignal = getSignal(rsrp, rssi);
            router.setSignalStrength(levelSignal);

            Log.i(AppController.LOG_TAG, ">>> КИНЕТИК ОБНОВЛЕН: " + net + " (" + signal + ")" + " rssi = "
                    + rssi + " rsrp = " + rsrp + " сеть " + network + " сигнал " + levelSignal);

            if (!network.equals(SimListener.TYPE_XG) && levelSignal != -1) {
                setManager(router, this);
                return true;
            } else {
                resetManager(router);
            }

        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка парсинга JSON: " + e.getMessage());
        }
        router.addError();
        return false;
    }

    private synchronized String md5(String s) {
        if(mMessageDigestMd5 == null) return AppController.EMPTY_STRING;
        mMessageDigestMd5.reset();
        byte[] hash = mMessageDigestMd5.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private synchronized String sha256(String s) {
        if (mMessageDigestSha256 == null) return AppController.EMPTY_STRING;
        mMessageDigestSha256.reset();
        byte[] hash = mMessageDigestSha256.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * case "5G" -> SimListener.TYPE_5G;
     * case "4G",
     * "4G+" -> SimListener.TYPE_4G;
     * case "3G WCDMA",
     * "3G HSPA",
     * "3G HSPA+",
     * "3G DC-HSPA+" -> SimListener.TYPE_3G;
     * case "2G EDGE",
     * "2G GPRS" -> SimListener.TYPE_2G;
     * default -> SimListener.TYPE_XG;
     *
     */

    protected String getNetworkType(String type) {
        if (type.equals(SimListener.TYPE_XG)) return SimListener.TYPE_XG;
        if (type.startsWith("4G")) return SimListener.TYPE_4G;
        if (type.startsWith("3G")) return SimListener.TYPE_3G;
        if (type.startsWith("2G")) return SimListener.TYPE_2G;
        if (type.startsWith("5G")) return SimListener.TYPE_5G;
        return SimListener.TYPE_XG;
    }

}
