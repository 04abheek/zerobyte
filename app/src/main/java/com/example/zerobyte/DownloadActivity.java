package com.example.zerobyte;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class DownloadActivity extends AppCompatActivity {
    private static final int PICK_SAVE_LOCATION_REQUEST = 1001;

    private EditText edtFileHash;
    private Button btnDownload, btnCheckStatus;
    private ProgressDialog progressDialog;
    private IPFSManager ipfsManager;
    private Uri saveLocationUri;
    private String currentFileHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        edtFileHash = findViewById(R.id.edtFileHash);
        btnDownload = findViewById(R.id.btnDownloadFile);
        btnCheckStatus = findViewById(R.id.btnCheckIPFSStatus);
        btnDownload.setOnClickListener(v -> handleDownloadClick());
        btnCheckStatus.setOnClickListener(v -> checkIPFSStatus());
        initializeIPFS();
    }

    private void initializeIPFS() {
        String ipfsAddress = getIntent().getStringExtra("IPFS_ADDRESS");
        if (ipfsAddress == null || ipfsAddress.isEmpty()) {
            showToast("Invalid IPFS address");
            finish();
            return;
        }
        ipfsManager = new IPFSManager(this, ipfsAddress, new IPFSManager.IPFSInitListener() {
            @Override
            public void onIPFSInitialized() {
            }

            @Override
            public void onIPFSInitFailed(String error) {
                showToast("Offline: " + error);
            }
        });
    }

    private void checkIPFSStatus() {
        if (ipfsManager == null) {
            showToast("Offline: Not initialized");
            return;
        }
        showProgress("Checking connection...");
        ipfsManager.isIPFSOnline(new IPFSManager.IPFSStatusListener() {
            @Override
            public void onIPFSStatusChecked(boolean isOnline, String message) {
                dismissProgress();
                showToast(isOnline ? "Online" : "Offline: " + message);
            }
        });
    }

    private void handleDownloadClick() {
        String fileHash = edtFileHash.getText().toString().trim();
        if (isValidFileHash(fileHash)) {
            currentFileHash = fileHash;
            requestSaveLocation();
        } else {
            showToast("Invalid IPFS hash (must start with Qm)");
        }
    }

    private boolean isValidFileHash(String hash) {
        return hash != null && hash.startsWith("Qm") && hash.length() > 10;
    }

    private void requestSaveLocation() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "downloaded_file");
        startActivityForResult(intent, PICK_SAVE_LOCATION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_SAVE_LOCATION_REQUEST && resultCode == RESULT_OK && data != null) {
            saveLocationUri = data.getData();
            if (saveLocationUri != null) {
                startVirusScan();
            }
        }
    }

    private void startVirusScan() {
        showProgress("Scanning for viruses...");
        new Thread(() -> {
            VirusTotalAPI.ScanResult result = VirusTotalAPI.scanFile(saveLocationUri, getContentResolver());
            runOnUiThread(() -> {
                dismissProgress();
                handleScanResult(result);
            });
        }).start();
    }

    private void handleScanResult(VirusTotalAPI.ScanResult result) {
        if (result.isError) {
            showErrorDialog("Scan Failed", result.errorMessage, true);
        } else if (result.isInfected()) {
            showVirusWarning(result);
        } else {
            startFileDownload();
        }
    }

    private void showVirusWarning(VirusTotalAPI.ScanResult result) {
        String message = String.format(
                "Security Alert!\n\n" +
                        "• Malicious detections: %d\n" +
                        "• Suspicious detections: %d\n" +
                        "• Clean scans: %d\n\n" +
                        "This file may harm your device. Download anyway?",
                result.maliciousCount,
                result.suspiciousCount,
                result.cleanCount
        );

        new AlertDialog.Builder(this)
                .setTitle("Virus Detected")
                .setMessage(message)
                .setPositiveButton("Download", (d, w) -> startFileDownload())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void showErrorDialog(String title, String message, boolean allowRetry) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert);
        if (allowRetry) {
            builder.setPositiveButton("Retry", (d, w) -> startVirusScan());
        }
        builder.setNegativeButton("OK", null).show();
    }

    private void startFileDownload() {
        showProgress("Downloading file...");
        ipfsManager.downloadFileFromIPFS(currentFileHash, saveLocationUri,
                getContentResolver(), new IPFSManager.IPFSDownloadListener() {
                    @Override
                    public void onDownloadSuccess(byte[] fileData) {
                        runOnUiThread(() -> {
                            dismissProgress();
                            showToast("Download completed successfully");
                        });
                    }

                    @Override
                    public void onDownloadFailed(String error) {
                        runOnUiThread(() -> {
                            dismissProgress();
                            showErrorDialog("Download Failed", error, true);
                        });
                    }
                });
    }

    private void showProgress(String message) {
        runOnUiThread(() -> {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog(this);
                progressDialog.setCancelable(false);
            }
            progressDialog.setMessage(message);
            if (!progressDialog.isShowing()) {
                progressDialog.show();
            }
        });
    }

    private void dismissProgress() {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}