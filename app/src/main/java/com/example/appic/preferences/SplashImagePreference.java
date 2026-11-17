package com.example.appic.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.appic.R;

import java.io.File;

public class SplashImagePreference extends Preference {

    public SplashImagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_splash_image);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        ImageView imageView = (ImageView) holder.findViewById(R.id.iv_splash_thumbnail);
        if (imageView != null) {
            File customSplash = new File(getContext().getFilesDir(), "custom_splash.png");
            
            if (customSplash.exists()) {
                // ИСПОЛЬЗУЕМ SIGNATURE, чтобы Glide не кэшировал старую картинку
                Glide.with(getContext())
                        .load(customSplash)
                        .signature(new ObjectKey(customSplash.length() + "_" + customSplash.lastModified()))
                        .centerCrop()
                        .into(imageView);
            } else {
                Glide.with(getContext())
                        .load(R.drawable.ic_launcher)
                        .centerCrop()
                        .into(imageView);
            }
        }
    }
}