package com.safelogj.simlog.routers.huawei;

import androidx.annotation.NonNull;

public class HuaweiAuthParams {

    private final String sessionId;
    private final String token;

    public HuaweiAuthParams(String sessionId, String token) {
        this.sessionId = sessionId;
        this.token = token;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getToken() {
        return token;
    }

    @NonNull
    @Override
    public String toString() {
        return "sessionId = " + sessionId + " token = " + token;
    }
}
