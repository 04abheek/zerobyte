package com.example.zerobyte;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class UploadActivity extends AppCompatActivity {
    private static final int PICK_FILE_REQUEST = 1;
    private Button btnUpload, btnCheckIPFS;
    private EditText txtFileStatus;
    private Uri fileUri;
    private IPFSManager ipfsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        btnUpload = findViewById(R.id.btnUpload);
        btnCheckIPFS = findViewById(R.id.btnCheckIPFS);
        txtFileStatus = findViewById(R.id.txtFileStatus);

        String ipfsAddress = getIntent().getStringExtra("IPFS_ADDRESS");
        if (ipfsAddress == null || ipfsAddress.isEmpty() || !isValidIPFSAddress(ipfsAddress)) {
            Toast.makeText(this, "Invalid IPFS address", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ipfsManager = new IPFSManager(this, ipfsAddress, new IPFSManager.IPFSInitListener() {
            @Override
            public void onIPFSInitialized() {
            }

            @Override
            public void onIPFSInitFailed(String error) {
                runOnUiThread(() -> Toast.makeText(UploadActivity.this, "Offline", Toast.LENGTH_SHORT).show());
            }
        });
        txtFileStatus.setOnClickListener(view -> openFileChooser());
        btnUpload.setOnClickListener(view -> uploadFileToIPFS());
        btnCheckIPFS.setOnClickListener(view -> checkIPFSStatus());
    }

    private boolean isValidIPFSAddress(String address) {
        return address.startsWith("/ip4/") && address.contains("/tcp/");
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            fileUri = data.getData();
            if (fileUri != null) {
                String fileName = getFileName(fileUri);
                txtFileStatus.setText(fileName != null ? fileName : "Unknown file");
            }
        }
    }

    private void uploadFileToIPFS() {
        if (fileUri == null) {
            Toast.makeText(this, "Select a file first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File file = getFileFromUri(fileUri);
            if (file == null || !file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }
            ipfsManager.uploadFileToIPFS(fileUri, getContentResolver(), new IPFSManager.IPFSUploadListener() {
                @Override
                public void onUploadSuccess(String cid) {
                    runOnUiThread(() -> {
                        txtFileStatus.setText(cid);
                        copyToClipboard(cid);
                        Toast.makeText(UploadActivity.this, "Uploaded! CID copied", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onUploadFailed(String error) {
                    runOnUiThread(() -> Toast.makeText(UploadActivity.this, "Upload failed", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkIPFSStatus() {
        if (ipfsManager == null) {
            Toast.makeText(this, "Offline", Toast.LENGTH_SHORT).show();
            return;
        }
        ipfsManager.isIPFSOnline(new IPFSManager.IPFSStatusListener() {
            @Override
            public void onIPFSStatusChecked(boolean isOnline, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(UploadActivity.this, isOnline ? "Online" : "Offline", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("CID", text);
        clipboard.setPrimaryClip(clip);
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private File getFileFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) throw new IOException("Cannot open file");
        File tempFile = File.createTempFile("ipfs_upload", null, getCacheDir());
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
        return tempFile;
    }
}