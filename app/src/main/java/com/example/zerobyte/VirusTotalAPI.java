package com.example.zerobyte;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VirusTotalAPI {
    private static final String API_KEY = "c81338cbcf544fbf865db3975269ccfa28cb621e038541f5ea862571254c0e4c"; // Replace with actual key
    private static final String UPLOAD_URL = "https://www.virustotal.com/api/v3/files";
    private static final String REPORT_URL = "https://www.virustotal.com/api/v3/analyses/%s";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build();

    public static ScanResult scanFile(Uri fileUri, ContentResolver contentResolver) {
        try {
            String analysisId = uploadFile(fileUri, contentResolver);
            if (analysisId == null) {
                return new ScanResult("Upload failed", true);
            }
            String scanReport = pollScanResults(analysisId);
            if (scanReport == null) {
                return new ScanResult("Scan timeout", true);
            }
            return analyzeScanReport(scanReport);
        } catch (Exception e) {
            Log.e("VirusTotal", "Scan error", e);
            return new ScanResult("Scan failed: " + e.getMessage(), true);
        }
    }

    private static String uploadFile(Uri fileUri, ContentResolver contentResolver)
            throws IOException, JSONException {
        try (InputStream inputStream = contentResolver.openInputStream(fileUri)) {
            if (inputStream == null) {
                throw new IOException("Cannot open file stream");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[16384];
            int bytesRead;
            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "file",
                            RequestBody.create(buffer.toByteArray(), MediaType.get("application/octet-stream")))
                    .build();
            Request request = new Request.Builder()
                    .url(UPLOAD_URL)
                    .post(body)
                    .addHeader("x-apikey", API_KEY)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Upload failed: " + response.code());
                }
                String json = response.body().string();
                return new JSONObject(json).getJSONObject("data").getString("id");
            }
        }
    }

    private static String pollScanResults(String analysisId) {
        int maxAttempts = 7;
        int initialDelayMs = 4000;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(initialDelayMs * (int)Math.pow(2, attempt));
                String report = getScanReport(analysisId);
                JSONObject json = new JSONObject(report);
                String status = json.getJSONObject("data")
                        .getJSONObject("attributes")
                        .getString("status");
                if ("completed".equals(status)) {
                    return report;
                }
            } catch (Exception e) {
                Log.w("VirusTotal", "Polling attempt " + (attempt + 1) + " failed", e);
            }
        }
        return null;
    }

    private static String getScanReport(String analysisId) throws IOException {
        Request request = new Request.Builder()
                .url(String.format(Locale.US, REPORT_URL, analysisId))
                .get()
                .addHeader("x-apikey", API_KEY)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API error: " + response.code());
            }
            return response.body().string();
        } catch (Exception e) {
            throw new IOException("Network error: " + e.getMessage());
        }
    }

    private static ScanResult analyzeScanReport(String scanReport) throws JSONException {
        JSONObject report = new JSONObject(scanReport);
        JSONObject stats = report.getJSONObject("data")
                .getJSONObject("attributes")
                .getJSONObject("stats");
        return new ScanResult(
                stats.getInt("malicious"),
                stats.getInt("suspicious"),
                stats.getInt("undetected")
        );
    }

    public static class ScanResult {
        public final int maliciousCount;
        public final int suspiciousCount;
        public final int cleanCount;
        public final boolean isError;
        public final String errorMessage;
        public ScanResult(int malicious, int suspicious, int clean) {
            this.maliciousCount = malicious;
            this.suspiciousCount = suspicious;
            this.cleanCount = clean;
            this.isError = false;
            this.errorMessage = null;
        }
        public ScanResult(String error, boolean isError) {
            this.maliciousCount = 0;
            this.suspiciousCount = 0;
            this.cleanCount = 0;
            this.isError = isError;
            this.errorMessage = error;
        }
        public boolean isInfected() {
            return maliciousCount > 0 || suspiciousCount > 0;
        }
    }
}