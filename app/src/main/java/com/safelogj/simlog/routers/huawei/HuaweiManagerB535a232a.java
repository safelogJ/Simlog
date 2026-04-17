package com.safelogj.simlog.routers.huawei;

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
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HuaweiManagerB535a232a extends RouterManager implements CellDataUpdatable {
    private static final Pattern PATTERN_TOKEN = Pattern.compile("<token>(.*?)</token>");
    private static final Pattern PATTERN_SERVERNONCE = Pattern.compile("<servernonce>(.*?)</servernonce>");
    private static final Pattern PATTERN_SALT = Pattern.compile("<salt>(.*?)</salt>");
    private static final Pattern PATTERN_ITERATIONS = Pattern.compile("<iterations>(.*?)</iterations>");
    private final SecureRandom mRandom = new SecureRandom();
    private SecretKeyFactory mSkf;
    private Mac mMac;
    private MessageDigest mDigest;

    public HuaweiManagerB535a232a() {
        try {
            mSkf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            mMac = Mac.getInstance("HmacSHA256");
            mDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Log.d(AppController.LOG_TAG, "Ошибка в конструкторе HuaweiManagerB535a232a " + e.getClass().getName());
        }
    }

    @Override
    public boolean setNewDataToSimCard(SimCardDataRouter router) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        try {
            resetData(router);
            return fetchLteData(router, true);
        } catch (InterruptedException e) { // Пробрасываем наверх согласно интерфейсу
            throw e;
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, " Ошибка при работе менеджера = HuaweiManagerB535a232a " + e.getClass().getName());
        }
        router.addError();
        return false;
    }

    /**
     * @param isFirstAttempt Если true, то при 125002 ошибке пойдем на авторизацию.
     *                       Если false, значит мы уже после авторизации и 125002 - это финал.
     */

    private boolean fetchLteData(SimCardDataRouter router, boolean isFirstAttempt) throws Exception {
        if (isFirstAttempt) {
            return askCellData(router, true);
        } else {
            router.clearCookie();
            callCookieByStatus(router);
            String tokenXml = getTokenXml(router);
            String token = extractXmlTagGroup1Huawei(tokenXml, PATTERN_TOKEN);
            if (token.isEmpty()) {
                router.addError();
                return false;
            }
            HuaweiAuthParams params = new HuaweiAuthParams();
            params.setToken(token);
            String challengeLogin = getXmlChallengeLogin(router, params);
            String serveronce = extractXmlTagGroup1Huawei(challengeLogin, PATTERN_SERVERNONCE);
            String salt = extractXmlTagGroup1Huawei(challengeLogin, PATTERN_SALT);
            String iterations = extractXmlTagGroup1Huawei(challengeLogin, PATTERN_ITERATIONS);
            if (serveronce.isEmpty() || salt.isEmpty() || iterations.isEmpty()) {
                router.addError();
                return false;
            }
            params.setServernonce(serveronce);
            params.setSalt(salt);
            params.setIterations(iterations);
            if (!callAuthenticationLogin(router, params)) {
                router.addError();
                return false;
            } else {
                return askCellData(router, false);
            }
        }
    }


    private void callCookieByStatus(SimCardDataRouter router) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + API_MONITORING_STATUS)
                .header(HOST, router.getAddress())
                .header(RESPONSE_SOURCE, BROSWER)
                .header(REFERER, HTTP + router.getAddress() + "/")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUESTED)
                .header("Update-Cookie", "UpdateCookie")
                .header(USER_AGENT, MOBILE_USER_AGENT)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            Log.w(AppController.LOG_TAG, "Хуавей B535a232a ответ на получение кук: " + response.code());
        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса кук: " + e.getMessage());
        }
    }

    @NonNull
    private String getTokenXml(SimCardDataRouter router) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + "/api/webserver/token")
                .header(HOST, router.getAddress())
                .header(RESPONSE_SOURCE, BROSWER)
                .header(REFERER, HTTP + router.getAddress() + "/")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUESTED)
                .header(USER_AGENT, MOBILE_USER_AGENT)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                return response.body().string();
            } else {
                Log.w(AppController.LOG_TAG, "Хуавей B535a232a ответ получение токена: " + response.code());
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса токена: " + e.getMessage());
        }
        return AppController.EMPTY_STRING;
    }
    @NonNull
    private String getXmlChallengeLogin(SimCardDataRouter router, HuaweiAuthParams params) {
        String firstNonce = generateFirstNonce();
        params.setFirstnonce(firstNonce);

        String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><username>admin</username><firstnonce>" + firstNonce + "</firstnonce><mode>1</mode></request>";
        RequestBody body = RequestBody.create(xmlBody, MediaType.parse(CONTENT_TYPE_BODY));
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + "/api/user/challenge_login")
                .post(body)
                .header(RESPONSE_SOURCE, BROSWER)
                .header(CONTENT_TYPE, CONTENT_TYPE_BODY)
                .header(REQUEST_VER_TOKEN, params.getToken().substring(params.getToken().length() - 32))
                .header(HOST, router.getAddress())
                .header(REFERER, HTTP + router.getAddress() + "/")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUESTED)
                .header(USER_AGENT, MOBILE_USER_AGENT)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                params.setRequestVerificationToken(response.header(REQUEST_VER_TOKEN));
                return response.body().string();
            } else {
                Log.w(AppController.LOG_TAG, "Хуавей B535a232a ответ: " + response.code());
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса: " + e.getMessage());
        }
        return AppController.EMPTY_STRING;

    }

    private boolean callAuthenticationLogin(SimCardDataRouter router, HuaweiAuthParams params) {
        String clientProof = calculateClientProof(router.getPass(), params);
        if (clientProof == null) return false;

        String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<request><clientproof>" + clientProof + "</clientproof><finalnonce>" + params.getServernonce() + "</finalnonce></request>";

        RequestBody body = RequestBody.create(xmlBody, MediaType.parse(CONTENT_TYPE_BODY));
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + "/api/user/authentication_login")
                .post(body)
                .header(RESPONSE_SOURCE, BROSWER)
                .header(CONTENT_TYPE, CONTENT_TYPE_BODY)
                .header(REQUEST_VER_TOKEN, params.getRequestVerificationToken())
                .header(HOST, router.getAddress())
                .header(REFERER, HTTP + router.getAddress() + "/")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUESTED)
                .header(USER_AGENT, MOBILE_USER_AGENT)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                Log.w(AppController.LOG_TAG, "Хуавей B535a232a авторизация код = : " + response.code());
                String headerTwo = response.header("__RequestVerificationTokentwo");
                return headerTwo != null && !headerTwo.isEmpty();
            } else {
                Log.w(AppController.LOG_TAG, "Хуавей B535a232a ответ: " + response.code());
            }

        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса: " + e.getMessage());
        }
        return false;
    }

    private boolean askCellData(SimCardDataRouter router, boolean isFirstAttempt) throws Exception {
        Request requestNetwork = getCellRequest(router, API_MONITORING_STATUS);
        Request requestSignal = getCellRequest(router, API_DEVICE_SIGNAL);
        String networkBody;
        String signalBody;
        try (Response response = router.getHttpClient().newCall(requestNetwork).execute()) {
            networkBody = response.body().string();
        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса типа сети: " + e.getMessage());
            router.addError();
            return false;
        }

        try (Response response = router.getHttpClient().newCall(requestSignal).execute()) {
            signalBody = response.body().string();
        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка запроса сигнала: " + e.getMessage());
            router.addError();
            return false;
        }

        if (isFirstAttempt && isNeedFreshCookieCode(networkBody, signalBody)) {
            return fetchLteData(router, false);
        } else {
            String network;
            int levelSignal;
            levelSignal = getSignal(extractXmlTagGroup1Huawei(signalBody, PATTERN_HUAWEI_RSRP), extractXmlTagGroup1Huawei(signalBody, PATTERN_HUAWEI_RSSI));
            network = getHuaweiNetworkType(extractXmlTagGroup1Huawei(networkBody, PATTERN_HUAWEI_MODE));

            if (network.equals(SimListener.TYPE_XG)) {
                network = getHuaweiNetworkType(extractXmlTagGroup1Huawei(networkBody, PATTERN_HUAWEI_MODEEX));
            }

            router.setNetworkType(network);
            router.setSignalStrength(levelSignal);
            Log.i(AppController.LOG_TAG, "B525-232a  обновлён Сигнал: " + levelSignal + " | Сеть: " + network);

            if (!network.equals(SimListener.TYPE_XG) && levelSignal != -1) {
                setManager(router, this);
                return true;
            }
            router.addError();
            return false;
        }
    }

    private boolean isNeedFreshCookieCode(String networkBody, String signalBody) {
        return networkBody.contains("<code>125002</code>")
                || signalBody.contains("<code>125002</code>")
                || networkBody.contains("error")
                || signalBody.contains("error");
    }

    private Request getCellRequest(SimCardDataRouter router, String urlTail) {
        return new Request.Builder()
                .url(HTTP + router.getAddress() + urlTail)
                .header(HOST, router.getAddress())
                .header(RESPONSE_SOURCE, BROSWER)
                .header(REFERER, HTTP + router.getAddress() + "/")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUESTED)
                .header(USER_AGENT, MOBILE_USER_AGENT)
                .build();
    }

    private String generateFirstNonce() {
        byte[] nonce = new byte[32];
        mRandom.nextBytes(nonce);
        return bytesToHex(nonce);
    }

    @Nullable
    private synchronized String calculateClientProof(String password, HuaweiAuthParams params) {
        if (mSkf == null || mDigest == null || mMac == null) {
            return null;
        }
        byte[] saltBytes = hexToBytes(params.getSalt());
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, params.getIterations(), 256);
            byte[] saltedPassword = mSkf.generateSecret(spec).getEncoded();
            spec.clearPassword();

            byte[] clientKey = hmacSha256("Client Key".getBytes(StandardCharsets.UTF_8), saltedPassword);

            mDigest.reset();
            byte[] storedKey = mDigest.digest(clientKey);

            String authMessage = params.getFirstnonce() + "," + params.getServernonce() + "," + params.getServernonce();
            byte[] clientSignature = hmacSha256(authMessage.getBytes(StandardCharsets.UTF_8), storedKey);

            for (int i = 0; i < clientKey.length; i++) {
                clientKey[i] ^= clientSignature[i];
            }
            return bytesToHex(clientKey);
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized byte[] hmacSha256(byte[] key, byte[] data) throws InvalidKeyException {
        mMac.reset();
        mMac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mMac.doFinal(data);
    }
}
