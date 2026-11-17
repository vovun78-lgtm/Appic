package com.example.appic.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.appic.R;
import com.example.appic.utils.TermsHelper;

import java.io.File;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = this::navigateNext;
    private boolean navigated = false;
    private boolean firstStart = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Appic_Splash);
        super.onCreate(savedInstanceState);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_splash);

        ImageView ivSplash = findViewById(R.id.iv_splash);

        File customSplash = new File(getFilesDir(), "custom_splash.png");
        if (customSplash.exists()) {
            Glide.with(this)
                    .load(customSplash)
                    .signature(new ObjectKey(customSplash.length() + "_" + customSplash.lastModified()))
                    .centerCrop()
                    .into(ivSplash);
        } else {
            ivSplash.setImageResource(R.drawable.ic_launcher);
            ivSplash.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
        // Таймер запускается в onStart (см. ниже), чтобы корректно переживать
        // сворачивание приложения в первую секунду.
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (navigated) return;
        if (firstStart) {
            // ИСПРАВЛЕНО (G): первый запуск — показываем сплэш 1 секунду
            firstStart = false;
            handler.postDelayed(navigateRunnable, SPLASH_DELAY_MS);
        } else {
            // Возврат после сворачивания — сплэш уже был показан, уходим сразу
            handler.post(navigateRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ИСПРАВЛЕНО (G): отменяем таймер при уходе в фон, чтобы не было
        // startActivity из фона (Android 10+ это блокирует, пользователь
        // увидел бы «застывший» сплэш).
        handler.removeCallbacks(navigateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void navigateNext() {
        if (navigated) return;
        if (isFinishing() || isDestroyed()) return;
        navigated = true;

        Intent intent;
        if (!TermsHelper.isOnboardingDone(this)) {
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }
}