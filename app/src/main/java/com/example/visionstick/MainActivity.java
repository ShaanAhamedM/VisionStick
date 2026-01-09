package com.example.visionstick;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Check Audio Permissions (Critical for Android 10+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        statusText = findViewById(R.id.statusText);

        // 2. Setup Text-to-Speech (The App's Voice)
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                speak("Vision Stick Ready. Tap anywhere and say a command.");
            }
        });

        // 3. Setup Speech Recognizer (The App's Ears)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                statusText.setText("LISTENING...");
                statusText.setTextColor(getResources().getColor(R.color.neon_blue));
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0).toLowerCase();
                    processCommand(command);
                }
            }

            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {
                statusText.setText("PROCESSING...");
            }
            @Override
            public void onError(int error) {
                speak("I didn't catch that. Tap and try again.");
                statusText.setText("ERROR");
            }
            @Override
            public void onEvent(int eventType, Bundle params) {}
            @Override
            public void onPartialResults(Bundle partialResults) {}
        });

        // 4. THE MAGIC: Make the WHOLE screen a button
        // We find the root layout (the background) and listen for clicks
        View rootView = findViewById(android.R.id.content);
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startListening();
            }
        });
    }

    private void startListening() {
        speak("Listening"); // Auditory cue
        // Small delay to let the app finish speaking "Listening" before recording
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) { e.printStackTrace(); }

        speechRecognizer.startListening(speechIntent);
    }

    private void processCommand(String command) {
        statusText.setText("CMD: " + command.toUpperCase());

        if (command.contains("vision") || command.contains("see") || command.contains("eyes")) {
            speak("Opening Vision Mode");
            // Intent to open VisionActivity (we will code this next)
            Intent intent = new Intent(MainActivity.this, VisionActivity.class);
            startActivity(intent);
        }
        else if (command.contains("navigate") || command.contains("map") || command.contains("go")) {
            speak("Navigation Mode. Where do you want to go?");
            // Logic for maps
        }
        else if (command.contains("help") || command.contains("sos") || command.contains("emergency")) {
            speak("Sending Emergency Alert");
            // Logic for SOS
        }
        else {
            speak("Command not recognized. Try saying Vision, Navigate, or Help.");
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