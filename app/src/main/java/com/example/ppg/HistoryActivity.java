package com.example.ppg;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;

import com.example.ppg.api.ApiService;
import com.example.ppg.models.Measurement;
import com.example.ppg.adapters.MeasurementAdapter;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends Activity {
    
    private static final String TAG = "HistoryActivity";
    
    private TextView titleText;
    private TextView statsText;
    private ListView measurementsList;
    
    private ApiService apiService;
    private String subjectId;
    private String subjectName;
    private List<Measurement> measurements = new ArrayList<>();
    private MeasurementAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Get subject info from intent
        subjectId = getIntent().getStringExtra("subject_id");
        subjectName = getIntent().getStringExtra("subject_name");
        
        if (subjectId == null) {
            Toast.makeText(this, "No subject selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI components
        titleText = findViewById(R.id.titleText);
        statsText = findViewById(R.id.statsText);
        measurementsList = findViewById(R.id.measurementsList);
        
        // Set title
        titleText.setText("History - " + subjectName);
        
        // Initialize API service
        apiService = new ApiService();
        
        // Set up list adapter
        adapter = new MeasurementAdapter(this, measurements);
        measurementsList.setAdapter(adapter);
        
        // Load measurement history
        loadHistory();
    }
    
    private void loadHistory() {
        statsText.setText("Loading measurement history...");
        
        apiService.getSubjectHistory(subjectId, new ApiService.HistoryCallback() {
            @Override
            public void onSuccess(List<Measurement> measurementList, ApiService.SubjectStats stats) {
                runOnUiThread(() -> {
                    measurements.clear();
                    measurements.addAll(measurementList);
                    adapter.notifyDataSetChanged();
                    
                    // Update stats display
                    if (stats != null && stats.total_measurements > 0) {
                        String statsDisplay = String.format(
                            "Total Measurements: %d\nAverage Heart Rate: %.1f BPM\nRange: %d - %d BPM",
                            stats.total_measurements,
                            stats.avg_heart_rate,
                            stats.min_heart_rate,
                            stats.max_heart_rate
                        );
                        statsText.setText(statsDisplay);
                    } else {
                        statsText.setText("No measurements found for this patient");
                    }
                    
                    Log.d(TAG, "Loaded " + measurementList.size() + " measurements for " + subjectId);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    statsText.setText("Error loading history: " + error);
                    Toast.makeText(HistoryActivity.this, "Failed to load history: " + error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to load history for " + subjectId + ": " + error);
                });
            }
        });
    }
}
