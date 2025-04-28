package com.example.smarttrack;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class SmartTrackApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
    }
}
