package com.example.visionstick;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.support.image.TensorImage;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VisionActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private TextView tvResult, tvAnalysis;
    private ImageButton btnBack;
    private ObjectDetector objectDetector;
    private TextToSpeech textToSpeech;
    private long lastSpeakTime = 0;

    // Background worker to prevent UI crashes
    private ExecutorService cameraExecutor;

    private static final int CAMERA_REQUEST_CODE = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);

        // 1. Link UI components
        viewFinder = findViewById(R.id.viewFinder);
        tvResult = findViewById(R.id.tvResult);
        tvAnalysis = findViewById(R.id.tvAnalysis);
        btnBack = findViewById(R.id.btnBack);

        // 2. Start Background Thread
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 3. Setup Voice
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        btnBack.setOnClickListener(v -> finish());

        // 4. Load the AI Model (Lower threshold for better detection)
        try {
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(1)
                    .setScoreThreshold(0.3f)
                    .build();
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this, "model.tflite", options);
            Log.d("Vision", "Model Loaded Successfully");
        } catch (IOException e) {
            Log.e("Vision", "Model loading failed", e);
            tvResult.setText("MODEL ERROR");
        }

        // 5. Check Permissions and Start
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Setup Preview (Video on screen)
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Setup AI Analysis (The Fix is here: Output RGBA directly)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy image) {
                        if (objectDetector != null) {
                            runObjectDetection(image);
                        } else {
                            image.close();
                        }
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("Vision", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void runObjectDetection(ImageProxy image) {
        // This runs in the background. We must NOT touch the View system here.
        try {
            // Create Bitmap directly from camera data (Safe)
            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth(),
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

            // Feed to AI
            TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
            List<Detection> results = objectDetector.detect(tensorImage);

            // Close image immediately so camera continues
            image.close();

            // Process Results
            if (!results.isEmpty()) {
                Detection topResult = results.get(0);
                String objectName = topResult.getCategories().get(0).getLabel();
                float score = topResult.getCategories().get(0).getScore();

                Log.d("Vision", "Detected: " + objectName);

                // Update UI (Must go back to Main Thread for this part)
                runOnUiThread(() -> {
                    tvResult.setText(objectName.toUpperCase());
                    tvAnalysis.setText("CONFIDENCE: " + Math.round(score * 100) + "%");
                });

                // Speak if 3 seconds have passed
                if (System.currentTimeMillis() - lastSpeakTime > 3000) {
                    textToSpeech.speak("I see " + objectName, TextToSpeech.QUEUE_FLUSH, null, null);
                    lastSpeakTime = System.currentTimeMillis();
                }
            } else {
                // Optional: Clear text if nothing seen
                // runOnUiThread(() -> tvResult.setText("SCANNING..."));
            }

        } catch (Exception e) {
            image.close(); // Safety close
            Log.e("Vision", "Analysis Error", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) textToSpeech.shutdown();
        cameraExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}