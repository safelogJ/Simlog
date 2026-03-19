package com.safelogj.simlog.collecting;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.safelogj.simlog.AppController;

import java.lang.ref.WeakReference;

public class NotificationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WeakReference<Activity> activityWeakReference = ((AppController) getApplication()).getCurrentActivityRef();
        if (activityWeakReference != null) {
            Activity activity = activityWeakReference.get();
            if (activity != null && activity.getClass() == CollectActivity.class) {
                finish();
                return;
            }
        }
        Intent intent = new Intent(this, CollectActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}