package com.safelogj.simlog.routers.cudy;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.collecting.SimListener;
import com.safelogj.simlog.routers.CellDataUpdatable;
import com.safelogj.simlog.routers.RouterManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CudyLT500Manager extends RouterManager implements CellDataUpdatable {

    private static final String LTE_PAGE = "/cgi-bin/luci/admin/network/gcom/status?detail=1&iface=4g";
    private static final String LOGOUT_PAGE = "/cgi-bin/luci/admin/logout";
    private static final String RSSI = "RSSI";
    private static final String RSRP = "RSRP";
    private Pattern dbiPattern;
    private Pattern pTagPattern;
    private Pattern tokensPatternCsrf;
    private Pattern tokensPatternCsrfAlt;
    private Pattern tokensPatternToken;
    private Pattern tokensPatternTokenAlt;
    private Pattern tokensPatternSalt;
    private Pattern tokensPatternSaltAlt;
    private MessageDigest mMessageDigest;


    public CudyLT500Manager() {
        try {
            dbiPattern = Pattern.compile("-?\\d+");
            pTagPattern = Pattern.compile("<p class=\"visible-xs\">(.*?)</p>", Pattern.DOTALL);
            tokensPatternCsrf = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            tokensPatternCsrfAlt = Pattern.compile("<input[^>]*value=\"([^\"]*)\"[^>]*name=\"_csrf\"", Pattern.CASE_INSENSITIVE);
            tokensPatternToken = Pattern.compile("<input[^>]*name=\"token\"[^>]*value=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            tokensPatternTokenAlt = Pattern.compile("<input[^>]*value=\"([^\"]*)\"[^>]*name=\"token\"", Pattern.CASE_INSENSITIVE);
            tokensPatternSalt = Pattern.compile("<input[^>]*name=\"salt\"[^>]*value=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            tokensPatternSaltAlt = Pattern.compile("<input[^>]*value=\"([^\"]*)\"[^>]*name=\"salt\"", Pattern.CASE_INSENSITIVE);
            mMessageDigest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка в конструкторе CudyLT500Manager " + e.getClass().getName());
        }
    }

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
            Log.d(AppController.LOG_TAG, "Ошибка при работе менеджера = KeeneticManager " + e.getClass().getName());
        }
        router.addError();
        return false;
    }

    private boolean fetchLteData(SimCardDataRouter router) throws Exception {
        CudyAuthTokens tokens = getTokens(router, new CudyAuthTokens());
        if (tokens.isValid()) {
            String passwordHash = calculateCudyHash(router, tokens);
            if (passwordHash == null) {
                router.addError();
                return false;
            }
            Log.d(AppController.LOG_TAG, "tokens = " + tokens);
            return performLogin(router, passwordHash, tokens);

        } else {
            Log.d(AppController.LOG_TAG, "Токены не спарсились");
            router.addError();
            return false;
        }

    }

    private CudyAuthTokens getTokens(SimCardDataRouter router, CudyAuthTokens token) throws Exception {
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + LOGOUT_PAGE)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            String html = response.body().string();
            return parseTokens(html, token);
        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка при получении токена");
        }
        return token;
    }

    private boolean performLogin(SimCardDataRouter router, String passwordHash, CudyAuthTokens tokens) throws IllegalArgumentException {
        RequestBody formBody = new FormBody.Builder()
                .add("_csrf", tokens.getCsrf())
                .add("token", tokens.getToken())
                .add("salt", tokens.getSalt())
                .add("zonename", "") // Поле есть в HTML, хоть и пустое
                .add("timeclock", String.valueOf(System.currentTimeMillis() / 1000))
                .add("luci_username", "admin")
                .add("luci_password", passwordHash)
                .add("luci_language", "en")
                .build();

        boolean login = loginLogout(router, formBody, LTE_PAGE);
        if (!login) {
            router.addError();
            return false;
        }

        Request lteRequest = getRequest(router, formBody, LTE_PAGE);
        try (Response response = router.getHttpClient().newCall(lteRequest).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                String html = response.body().string();
                boolean parseLteData = parseWithRegex(html, router);
                loginLogout(router, formBody, LOGOUT_PAGE);
                return parseLteData;
            } else {
                loginLogout(router, formBody, LOGOUT_PAGE);
            }
        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка : " + e);
        }
        router.addError();
        return false;
    }

    private boolean loginLogout(SimCardDataRouter router, RequestBody formBody, String tail) throws IllegalArgumentException {
        Request loginRequest = getRequest(router, formBody, tail);
        try (Response response = router.getHttpClient().newCall(loginRequest).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                return true;
            }
        } catch (IOException | IllegalStateException e) {
            Log.d(AppController.LOG_TAG, "Ошибка логина Cudy : " + e);
        }
        return false;
    }

    private Request getRequest(SimCardDataRouter router, RequestBody formBody, String tail) throws IllegalArgumentException {
        return new Request.Builder()
                .url(HTTP + router.getAddress() + tail)
                .post(formBody)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36")
                .build();
    }

    private boolean parseWithRegex(String html, SimCardDataRouter router) {
        List<String> pTagValues = new ArrayList<>();
        String rsrp = AppController.EMPTY_STRING;
        String rssi = AppController.EMPTY_STRING;
        String networkType = SimListener.TYPE_XG;
        String netTypeRaw;

        // Регулярное выражение: ищем содержимое между тегами <p class="visible-xs"> и </p>
        Matcher matcher = pTagPattern.matcher(html);

        while (matcher.find()) {
            String content = matcher.group(1);
            pTagValues.add(content == null ? AppController.EMPTY_STRING : content.trim());
        }
        // Теперь перебираем список и ищем нужные заголовки
        for (int i = 0; i < pTagValues.size(); i++) {
            String current = pTagValues.get(i);
            // 1. Ищем Тип сети
            if (current.contains("Network Type") && i + 1 < pTagValues.size()) {
                netTypeRaw = pTagValues.get(i + 1); // Очищаем от HTML тегов (span, i), если они там есть
                String[] parts = netTypeRaw.split("&nbsp;");
                if (parts.length == 3) {
                    networkType = getNetworkType(parts[1]);
                } else {
                    netTypeRaw = netTypeRaw.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
                    networkType = getNetworkType(netTypeRaw);
                }
            }
            if (current.equals(RSSI) && i + 1 < pTagValues.size()) {
                rssi = normalizeSignalValue(RSSI, pTagValues.get(i + 1).trim());
            }

            if (current.equals(RSRP) && i + 1 < pTagValues.size()) {
                rsrp = normalizeSignalValue(RSRP, pTagValues.get(i + 1).trim());
            }
        }

        int signal = getSignal(rsrp, rssi);
        router.setNetworkType(networkType);
        router.setSignalStrength(signal);
        Log.d(AppController.LOG_TAG, "!!! RSSI : " + rssi + " rsrp = " + rsrp);
        Log.d(AppController.LOG_TAG, "!!! Нашел Тип сети: " + networkType + " сигнал = " + signal);

        if (!networkType.equals(SimListener.TYPE_XG) && signal != -1) {
            setManager(router, this);
            return true;
        }
        router.addError();
        return false;
    }

    private String normalizeSignalValue(String target, String value) {
        try {
            Matcher matcher = dbiPattern.matcher(value);
            if (matcher.find()) {
                value = matcher.group();
            } else {
                return value;
            }
            int val = Integer.parseInt(value);
            if (val < 0) {
                return String.valueOf(val);
            }
            // Если положительное — конвертируем
            if (target.equals(RSSI) && val <= 31) {  // Стандартный ASU 0-31
                return String.valueOf((val * 2) - 113);
            } else if (target.equals(RSRP) && val <= 97) { // В LTE RSRP ASU часто 0-97 (формула: ASU - 140)
                return String.valueOf(val - 140);
            }

            return value;
        } catch (Exception e) {
            return value;
        }
    }
    @NonNull
    private String getNetworkType(String rawType) {
        if (rawType == null || rawType.isEmpty()) return SimListener.TYPE_XG;
        // Переводим в верхний регистр для исключения ошибок регистра
        String type = rawType.toUpperCase().trim();

        // Покрывает: NR5G, 5G, NR, 5G NSA, 5G SA
        if (type.contains("5G") || type.contains("NR")) {
            return SimListener.TYPE_5G;
        }
        // Покрывает: LTE, LTE-A, LTE CA, E-UTRAN, Evolved 3G (LTE) и все вариации CA
        if (type.contains("4G") || type.contains("LTE") || type.contains("E-UTRAN") || type.contains("EVOLVED 3G")) {
            return SimListener.TYPE_4G;
        }
        // Покрывает: 3G, WCDMA, UTRAN, HSPA, HSPA+, HSUPA, HSDPA и их комбинации
        if (type.contains("3G") || type.contains("WCDMA") || type.contains("TDSCDMA") || type.contains("UTRAN") || type.contains("HSPA") ||
                type.contains("HSDPA") || type.contains("HSUPA") || type.contains("HSDPS")) {
            return SimListener.TYPE_3G;
        }

        if (type.contains("2G") || type.contains("GSM") || type.contains("GPRS") || type.contains("EDGE") || type.contains("EGPRS")) {
            return SimListener.TYPE_2G;
        }
        return SimListener.TYPE_XG;
    }
    @NonNull
    private CudyAuthTokens parseTokens(String html, @NonNull CudyAuthTokens tokens) {
        tokens.setCsrf(findInputValue(html, tokensPatternCsrf, tokensPatternCsrfAlt));
        tokens.setToken(findInputValue(html, tokensPatternToken, tokensPatternTokenAlt));
        tokens.setSalt(findInputValue(html, tokensPatternSalt, tokensPatternSaltAlt));
        return tokens;
    }

    @NonNull
    private String findInputValue(String html, @NonNull Pattern pattern, @NonNull Pattern patternAlt) {
        // Если в HTML сначала идет value, а потом name,
        // добавим проверку и на такой случай (для надежности LuCI)
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String t = matcher.group(1);
            return t == null ? AppController.EMPTY_STRING : t;
        }
        // Пробуем альтернативный порядок атрибутов
        matcher = patternAlt.matcher(html);
        if (matcher.find()) {
            String at = matcher.group(1);
            return at == null ? AppController.EMPTY_STRING : at;
        }

        return AppController.EMPTY_STRING;
    }

    // ШАГ 3: Реализация алгоритма хеширования Cudy
    @Nullable
    private String calculateCudyHash(SimCardDataRouter router, CudyAuthTokens tokens) {
        // Шаг 1: sha256(password + salt)
        String step1 = sha256(router.getPass() + tokens.getSalt());
        // Шаг 2: sha256(step1 + token)
        return step1 == null ? null : sha256(step1 + tokens.getToken());
    }

    // Вспомогательный метод для SHA256
    @Nullable
    private synchronized String sha256(String base) {
        if (mMessageDigest == null) return null;
        mMessageDigest.reset();
        byte[] hash = mMessageDigest.digest(base.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
}
