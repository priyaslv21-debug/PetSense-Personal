package com.example.pet.ui.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.example.pet.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PetPhotoLoader {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void bind(Context context, ImageView imageView, String photoUrl) {
        if (imageView == null) return;
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.pd_avatar_placeholder);
            return;
        }

        imageView.setImageResource(R.drawable.pd_avatar_placeholder);
        executor.execute(() -> {
            try {
                Bitmap loadedBitmap = null;
                if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                    URL url = new URL(photoUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.connect();
                    InputStream input = conn.getInputStream();
                    loadedBitmap = BitmapFactory.decodeStream(input);
                } else if (context != null) {
                    InputStream input = context.getContentResolver().openInputStream(Uri.parse(photoUrl));
                    loadedBitmap = BitmapFactory.decodeStream(input);
                }
                final Bitmap finalBitmap = loadedBitmap;
                if (finalBitmap != null) {
                    mainHandler.post(() -> imageView.setImageBitmap(finalBitmap));
                }
            } catch (Exception ignored) {
            }
        });
    }

    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
