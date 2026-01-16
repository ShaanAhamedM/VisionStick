package com.example.visionstick;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false; // Flag to prevent double taps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Critical Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, 1);
        }

        // 2. UI Hookup
        statusText = findViewById(R.id.statusText);
        CardView cardVision = findViewById(R.id.cardVision);
        CardView cardNav = findViewById(R.id.cardNav);
        CardView cardSOS = findViewById(R.id.cardSOS);

        // Update UI to show instructions
        statusText.setText("TAP SCREEN TO SPEAK");

        // 3. Setup Voice Feedback
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                // We removed the automatic restart logic here
            }
        });

        // 4. Setup Ear (Single Shot Recognition)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                statusText.setText("LISTENING...");
                isListening = true;
            }
            @Override public void onEndOfSpeech() {
                statusText.setText("PROCESSING...");
                isListening = false;
            }
            @Override public void onError(int error) {
                statusText.setText("TAP SCREEN TO SPEAK");
                isListening = false;
                // Do NOT restart listening automatically
            }
            @Override public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    processCommand(matches.get(0).toLowerCase());
                } else {
                    statusText.setText("TAP SCREEN TO SPEAK");
                }
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        // 5. Button Logic
        cardVision.setOnClickListener(v -> openVisionMode());
        cardNav.setOnClickListener(v -> speak("Navigation coming soon."));
        cardSOS.setOnClickListener(v -> speak("Emergency alert sent."));

        // 6. TAP TO SPEAK (This simulates your future Physical Stick Button)
        findViewById(R.id.rootLayout).setOnClickListener(v -> {
            if (!isListening) {
                startListening();
            }
        });
    }

    private void startListening() {
        if (speechRecognizer != null) {
            // Stop any TTS to clear the air for listening
            if (textToSpeech != null && textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            speechRecognizer.startListening(speechIntent);
        }
    }

    private void openVisionMode() {
        speak("Opening Vision.");
        startActivity(new Intent(MainActivity.this, VisionActivity.class));
    }

    private void processCommand(String command) {
        statusText.setText("CMD: " + command.toUpperCase());

        // TRIGGER: "What am I looking at"
        if (command.contains("what") || command.contains("describe") || command.contains("scan") || command.contains("looking")) {
            speak("Scanning...");
            Intent intent = new Intent(this, VisionActivity.class);
            intent.putExtra("TRIGGER_GEMINI", true);
            startActivity(intent);
        }
        else if (command.contains("vision")) {
            openVisionMode();
        }
        else if (command.contains("help") || command.contains("sos")) {
            speak("Alerting emergency services.");
        }
        else {
            speak("Unknown command.");
            statusText.setText("TAP SCREEN TO SPEAK");
        }
    }

    private void speak(String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
    }
}