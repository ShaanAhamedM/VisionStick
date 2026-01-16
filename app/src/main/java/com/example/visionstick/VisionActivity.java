package com.example.visionstick;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

// USE THESE EXACT IMPORTS
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

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

    // AI Engines
    private ObjectDetector objectDetector;
    private GenerativeModelFutures geminiModel;

    // State Management
    private TextToSpeech textToSpeech;
    private Bitmap currentFrameBitmap;
    private boolean isGeminiProcessing = false;
    private ExecutorService cameraExecutor;

    private static final int CAMERA_REQUEST_CODE = 10;
    private final String GEMINI_API_KEY = "AIzaSyAxBEGMtnPSAj5CvKabcyy2TQEZPiuJgJU"; //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);

        // 1. UI Links
        viewFinder = findViewById(R.id.viewFinder);
        tvResult = findViewById(R.id.tvResult);
        tvAnalysis = findViewById(R.id.tvAnalysis);
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());


        // USE THIS EXACT BLOCK
        // 1. We trim the key to remove any copy-paste spaces
        String SAFE_KEY = GEMINI_API_KEY.trim();

        // 2. Use the specific "001" stable version of Flash
        // This version is guaranteed to exist on the v1 endpoint.
        GenerativeModel gm = new GenerativeModel("gemini-2.0-flash", SAFE_KEY);

        geminiModel = GenerativeModelFutures.from(gm);

        // 3. Setup Voice
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        // 4. Load TFLite Model for Silent Scanning
        try {
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(1)
                    .setScoreThreshold(0.3f)
                    .build();
            objectDetector = ObjectDetector.createFromFileAndOptions(this, "model.tflite", options);
        } catch (IOException e) {
            Log.e("Vision", "TFLite Load Error", e);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 5. Check Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            startCamera();
        }

        // 6. BRIDGE LOGIC: Auto-trigger Gemini if opened via voice command
        if (getIntent().getBooleanExtra("TRIGGER_GEMINI", false)) {
            new Handler().postDelayed(() -> {
                onWhatAmILookingAtCommand();
            }, 2000); // 2-second delay to let the camera warm up
        }

        // 7. Manual Trigger: Tap the screen to describe
        viewFinder.setOnClickListener(v -> onWhatAmILookingAtCommand());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    runHybridDetection(image);
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("Vision", "Camera Setup Failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void runHybridDetection(ImageProxy image) {
        try {
            // Convert to Bitmap for both AI models
            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
            this.currentFrameBitmap = bitmap; // Keep latest frame for Gemini

            // TFLite: Continuous Silent Scan
            TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
            List<Detection> results = objectDetector.detect(tensorImage);
            image.close();

            if (!results.isEmpty()) {
                Detection topResult = results.get(0);
                String label = topResult.getCategories().get(0).getLabel();
                float score = topResult.getCategories().get(0).getScore();

                runOnUiThread(() -> {
                    tvResult.setText(label.toUpperCase());
                    tvAnalysis.setText("SCANNING: " + Math.round(score * 100) + "%");
                });
            }
        } catch (Exception e) {
            image.close();
        }
    }

    public void onWhatAmILookingAtCommand() {
        if (currentFrameBitmap == null || isGeminiProcessing) return;

        isGeminiProcessing = true;
        runOnUiThread(() -> Toast.makeText(this, "Gemini is thinking...", Toast.LENGTH_SHORT).show());
        textToSpeech.speak("Analyzing the entire scene...", TextToSpeech.QUEUE_FLUSH, null, null);

        // High Class Vision Prompt
        Content content = new Content.Builder()
                .addImage(currentFrameBitmap)
                .addText("You are a vision assistant for a blind person. Alert if you see a hazard. Describe exactly what is in front of me and list any obstacles. Keep it under 20 words.")
                .build();

        ListenableFuture<GenerateContentResponse> response = geminiModel.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                isGeminiProcessing = false;
                String description = result.getText(); // This is what Gemini "saw"

                // CRITICAL: We must run this on the UI thread for the voice to work
                runOnUiThread(() -> {
                    if (description != null && !description.isEmpty()) {
                        Log.d("GeminiTalk", "Gemini says: " + description);
                        // This is the line that actually talks!
                        textToSpeech.speak(description, TextToSpeech.QUEUE_FLUSH, null, "GeminiID");
                    } else {
                        textToSpeech.speak("I saw it, but I can't describe it.", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                isGeminiProcessing = false;
                // This will print the EXACT error (like "Invalid API Key" or "Quota Exceeded")
                String errorMessage = "Cloud Error: " + t.getMessage();
                Log.e("GeminiError", errorMessage);

                runOnUiThread(() -> {
                    Toast.makeText(VisionActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    textToSpeech.speak("I'm having trouble connecting to my brain. Check your internet or API key.",
                            TextToSpeech.QUEUE_FLUSH, null, null);
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (textToSpeech != null) textToSpeech.shutdown();
    }
}