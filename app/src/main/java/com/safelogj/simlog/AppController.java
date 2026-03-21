package com.safelogj.simlog;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.PowerManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.safelogj.simlog.collecting.SavedRouter;
import com.safelogj.simlog.collecting.SimCardData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AppController extends Application {
    public static final String NOTIFICATION_CHANNEL_ID = "Notification_CHANNEL_ID";
    public static final String LOG_TAG = "simlog";
    public static final String EMPTY_STRING = "";
    private static final String PRIVACY_ID = "mPrivacyId";
    private static final String SETTINGS = "settings";
    private static final String ADS = "ads.txt";
    private static final String ROUTERS = "routers";
    private static final String ROUTERS_JSON = "routers.txt";
    private static final String ROUTER_ADDRESS = "routerAddress";
    private static final String ROUTER_LOGIN = "routerLogin";
    private static final String ROUTER_PASS = "routerPass";
    private static final String ROUTER_CUSTOM_COMMAND = "routerCustomCommand";
    private static final int GCM_TAG_LENGTH = 16; // Длина аутентификационного тега в байтах (128 бит)
    private static final int AES_KEY_SIZE = 256;
    private static final String ENCRYPTED_DATA_KEY = "encryptedData"; // Ключ для хранения зашифрованных данных в файле
    private static final String CURRENT_ROUTER = "currentRouter";
    private static final String ROUTERS_LIST = "routersList";
    private static final String KEY_ALIAS = "SavedRouterKeyAlias";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String USER_COLLECT_IMAGE = "user_collect_image.png";
    private static final String USER_CHOOSE_IMAGE = "user_choose_image.png";
    private static final String DEFAULT_PRIVACY = "https://github.com/safelogJ/Simlog/blob/master/privacy";
    private static final String PRIVACY_CONCATENATOR = "\n";
    private final ScheduledExecutorService saveFileExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Path> mFilesPaths = new HashMap<>();
    private final SavedRouter currentRouter = new SavedRouter();
    private List<SimCardData> mCheckedSims;
    private File mExternalFileDir;
    private int mPrivacyId;
    private Bitmap userCollectImage;
    private final Map<String, SavedRouter> savedRoutersMap = new LinkedHashMap<>();

    private PowerManager mPowerManager;
    private NotificationManagerCompat mNotificationManagerCompat;
    private ColorStateList mBtnBackColorGreen;
    private ColorStateList mBtnBackColorBlack;
    private ColorStateList mBtnRipleColorGreen;
    private ColorStateList mBtnRipleColorBlack;
    private WeakReference<Activity> currentActivityRef;
    private Cipher mCipher;

    public List<SimCardData> getCheckedSims() {
        return mCheckedSims;
    }

    public void setCheckedSims(List<SimCardData> checkedSims) {
        mCheckedSims = checkedSims;
    }

    public Map<String, Path> getFilesPaths() {
        updateFilesPaths();
        return mFilesPaths;
    }

    public int getPrivacyId() {
        return mPrivacyId;
    }

    public void setPrivacyId(int mPrivacyId) {
        this.mPrivacyId = mPrivacyId;
    }

    public PowerManager getPowerManager() {
        return mPowerManager;
    }

    public NotificationManagerCompat getNotificationManagerCompat() {
        return mNotificationManagerCompat;
    }

    public ColorStateList getBtnBackColorGreen() {
        return mBtnBackColorGreen;
    }

    public ColorStateList getBtnBackColorBlack() {
        return mBtnBackColorBlack;
    }

    public ColorStateList getBtnRipleColorGreen() {
        return mBtnRipleColorGreen;
    }

    public ColorStateList getBtnRipleColorBlack() {
        return mBtnRipleColorBlack;
    }

    public Bitmap getUserCollectImage() {
        return userCollectImage;
    }

    public void setUserCollectImage(Bitmap userImage) {
        this.userCollectImage = userImage;
        saveUserImage(userImage, USER_COLLECT_IMAGE);
    }

    public SavedRouter getCurrentRouter() {
        return currentRouter;
    }

    public Map<String, SavedRouter> getSavedRoutersMap() {
        return savedRoutersMap;
    }

    public void addRouterToMap(SavedRouter router) {
        savedRoutersMap.put(router.getAddress(), router);
        writeRoutersListEncrypted();
    }

    public void removeRouterFromMap(SavedRouter router) {
        savedRoutersMap.remove(router.getAddress());
        writeRoutersListEncrypted();
    }

    public WeakReference<Activity> getCurrentActivityRef() {
        return currentActivityRef;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        regActivityListener();
        mExternalFileDir = getExternalFilesDir(null);
        SimCardData.setExternalFileDir(mExternalFileDir);
        createNotificationChannel();
        readSettings();
        initCollectIcons();
        loadUsersImage();
        readRoutersListEncrypted();
    }

    public String getPrivacyText() {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        try (InputStream inputStream = getResources().openRawResource(R.raw.privacy_text);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append(PRIVACY_CONCATENATOR);
            }
        } catch (IOException e) {
            return DEFAULT_PRIVACY;
        }
        return stringBuilder.toString().trim();
    }

    public boolean updateFilesPaths() {
        mFilesPaths.clear();
        if (mExternalFileDir != null && mExternalFileDir.isDirectory()) {
            File[] files = mExternalFileDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        mFilesPaths.put(file.getName(), file.toPath());
                    }
                }
            }
        }
        return mFilesPaths.isEmpty();
    }

    public void writeSetting() {
        JSONObject settingsJson = new JSONObject();
        try {
            settingsJson.put(PRIVACY_ID, mPrivacyId);

            File settingsDir = new File(getFilesDir(), SETTINGS);
            if (!settingsDir.isDirectory() && !settingsDir.mkdirs()) {
                return;
            }
            File settingsFile = new File(settingsDir, ADS);
            try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                fos.write(settingsJson.toString().getBytes());
                fos.flush();
            }

        } catch (JSONException | IOException e) {
            //  Log.d("MyApplication", "Ошибка при сохранении настроек: " + e.getMessage());
        }

    }

    private void readSettings() {
        File settingsDir = new File(getFilesDir(), SETTINGS);
        File settingsFile = new File(settingsDir, ADS);

        if (settingsFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                byte[] data = new byte[(int) settingsFile.length()];
                fis.read(data);
                String jsonStr = new String(data);
                JSONObject settingsJson = new JSONObject(jsonStr);
                mPrivacyId = settingsJson.optInt(PRIVACY_ID, 0);
            } catch (IOException | JSONException e) {
                //   Log.d("MyApplication", "Ошибка при загрузке настроек: " + e.getMessage());
            }
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }


    private void initCollectIcons() {
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mNotificationManagerCompat = NotificationManagerCompat.from(this);
        mBtnBackColorGreen = ContextCompat.getColorStateList(getApplicationContext(), R.color.green_600);
        mBtnBackColorBlack = ContextCompat.getColorStateList(getApplicationContext(), R.color.black3);
        mBtnRipleColorGreen = ContextCompat.getColorStateList(getApplicationContext(), R.color.green_100);
        mBtnRipleColorBlack = ContextCompat.getColorStateList(getApplicationContext(), R.color.spinner_font);
    }

    public void saveUserImage(Bitmap userImage, String image) {
        saveFileExecutor.schedule(() -> {
        try {
            File file = new File(getFilesDir(), image);
            FileOutputStream fos = new FileOutputStream(file);
            userImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
          //  e.printStackTrace();
        }
        }, 0, TimeUnit.SECONDS);
    }

    private void loadUsersImage() {
        saveFileExecutor.schedule(() -> {
            try {
                File file = new File(getFilesDir(), USER_COLLECT_IMAGE);
                if (file.exists()) {
                    userCollectImage = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            } catch (Exception e) {
                userCollectImage = null;
            }
            try {
                File file = new File(getFilesDir(), USER_CHOOSE_IMAGE);
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
              //
            }
        }, 0, TimeUnit.SECONDS);
    }

    private void buildJsonFromRouter(JSONObject routerJson, SavedRouter router) throws JSONException {
        String address = router.getAddress();
        routerJson.put(ROUTER_ADDRESS, address != null ? address : EMPTY_STRING);
        String login = router.getLogin();
        routerJson.put(ROUTER_LOGIN, login != null ? login : EMPTY_STRING);
        String pass = router.getPass();
        routerJson.put(ROUTER_PASS, pass != null ? pass : EMPTY_STRING);
        String customCommand = router.getCustomCommand();
        routerJson.put(ROUTER_CUSTOM_COMMAND, customCommand != null ? customCommand : EMPTY_STRING);
    }

    private void readRouterFromJson(JSONObject routerJson, SavedRouter router) {
        String address = routerJson.optString(ROUTER_ADDRESS, EMPTY_STRING);
        String login = routerJson.optString(ROUTER_LOGIN, EMPTY_STRING);
        String pass = routerJson.optString(ROUTER_PASS, EMPTY_STRING);
        String customCommand = routerJson.optString(ROUTER_CUSTOM_COMMAND, EMPTY_STRING);
        router.setAddress(address);
        router.setLogin(login);
        router.setPass(pass);
        router.setCustomCommand(customCommand);
    }

    public void writeRoutersListEncrypted() {
        saveFileExecutor.schedule(()-> {
            File routersListDir = new File(getFilesDir(), ROUTERS);
            if (!routersListDir.exists() && !routersListDir.mkdirs()) {
                Log.d(LOG_TAG, "Failed to create directory.");
                return;
            }

            File routersListFile = new File(routersListDir, ROUTERS_JSON);

            JSONObject rootJson = new JSONObject();
            JSONObject routersJson = new JSONObject();
            try {
                for (Map.Entry<String, SavedRouter> entry : savedRoutersMap.entrySet()) {
                    JSONObject routerJson = new JSONObject();
                    buildJsonFromRouter(routerJson, entry.getValue()); // Пароли здесь в открытом виде
                    routersJson.put(entry.getKey(), routerJson);
                }

                JSONObject currentRouterJson = new JSONObject();
                buildJsonFromRouter(currentRouterJson, currentRouter); // Пароль здесь в открытом виде

                rootJson.put(ROUTERS_LIST, routersJson);
                rootJson.put(CURRENT_ROUTER, currentRouterJson);

                // 2. Шифрование всего JSON-контента
                String rawJsonString = rootJson.toString();
                byte[] rawJsonBytes = rawJsonString.getBytes(StandardCharsets.UTF_8);
                byte[] encryptedCombinedBytes = encrypt(rawJsonBytes);
                String encryptedBase64 = Base64.encodeToString(encryptedCombinedBytes, Base64.NO_WRAP);

                // 3. Создание JSON-оболочки для записи в файл
                JSONObject fileWrapper = new JSONObject();
                fileWrapper.put(ENCRYPTED_DATA_KEY, encryptedBase64);

                // 4. Запись JSON-оболочки в файл
                try (FileWriter file = new FileWriter(routersListFile)) {
                    file.write(fileWrapper.toString(4));
                }

            } catch (Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
                Log.d(LOG_TAG, "Error writing encrypted JSON file or key management failure: ", e);
            }
        }, 0, TimeUnit.SECONDS);

    }

    public void readRoutersListEncrypted() {
        saveFileExecutor.schedule(()-> {
            File routersListDir = new File(getFilesDir(), ROUTERS);
            File routersListFile = new File(routersListDir, ROUTERS_JSON);
            StringBuilder fileContent = new StringBuilder();

            if (!routersListFile.exists()) {
                Log.d(LOG_TAG, "Encrypted settings file not found.");
                return;
            }

            // 1. Чтение содержимого файла-оболочки
            try (FileReader reader = new FileReader(routersListFile)) {
                char[] buffer = new char[1024];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    fileContent.append(buffer, 0, length);
                }
            } catch (IOException e) {
                Log.d(LOG_TAG, "Error reading encrypted settings file: ", e);
                return;
            }

            // 2. Извлечение и дешифрование данных
            try {
                JSONObject fileWrapper = new JSONObject(fileContent.toString());
                String encryptedBase64 = fileWrapper.getString(ENCRYPTED_DATA_KEY);

                // Декодирование и дешифрование
                byte[] combinedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT); // Base64.DEFAULT безопасно для декодирования
                byte[] decryptedBytes = decrypt(combinedBytes);
                String rawJsonString = new String(decryptedBytes, StandardCharsets.UTF_8);

                // 3. Парсинг дешифрованного полного JSON
                JSONObject rootJson = new JSONObject(rawJsonString);
                JSONObject routersJson = rootJson.getJSONObject(ROUTERS_LIST);

             //   savedRoutersMap.clear(); // Очищаем старые данные перед чтением новых
                for (Iterator<String> it = routersJson.keys(); it.hasNext(); ) {
                    String key = it.next();
                    JSONObject routerJson = routersJson.getJSONObject(key);

                    SavedRouter router = new SavedRouter();
                    readRouterFromJson(routerJson, router); // Использует открытый пароль из JSON
                    savedRoutersMap.put(key, router);
                }

                JSONObject currentRouterJson = rootJson.getJSONObject(CURRENT_ROUTER);
                readRouterFromJson(currentRouterJson, currentRouter); // Использует открытый пароль из JSON

            } catch (
                    Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
                Log.d(LOG_TAG, "Error reading or decrypting full JSON data: ", e);
            }

        }, 0, TimeUnit.SECONDS);

    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        // Попытка получить существующий ключ
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        // Если ключа нет, создаем новый (Требуется API 23+ для KeyGenParameterSpec)
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

        // Настройка параметров: AES/GCM/NoPadding
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build());

        return keyGenerator.generateKey();
    }


    private byte[] encrypt(byte[] dataBytes) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        if (mCipher == null) {
            mCipher = Cipher.getInstance(TRANSFORMATION);
        }
        mCipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] iv = mCipher.getIV();
        byte[] encryptedData = mCipher.doFinal(dataBytes);
        byte[] combined = new byte[1 + iv.length + encryptedData.length];
        combined[0] = (byte) iv.length; // Сохраняем длину IV в первом байте
        System.arraycopy(iv, 0, combined, 1, iv.length); // Копируем IV начиная со второго байта
        System.arraycopy(encryptedData, 0, combined, 1 + iv.length, encryptedData.length); // Копируем данные
        return combined;
    }

    private byte[] decrypt(byte[] combinedBytes) throws Exception {
        // Минимальная длина: 1 байт (длина IV) + 1 байт (IV) + 16 байт (GCM Tag) = 18 байт
        if (combinedBytes.length < 1 + GCM_TAG_LENGTH) {
            throw new InvalidKeyException("Combined data too short to contain IV length and GCM Tag.");
        }

        int ivLength = combinedBytes[0] & 0xFF; // Получаем фактическую длину IV из первого байта
        // Проверяем, достаточно ли данных для IV и GCM Tag
        if (combinedBytes.length < 1 + ivLength + GCM_TAG_LENGTH) {
            throw new InvalidKeyException("IV length leads to combined data too short for GCM Tag.");
        }
        // Извлекаем IV
        byte[] iv = Arrays.copyOfRange(combinedBytes, 1, 1 + ivLength);
        // Извлекаем зашифрованные данные (начинаются после байта длины и IV)
        byte[] encryptedData = Arrays.copyOfRange(combinedBytes, 1 + ivLength, combinedBytes.length);

        SecretKey secretKey = getOrCreateSecretKey();
        mCipher = Cipher.getInstance(TRANSFORMATION);
        // GCM_TAG_LENGTH * 8, так как длина тега указывается в битах (16 байт * 8 = 128 бит)
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

        mCipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return mCipher.doFinal(encryptedData);
    }

    private void regActivityListener() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                //
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                //
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
                if (current == activity) {
                    currentActivityRef = null;
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                //
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                //
            }
        });
    }
}
