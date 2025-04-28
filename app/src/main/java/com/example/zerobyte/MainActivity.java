package com.example.zerobyte;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {
    private TextView tvWelcome;
    private EditText etIPAddress, etPortNumber;
    private Button btnUploadFile, btnDownloadFile, btnCheckIPFSStatus, btnLogout;
    private FirebaseAuth mAuth;
    private IPFSManager ipfsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        tvWelcome = findViewById(R.id.tvWelcome);
        etIPAddress = findViewById(R.id.etIPAddress);
        etPortNumber = findViewById(R.id.etPortNumber);
        btnUploadFile = findViewById(R.id.btnUploadFile);
        btnDownloadFile = findViewById(R.id.btnDownloadFile);
        btnCheckIPFSStatus = findViewById(R.id.btnCheckIPFSStatus);
        btnLogout = findViewById(R.id.btnLogout);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String welcomeMessage = "Welcome, " + currentUser.getEmail();
            tvWelcome.setText(welcomeMessage);
        } else {
            tvWelcome.setText("Welcome to ZeroByte");
        }

        btnUploadFile.setOnClickListener(v -> navigateToUploadActivity());
        btnDownloadFile.setOnClickListener(v -> navigateToDownloadActivity());
        btnCheckIPFSStatus.setOnClickListener(v -> checkIPFSStatus());
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    private String getIPFSAddress() {
        String ip = etIPAddress.getText().toString().trim();
        String port = etPortNumber.getText().toString().trim();
        return "/ip4/" + ip + "/tcp/" + port;
    }

    private void navigateToUploadActivity() {
        String ipfsAddress = getIPFSAddress();
        if (ipfsAddress.isEmpty() || !isValidIPFSAddress(ipfsAddress)) {
            Toast.makeText(this, "Please enter valid IP and Port", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MainActivity.this, UploadActivity.class);
        intent.putExtra("IPFS_ADDRESS", ipfsAddress);
        startActivity(intent);
    }

    private void navigateToDownloadActivity() {
        String ipfsAddress = getIPFSAddress();
        if (ipfsAddress.isEmpty() || !isValidIPFSAddress(ipfsAddress)) {
            Toast.makeText(this, "Please enter valid IP and Port", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MainActivity.this, DownloadActivity.class);
        intent.putExtra("IPFS_ADDRESS", ipfsAddress);
        startActivity(intent);
    }

    private boolean isValidIPFSAddress(String address) {
        return address.startsWith("/ip4/") && address.contains("/tcp/");
    }

    private void checkIPFSStatus() {
        String ipfsAddress = getIPFSAddress();
        if (ipfsAddress.isEmpty() || !isValidIPFSAddress(ipfsAddress)) {
            Toast.makeText(this, "Please enter valid IP and Port", Toast.LENGTH_SHORT).show();
            return;
        }

        ipfsManager = new IPFSManager(this, ipfsAddress, new IPFSManager.IPFSInitListener() {
            @Override
            public void onIPFSInitialized() {
                ipfsManager.isIPFSOnline(new IPFSManager.IPFSStatusListener() {
                    @Override
                    public void onIPFSStatusChecked(boolean isOnline, String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    MainActivity.this,
                                    isOnline ? "Online" : "Offline",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                });
            }

            @Override
            public void onIPFSInitFailed(String error) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Offline",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(MainActivity.this, LoginActivity.class));
        finish();
    }
}