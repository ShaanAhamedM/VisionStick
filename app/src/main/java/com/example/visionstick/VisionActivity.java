package com.example.visionstick;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

public class VisionActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private TextView tvResult;
    private ImageButton btnBack;

    // Permission Code
    private static final int CAMERA_REQUEST_CODE = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);

        // 1. Link UI
        viewFinder = findViewById(R.id.viewFinder);
        tvResult = findViewById(R.id.tvResult);
        btnBack = findViewById(R.id.btnBack);

        // 2. Back Button Logic
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Go back to Home
            }
        });

        // 3. Request Camera Permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        // This is the magic that starts the stream
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview Use Case (To show the video on screen)
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Select Back Camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                try {
                    // Unbind any previous use cases before rebinding
                    cameraProvider.unbindAll();

                    // Bind the camera to this screen
                    cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview);

                    tvResult.setText("SCANNING...");

                } catch (Exception exc) {
                    Log.e("VisionActivity", "Use case binding failed", exc);
                    Toast.makeText(this, "Camera Failed", Toast.LENGTH_SHORT).show();
                }

            } catch (ExecutionException | InterruptedException e) {
                Log.e("VisionActivity", "Camera Provider failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Handle Permission Result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera Permission Required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}