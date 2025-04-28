package com.example.zerobyte;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import io.ipfs.multiaddr.MultiAddress;

public class IPFSManager {
    private IPFS ipfs;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final String ipfsAddress;

    public IPFSManager(Object context, String ipfsAddress, IPFSInitListener listener) {
        this.ipfsAddress = ipfsAddress;
        this.executorService = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeIPFS(listener);
    }

    private void initializeIPFS(IPFSInitListener listener) {
        executorService.execute(() -> {
            try {
                ipfs = new IPFS(new MultiAddress(ipfsAddress));
                ipfs.refs.local();
                mainHandler.post(listener::onIPFSInitialized);
            } catch (Exception e) {
                mainHandler.post(() -> listener.onIPFSInitFailed(e.getMessage()));
            }
        });
    }

    public void uploadFileToIPFS(Uri fileUri, ContentResolver contentResolver, IPFSUploadListener listener) {
        executorService.execute(() -> {
            File tempFile = null;
            try {
                InputStream inputStream = contentResolver.openInputStream(fileUri);
                if (inputStream == null) {
                    mainHandler.post(() -> listener.onUploadFailed("Cannot open file"));
                    return;
                }
                tempFile = File.createTempFile("ipfs_upload", ".tmp");
                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                NamedStreamable.FileWrapper fileWrapper = new NamedStreamable.FileWrapper(tempFile);
                List<MerkleNode> result = ipfs.add(fileWrapper);
                String cid = result.get(0).hash.toBase58();
                mainHandler.post(() -> listener.onUploadSuccess(cid));
            } catch (Exception e) {
                mainHandler.post(() -> listener.onUploadFailed(e.getMessage()));
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
    }

    public void downloadFileFromIPFS(String cid, Uri saveLocationUri,
                                     ContentResolver contentResolver, IPFSDownloadListener listener) {
        executorService.execute(() -> {
            try (InputStream inputStream = ipfs.catStream(Multihash.fromBase58(cid));
                 OutputStream outputStream = contentResolver.openOutputStream(saveLocationUri)) {
                if (outputStream == null) {
                    mainHandler.post(() -> listener.onDownloadFailed("Cannot create output file"));
                    return;
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                mainHandler.post(() -> listener.onDownloadSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> listener.onDownloadFailed(e.getMessage()));
            }
        });
    }

    public void isIPFSOnline(IPFSStatusListener listener) {
        executorService.execute(() -> {
            try {
                ipfs.refs.local();
                mainHandler.post(() -> listener.onIPFSStatusChecked(true, "Connected"));
            } catch (Exception e) {
                mainHandler.post(() -> listener.onIPFSStatusChecked(false,
                        e.getMessage() != null ? e.getMessage() : "Connection failed"));
            }
        });
    }

    public interface IPFSInitListener {
        void onIPFSInitialized();
        void onIPFSInitFailed(String error);
    }

    public interface IPFSStatusListener {
        void onIPFSStatusChecked(boolean isOnline, String message);
    }

    public interface IPFSUploadListener {
        void onUploadSuccess(String cid);
        void onUploadFailed(String error);
    }

    public interface IPFSDownloadListener {
        void onDownloadSuccess(byte[] fileData);
        void onDownloadFailed(String error);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}