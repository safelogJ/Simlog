package com.safelogj.simlog.collecting;

import android.util.Log;

import com.safelogj.simlog.AppController;
import com.safelogj.simlog.routers.LoggingInterceptor;
import com.safelogj.simlog.routers.cudy.CudyLT500Manager;
import com.safelogj.simlog.routers.huawei.HuaweiManagerB535a232a;
import com.safelogj.simlog.routers.huawei.HuaweiManagerB593s;
import com.safelogj.simlog.routers.huawei.HuaweiManagerE3372h320;
import com.safelogj.simlog.routers.keenetic.KeeneticManagerCdc;
import com.safelogj.simlog.routers.keenetic.KeeneticManagerCustomCmd;
import com.safelogj.simlog.routers.keenetic.KeeneticManagerLte;
import com.safelogj.simlog.routers.keenetic.KeeneticManagerModem;
import com.safelogj.simlog.routers.keenetic.KeeneticManagerQmi;
import com.safelogj.simlog.routers.RouterCookieJar;
import com.safelogj.simlog.routers.CellDataUpdatable;
import com.safelogj.simlog.routers.tplink.TpLinkMr150Manager;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class SimCardDataRouter extends SimCardData {


    private static final int CONNECT_TIMEOUT = 4;
    private static final int WRITE_TIMEOUT = 2;
    private static final int READ_TIMEOUT = 6;
    public static final int FULL_TIMEOUT = CONNECT_TIMEOUT + WRITE_TIMEOUT + READ_TIMEOUT;

    private static final CellDataUpdatable[] ROUTERS_MANAGERS = new CellDataUpdatable[] {

            new KeeneticManagerLte(),
            new KeeneticManagerModem(),
            new KeeneticManagerCdc(),
            new KeeneticManagerQmi(),
            new HuaweiManagerB593s(),
            new CudyLT500Manager(),
            new TpLinkMr150Manager(),
            new HuaweiManagerE3372h320(),
            new HuaweiManagerB535a232a()
    };

    private static final CellDataUpdatable KEENETIC_CUSTOM_CMD_MANAGER = new KeeneticManagerCustomCmd();
    private static final int ALL_MANAGERS = ROUTERS_MANAGERS.length + 1;


    private static final String ROUTER_FILE_NAME_PATTERN = "%s_%s.txt";
    private final String address;
    private final String login;
    private final String pass;
    private final String customCommand;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS) // Время на установку связи с роутером
            .writeTimeout(5, TimeUnit.SECONDS)   // Время на отправку данных (для POST)
            .readTimeout(5, TimeUnit.SECONDS)    // Время на ожидание ответа от роутера
            .callTimeout(FULL_TIMEOUT, TimeUnit.SECONDS) // Общее время на весь запрос с ответом, чтоб не переподключалось много раз
            .retryOnConnectionFailure(true)
          //   .addInterceptor(new LoggingInterceptor())
            .cookieJar(new RouterCookieJar()).build();
    private volatile CellDataUpdatable routerManager;

    public SimCardDataRouter(String address, String login, String pass, String customCommand) {
        super(0, AppController.EMPTY_STRING, 0, address);
        this.address = address;
        this.login = login;
        this.pass = pass;
        this.customCommand = customCommand;
    }

    @Override
    public boolean isRouter() {
        return true;
    }

    @Override
    protected String getFileName() {
        return String.format(Locale.US, ROUTER_FILE_NAME_PATTERN, address, mDate);
    }

    public String getAddress() {
        return address;
    }

    public String getLogin() {
        return login;
    }

    public String getPass() {
        return pass;
    }

    @Override
    public int getWriteErrors() {
        return Math.max(0, super.getWriteErrors() - ALL_MANAGERS);
    }

    public void setRouterManager(CellDataUpdatable routerManager) {
        this.routerManager = routerManager;
    }

    public CellDataUpdatable getRouterManager() {
        return routerManager;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public String getCustomCommand() {
        return customCommand;
    }

    public void clearCookie() {
        if(httpClient.cookieJar() instanceof RouterCookieJar routerCookieJar) {
            routerCookieJar.clearCookie();
        }
    }

    public void updateData() throws InterruptedException {
        if (!customCommand.isEmpty()) {
            KEENETIC_CUSTOM_CMD_MANAGER.setNewDataToSimCard(this);

        } else if (routerManager != null) {
            Log.d(AppController.LOG_TAG, "Знаем фабрику для " + address);
            routerManager.setNewDataToSimCard(this);

        } else {
            for (CellDataUpdatable manager : ROUTERS_MANAGERS) {
                if (manager.setNewDataToSimCard(this)) {
                    return;
                }

                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

}
