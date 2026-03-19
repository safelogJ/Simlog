package com.safelogj.simlog.routers.tplink;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.collecting.SimCardDataRouter;
import com.safelogj.simlog.collecting.SimListener;
import com.safelogj.simlog.routers.CellDataUpdatable;
import com.safelogj.simlog.routers.RouterManager;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.IllegalFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TpLinkMr150Manager extends RouterManager implements CellDataUpdatable {

    private static final String GET_PARM = "getParm";
    private static final Pattern NETWORK_PATTERN = Pattern.compile("networkType=(\\d+)");
    private static final Pattern RSSI_PATTERN = Pattern.compile("rfInfoRssi=(-?\\d+)");
    private static final Pattern RSRP_PATTERN = Pattern.compile("rfInfoRsrp=(-?\\d+)");
    private static final Pattern NN_PATTERN = Pattern.compile("nn\\s*=\\s*\"?([^\"]+)\"?");
    private static final Pattern EE_PATTERN = Pattern.compile("ee\\s*=\\s*\"?([^\"]+)\"?");
    private static final Pattern SEQ_PATTERN = Pattern.compile("seq\\s*=\\s*\"?([^\"]+)\"?");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token\\s*=\\s*\"?([^\"]+)\"?");
    private static final String LOGOUT_PAYLOAD = "8\r\n[/cgi/logout#0,0,0,0,0,0#0,0,0,0,0,0]0,0\r\n"; // ACT_CGI=8
    private static final String LTE_PAYLOAD = "1&1\r\n[WAN_LTE_LINK_CFG#2,1,0,0,0,0#0,0,0,0,0,0]0,0\r\n[LTE_NET_STATUS#2,1,0,0,0,0#0,0,0,0,0,0]1,0\r\n"; // ACT_GET=1
    private MessageDigest mMessageDigest;
    private KeyFactory mKeyFactory;
    private Cipher mCipher;
    private Cipher mCipherNoPadding;

    public TpLinkMr150Manager() {
        try {
            mMessageDigest = MessageDigest.getInstance("MD5");
            mKeyFactory = KeyFactory.getInstance("RSA");
            mCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            mCipherNoPadding = Cipher.getInstance("RSA/ECB/NoPadding");
        } catch (NoSuchAlgorithmException | NullPointerException | NoSuchPaddingException e) {
            Log.d(AppController.LOG_TAG, "Ошибка в конструкторе TpLinkMr150Manager " + e.getClass().getName());
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
            Log.d(AppController.LOG_TAG, "Ошибка при работе менеджера = TpLinkMr150Manager " + e.getClass().getName());
        }
        router.addError();
        return false;
    }

    private boolean fetchLteData(SimCardDataRouter router) throws Exception {
        TpLinkAuthParams auth = getAuthParams(router);
        if (auth == null) return false;
        HttpUrl url = getLoginUrl(router, auth);
        if (url == null) return false;

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", null))
//                .addHeader("Origin", HTTP + router.getAddress())
                .addHeader(REFERER, HTTP + router.getAddress() + "/")
//                .addHeader("User-Agent",
//                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
//                                "(KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36")
                .addHeader(CONNECTION, CLOSE)
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK && fetchToken(router, auth)) {
                Log.d(AppController.LOG_TAG, "УСПЕХ! Мы вошли.");
                return getLteStatus(router, auth);
            }
        } catch (IOException | IllegalStateException e) {
            Log.d(AppController.LOG_TAG, "Ошибка Авторизации: " + e.getMessage());
        }
        router.addError();
        return false;
    }

    private HttpUrl getLoginUrl(SimCardDataRouter router, TpLinkAuthParams auth) {
        try {
            // 2. Генерируем AES ключи (как в JS: getTime + random)
            String timePart = String.valueOf(System.currentTimeMillis());
            String aesKey = (timePart + "123").substring(0, 16);
            String aesIv = (timePart + "456").substring(0, 16);
            auth.setAesKey(aesKey);
            auth.setAesIv(aesIv);
            // 3. Шифруем данные (admin\npassword)
            String plaintext = "admin\n" + router.getPass();
            String encryptedData = aesEncrypt(plaintext, aesKey, aesIv); // AES/CBC/PKCS5Padding
            // 4. Формируем подпись (sign)
            // ВАЖНО: h = MD5(name + pwd)
            String hash = md5("admin" + router.getPass());
            auth.setHash(hash);
            int signSeq = auth.getSeq() + encryptedData.length();
            // Строка в точности как в JS: this.aesKeyString+"&h="+this.hash+"&s="+seq
            String signPlain = "key=" + aesKey + "&iv=" + aesIv + "&h=" + hash + "&s=" + signSeq;
            String signature = rsaEncryptTpLink(signPlain, auth.getNn(), auth.getEe());
            HttpUrl loginUrl = HttpUrl.parse(HTTP + router.getAddress() + "/cgi/login");
            if (loginUrl != null) {
                return loginUrl.newBuilder()
                        .addQueryParameter("data", encryptedData)
                        .addQueryParameter("sign", signature)
                        .addQueryParameter("Action", "1")
                        .addQueryParameter("LoginStatus", "0")
                        .build();
            }
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка в getLoginUrl : " + e.getMessage());
        }
        router.addError();
        return null;
    }

    private TpLinkAuthParams getAuthParams(SimCardDataRouter router) throws IllegalArgumentException {
        String url = HTTP + router.getAddress() + "/cgi/" + GET_PARM;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0], null))
