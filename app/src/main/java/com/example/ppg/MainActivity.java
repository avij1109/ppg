package com.example.ppg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    
    private Button startAnalysisButton;
    private Button historyButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        startAnalysisButton = findViewById(R.id.startAnalysisButton);
        historyButton = findViewById(R.id.historyButton);
        statusText = findViewById(R.id.statusText);
        
        // Set initial status
        statusText.setText("Ready to start PPG analysis");
        
        // Set up event listeners
        startAnalysisButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPPGAnalysis();
            }
        });
        
        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHistory();
            }
        });
    }
    
    private void startPPGAnalysis() {
        statusText.setText("Starting PPG analysis...");
        
        // Launch CameraActivity
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }
    
    private void openHistory() {
        // Launch HistoryActivity
        Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
        startActivity(intent);
    }
}