//                .addHeader("Origin", HTTP + router.getAddress())
                .addHeader(REFERER, HTTP + router.getAddress() + "/")
//                .addHeader("User-Agent",
//                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
//                                "(KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36")
                .addHeader(CONNECTION, CLOSE) // Закрываем соединение для надежности
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                String body = response.body().string();
                String nn = extractJsVar(body, NN_PATTERN);
                String ee = extractJsVar(body, EE_PATTERN);
                int seq = Integer.parseInt(extractJsVar(body, SEQ_PATTERN));

                TpLinkAuthParams p = new TpLinkAuthParams(nn, ee, seq);
                if (p.isValid()) return p;
            }
        } catch (IOException | IllegalStateException | NumberFormatException |
                 NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Ошибка ответа " + GET_PARM + " = " + e);
        }
        router.addError();
        return null;
    }

    private boolean fetchToken(SimCardDataRouter router, TpLinkAuthParams auth) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url(HTTP + router.getAddress() + "/")
//                .addHeader("Origin", HTTP + router.getAddress())
                .addHeader(REFERER, HTTP + router.getAddress() + "/login.htm")
//                .addHeader("User-Agent",
//                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
//                                "(KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36")
                .addHeader(CONNECTION, CLOSE) // Закрываем соединение для надежности
                .build();

        try (Response response = router.getHttpClient().newCall(request).execute()) {
            String body = response.body().string();
            String tokenID = extractJsVar(body, TOKEN_PATTERN);
            auth.setTokenID(tokenID);
            return !tokenID.isEmpty();
        } catch (IOException | IllegalStateException | NullPointerException e) {
            Log.e(AppController.LOG_TAG, "Ошибка получения токена: " + e.getMessage());
            return false;
        }
    }

    private boolean getLteStatus(SimCardDataRouter router, TpLinkAuthParams auth) throws IllegalArgumentException {
        //  String rawPayload = buildLtePayload();
        String requestBody = prepareGdprRequestBody(LTE_PAYLOAD, auth);
        if (requestBody != null) {
            Request request = getCgiGdprRequest(router, auth, requestBody);
            try (Response response = router.getHttpClient().newCall(request).execute()) {
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    String encryptedResponse = response.body().string().trim();
                    String decStr = aesDecrypt(encryptedResponse, auth.getAesKey(), auth.getAesIv());
                    boolean result = parseLteStatus(decStr, router);
                    logout(router, auth);
                    return result;
                } else {
                    Log.e(AppController.LOG_TAG, "Ответ ТП Линка на LТЕ = " + response.code());
                }

            } catch (Exception e) {
                Log.e(AppController.LOG_TAG, "Ошибка запроса в getLteStatus : " + e + " " + e.getClass().getName());
            }
        } else {
            Log.e(AppController.LOG_TAG, "prepareGdprRequestBody: NULL");
        }
        router.addError();
        return false;
    }

    private boolean parseLteStatus(String decryptedResponse, SimCardDataRouter router) {
        String networkType = getValue(decryptedResponse, NETWORK_PATTERN);
        String network = getNetworkType(networkType);
        router.setNetworkType(network);

        String rsrp = getValue(decryptedResponse, RSRP_PATTERN);
        String rssi = getValue(decryptedResponse, RSSI_PATTERN);
        int levelSignal = getSignal(rsrp, rssi);
        router.setSignalStrength(levelSignal);

        Log.w(AppController.LOG_TAG, "Тип сети: " + networkType);
        Log.w(AppController.LOG_TAG, "RSSI: " + rssi + " dBm");
        Log.w(AppController.LOG_TAG, "RSRP: " + rsrp + " dBm");
        Log.w(AppController.LOG_TAG, "Tp-Link - Тип сети: " + network + " Сигнал = " + levelSignal);
        if (!network.equals(SimListener.TYPE_XG) && levelSignal != -1) {
            setManager(router, this);
            return true;
//        } else {
//            resetManager(router);
        }
        router.addError();
        return false;
    }

    @NonNull
    private String getValue(String data, Pattern pattern) {
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            String value = matcher.group(1);
            return value == null ? AppController.EMPTY_STRING : value.trim();
        }
        return AppController.EMPTY_STRING;
    }

    private Request getCgiGdprRequest(SimCardDataRouter router, TpLinkAuthParams auth, @NotNull String requestBody) throws IllegalArgumentException {
        return new Request.Builder()
                .url(HTTP + router.getAddress() + "/cgi_gdpr")
                .post(RequestBody.create(requestBody, MediaType.parse("text/plain; charset=utf-8")))
                .addHeader("TokenID", auth.getTokenID()) // TokenID из залогиненной сессии
                .addHeader(REFERER, HTTP + router.getAddress() + "/")
                .addHeader(CONNECTION, CLOSE) // Закрываем соединение для надежности
                .build();
    }

    private void logout(SimCardDataRouter router, TpLinkAuthParams auth) throws IllegalArgumentException {
        String requestBody = prepareGdprRequestBody(LOGOUT_PAYLOAD, auth);
        if (requestBody == null) {
            return;
        }
        Request request = getCgiGdprRequest(router, auth, requestBody);
        try (Response response = router.getHttpClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) {
                Log.w(AppController.LOG_TAG, "Логаут удачный : " + response.code());
            } else {
                Log.e(AppController.LOG_TAG, "Ошибка логаута: " + response.code());
            }

        } catch (IOException | IllegalStateException e) {
            Log.e(AppController.LOG_TAG, "Ошибка запроса: " + e + " " + e.getClass().getName());
        }
    }

    /***
     private String buildLtePayload() {
     // Данные, которые ТЫ реально увидел в браузере
     // String defaultConnStack = "2,1,1,0,0,0";
     // stkPop
     String n = "2,1,0,0,0,0";
     String a = "2,0,0,0,0,0";
     String p = "0,0,0,0,0,0";


     StringBuilder sb = new StringBuilder();
     // ЧАСТЬ 1: Заголовок типов (tmpdata)
     // У нас 4 команды, все типа 1 (ACT_GET)
     sb.append("1&1&1&1\r\n");
     // ЧАСТЬ 2: Блоки данных (data)
     // Индекс 0
     sb.append("[WAN_LTE_LINK_CFG#").append(n).append("#").append(p).append("]0,0\r\n");
     // Индекс 1 (8 атрибутов)
     sb.append("[WAN_LTE_INTF_CFG#").append(a).append("#").append(p).append("]1,8\r\n");
     sb.append("dataLimit\r\n");
     sb.append("enablePaymentDay\r\n");
     sb.append("curStatistics\r\n");
     sb.append("totalStatistics\r\n");
     sb.append("enableDataLimit\r\n");
     sb.append("limitation\r\n");
     sb.append("curRxSpeed\r\n");
     sb.append("curTxSpeed\r\n");
     // Индекс 2
     sb.append("[LTE_NET_STATUS#").append(n).append("#").append(p).append("]2,0\r\n");
     // Индекс 3
     sb.append("[LTE_PROF_STAT#").append(n).append("#").append(p).append("]3,0\r\n");
     return sb.toString();
     }
     */

    private String prepareGdprRequestBody(String rawPayload, TpLinkAuthParams auth) {
        try {
            // --- Шаг 1: AES Шифрование данных (Аналог $.Iencryptor.AESEncrypt) ---
            String encryptedDataBase64 = aesEncrypt(rawPayload, auth.getAesKey(), auth.getAesIv());
            // --- Шаг 2: RSA Подпись (Аналог $.Iencryptor.getSignature) ---
            int dataLen = encryptedDataBase64.length();
            String s = "h=" + auth.getHash() + "&s=" + (auth.getSeq() + dataLen);
            // Шифруем s RSA ключом
            String signature = rsaEncryptTpLink(s, auth.getNn(), auth.getEe());
            // --- Шаг 3: Упаковка тела запроса (Аналог логики в tpAjax) ---
            return "sign=" + signature + "\r\ndata=" + encryptedDataBase64 + "\r\n";

        } catch (Exception e) {
            Log.i(AppController.LOG_TAG, "Тут ошибка : " + e.getMessage());
            return null;
        }
    }

    @NonNull
    private String extractJsVar(String js, Pattern p) {
        Matcher m = p.matcher(js);
        if (m.find()) {
            String value = m.group(1);
            return value == null ? AppController.EMPTY_STRING : value.trim();
        }
        return AppController.EMPTY_STRING; // Возвращаем пустоту, чтобы проверить её в вызывающем методе
    }

    private synchronized String md5(String input) throws IllegalFormatException, NullPointerException {
        if (mMessageDigest == null) return AppController.EMPTY_STRING;
        mMessageDigest.reset();
        byte[] digest = mMessageDigest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private synchronized String aesEncrypt(String plain, String key, String iv) throws InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalStateException, IllegalBlockSizeException, BadPaddingException {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        mCipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = mCipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private synchronized String aesDecrypt(String encrypted, String key, String iv) throws InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalStateException, IllegalBlockSizeException, BadPaddingException {
        byte[] data = Base64.decode(encrypted, Base64.DEFAULT);
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        mCipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decrypted = mCipher.doFinal(data);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private synchronized String rsaEncryptTpLink(String plain, String nnHex, String eeHex) throws NullPointerException, InvalidKeySpecException,
            UnsupportedOperationException, InvalidKeyException, IllegalStateException, IllegalBlockSizeException,
            BadPaddingException {
        BigInteger n = new BigInteger(nnHex, 16);
        BigInteger e = new BigInteger(eeHex, 16);

        RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(n, e);
        PublicKey publicKey = mKeyFactory.generatePublic(pubSpec);
        // В JS стоит 512 и 0 (NoPadding). В Java это требует блока ровно 64 байта.
        mCipherNoPadding.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        int blockSize = 64; // Для 512-битного ключа
        StringBuilder hexResult = new StringBuilder();

        for (int i = 0; i < data.length; i += blockSize) {
            byte[] block = new byte[blockSize]; // Инициализируется нулями (0x00)
            int chunkLen = Math.min(data.length - i, blockSize);
            System.arraycopy(data, i, block, 0, chunkLen);
            byte[] encrypted = mCipherNoPadding.doFinal(block);
            hexResult.append(bytesToHex(encrypted));
        }

        return hexResult.toString();
    }


    /**
     * "GSM", 2G (Самый базовый)
     * "CDMA 1x", 2G
     * "WCDMA", 3G (Стандарт)
     * "TD-SCDMA", 3G (Китай)
     * "CDMA 1x Ev-Do" 3G (Расширенный)
     * "4G LTE", 4G (Скоростной)
     * "4G+ LTE" 4G+ (Агрегация - Максимум)
     * networkType_str=["Сеть не найдена","GSM","WCDMA","4G LTE","TD-SCDMA","CDMA 1x","CDMA 1x Ev-Do","4G+ LTE"]
     */
    @NonNull
    private String getNetworkType(@NonNull String type) {
        String t = type.trim();
        return switch (t) {
            case "3", "7" -> SimListener.TYPE_4G;
            case "2", "4", "6" -> SimListener.TYPE_3G;
            case "1", "5" -> SimListener.TYPE_2G;
            default -> SimListener.TYPE_XG;
        };
    }
}
