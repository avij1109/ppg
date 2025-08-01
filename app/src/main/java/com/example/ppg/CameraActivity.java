package com.example.ppg;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity implements PPGWebSocketClient.PPGResultListener {

    private static final String TAG = "CameraActivity";
    
    private PreviewView previewView;
    private Button stopButton;
    private TextView statusText;
    private TextView greenSignalText;
    private TextView heartRateText;
    private TextView confidenceText;
    private TextView timerText;
    private TextView bpCategoryText;
    private TextView bpConfidenceText;
    private LineChart ppgChart;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private ProgressBar progressBar;
    
    // PPG Graph data
    private ArrayList<Entry> ppgEntries = new ArrayList<>();
    private LineDataSet ppgDataSet;
    private LineData ppgLineData;
    private int sampleIndex = 0;  // For X-axis progression
    private static final int MAX_VISIBLE_ENTRIES = 150; // Show ~5 seconds at 30fps
    
    // WebSocket client for advanced PPG processing
    private PPGWebSocketClient webSocketClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // PPG processing variables 
    private int frameCount = 0;
    private long startTime = 0;
    

    
    // PPG results from server - simplified for green signal + HR only
    private int currentHeartRate = 0;
    private double currentGreenSignal = 0.0;
    private String signalQuality = "Connecting...";
    private int localTimerCount = 0;  // Local countdown timer (separate from server)
    private boolean analysisComplete = false;
    private boolean serverConnected = false;
    private boolean timerRunning = false;
    private Timer countdownTimer;
    
    // BP analysis results
    private String bpCategory = "Analyzing...";
    private int bpConfidence = 0;
    private boolean bpAnalysisComplete = false;
    private float systolicBP = 0.0f;
    private float diastolicBP = 0.0f;
    private String bpRiskLevel = "Unknown";
    private String bpRecommendation = "Analyzing...";
    
    // Permission request launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                    // Connect to WebSocket server
                    webSocketClient.connect();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        stopButton = findViewById(R.id.stopButton);
        statusText = findViewById(R.id.statusText);
        greenSignalText = findViewById(R.id.greenSignalText);
        heartRateText = findViewById(R.id.heartRateText);
        confidenceText = findViewById(R.id.confidenceText);
        timerText = findViewById(R.id.timerText);
        bpCategoryText = findViewById(R.id.bpCategoryText);
        bpConfidenceText = findViewById(R.id.bpConfidenceText);
        ppgChart = findViewById(R.id.ppgChart);
        progressBar = findViewById(R.id.progressBar);

        // Initialize PPG Chart
        setupPPGChart();

        // Set up the stop button
        stopButton.setOnClickListener(v -> {
            if (webSocketClient != null) {
                webSocketClient.disconnect();
            }
            finish(); // Close this activity and return to MainActivity
        });

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Initialize WebSocket client
        webSocketClient = new PPGWebSocketClient(this);
        
        // DON'T initialize PPG processing here - wait for first frame
        // startTime = System.currentTimeMillis();

        // Check camera permission and start camera
        if (allPermissionsGranted()) {
            startCamera();
            // Connect to WebSocket server
            webSocketClient.connect();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Image analysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new PPGFrameAnalyzer());

                // Select back camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                
                // Enable flash
                enableFlash();

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void enableFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(true);
            Toast.makeText(this, "Flash enabled", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
        }
    }



    private void showPPGResult() {
        // Stop the analysis by setting a flag
        analysisComplete = true;
        
        // Turn off the flash/torch
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            camera.getCameraControl().enableTorch(false);
        }
        
        // Show BP results popup if we have BP data
        if (bpAnalysisComplete && systolicBP > 0 && diastolicBP > 0) {
            showBPResultsPopup();
        } else {
            // Fallback to simple result if no BP data
            String resultMessage = String.format(
                "✅ PPG Analysis Complete!\n\nHeart Rate: %d BPM\nSignal Quality: %s\nFrames Processed: %d\nDuration: 40 seconds\n\nTap 'Stop' to return to main menu",
                currentHeartRate, signalQuality, frameCount
            );
            
            mainHandler.post(() -> {
                statusText.setText(resultMessage);
                Toast.makeText(this, "Analysis complete! HR: " + currentHeartRate + " BPM", Toast.LENGTH_LONG).show();
            });
        }
        
        Log.d(TAG, "PPG Analysis completed - HR: " + currentHeartRate + " BPM, BP: " + systolicBP + "/" + diastolicBP + " mmHg");
    }
    
    private void showBPResultsPopup() {
        mainHandler.post(() -> {
            try {
                // Inflate custom dialog layout
                LayoutInflater inflater = getLayoutInflater();
                View dialogView = inflater.inflate(R.layout.dialog_bp_results, null);
                
                // Find views in dialog
                TextView systolicText = dialogView.findViewById(R.id.systolicValue);
                TextView diastolicText = dialogView.findViewById(R.id.diastolicValue);
                TextView categoryText = dialogView.findViewById(R.id.bpCategoryText);
                TextView confidenceText = dialogView.findViewById(R.id.confidenceText);
                TextView heartRateText = dialogView.findViewById(R.id.heartRateValue);
                TextView recommendationText = dialogView.findViewById(R.id.recommendationText);
                TextView riskLevelText = dialogView.findViewById(R.id.riskLevelText);
                
                // Set BP values
                systolicText.setText(String.format("%.0f", systolicBP));
                diastolicText.setText(String.format("%.0f", diastolicBP));
                heartRateText.setText(String.format("%d BPM", currentHeartRate));
                
                // Set category with color coding
                categoryText.setText(bpCategory);
                int categoryColor = getBPCategoryColor(bpCategory);
                categoryText.setTextColor(categoryColor);
                
                // Set other details
                confidenceText.setText(String.format("Confidence: %d%%", bpConfidence));
                recommendationText.setText(bpRecommendation);
                riskLevelText.setText(String.format("Risk Level: %s", bpRiskLevel));
                
                // Color code risk level
                int riskColor = getRiskLevelColor(bpRiskLevel);
                riskLevelText.setTextColor(riskColor);
                
                // Create and show dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setView(dialogView)
                       .setTitle("Blood Pressure Analysis Complete")
                       .setPositiveButton("Save Results", new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int which) {
                               // TODO: Save results to local storage or database
                               Toast.makeText(CameraActivity.this, "Results saved!", Toast.LENGTH_SHORT).show();
                               dialog.dismiss();
                           }
                       })
                       .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int which) {
                               dialog.dismiss();
                           }
                       })
                       .setNeutralButton("Retake", new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int which) {
                               // Reset for new measurement
                               resetForNewMeasurement();
                               dialog.dismiss();
                           }
                       });
                
                AlertDialog dialog = builder.create();
                dialog.show();
                
                // Update status text
                statusText.setText("Analysis Complete! Check your results above.");
                
            } catch (Exception e) {
                Log.e(TAG, "Error showing BP results popup: " + e.getMessage());
                // Fallback to simple toast
                Toast.makeText(this, String.format("BP Analysis Complete!\nBP: %.0f/%.0f mmHg\nHR: %d BPM\nCategory: %s", 
                    systolicBP, diastolicBP, currentHeartRate, bpCategory), Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private int getBPCategoryColor(String category) {
        switch (category.toLowerCase()) {
            case "normal":
                return Color.parseColor("#00FF88"); // Green
            case "elevated":
                return Color.parseColor("#FFA500"); // Orange
            case "high blood pressure stage 1":
                return Color.parseColor("#FF8C00"); // Dark Orange
            case "high blood pressure stage 2":
                return Color.parseColor("#FF4444"); // Red
            case "hypertensive crisis":
                return Color.parseColor("#8B0000"); // Dark Red
            default:
                return Color.parseColor("#CCCCCC"); // Gray
        }
    }
    
    private int getRiskLevelColor(String riskLevel) {
        switch (riskLevel.toLowerCase()) {
            case "low":
                return Color.parseColor("#00FF88"); // Green
            case "moderate":
                return Color.parseColor("#FFA500"); // Orange
            case "high":
                return Color.parseColor("#FF4444"); // Red
            case "very high":
                return Color.parseColor("#8B0000"); // Dark Red
            case "critical":
                return Color.parseColor("#800080"); // Purple
            default:
                return Color.parseColor("#CCCCCC"); // Gray
        }
    }
    
    private void resetForNewMeasurement() {
        // Reset all variables for new measurement
        analysisComplete = false;
        bpAnalysisComplete = false;
        frameCount = 0;
        startTime = 0;
        currentHeartRate = 0;
        systolicBP = 0.0f;
        diastolicBP = 0.0f;
        bpCategory = "Analyzing...";
        bpConfidence = 0;
        signalQuality = "Preparing...";
        
        // Reset UI
        heartRateText.setText("--");
        confidenceText.setText("Quality: --");
        bpCategoryText.setText("Analyzing...");
        bpConfidenceText.setText("--");
        statusText.setText("Place finger on camera lens with flash");
        timerText.setText("00:40");
        progressBar.setProgress(0);
        
        // Clear chart data
        ppgEntries.clear();
        sampleIndex = 0;
        updatePPGChart(0);
        
        // Send reset signal to server
        if (webSocketClient != null) {
            webSocketClient.sendResetSignal();
        }
        
        // Restart timer if needed
        timerRunning = false;
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        
        Log.d(TAG, "Reset for new measurement");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
        cameraExecutor.shutdown();
    }

    // Inner class for analyzing camera frames and sending to WebSocket server
    private class PPGFrameAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            try {
                // Stop processing if analysis is complete
                if (analysisComplete) {
                    image.close();
                    return;
                }
                
                // Initialize start time on first frame
                if (startTime == 0) {
                    startTime = System.currentTimeMillis();
                    Log.d(TAG, "PPG analysis started via WebSocket");
                }
                
                // Calculate elapsed time
                long currentTime = System.currentTimeMillis();
                int elapsedSeconds = (int) ((currentTime - startTime) / 1000);
                
                // Check if 40 seconds have elapsed
                if (elapsedSeconds >= 40) {
                    if (!analysisComplete) {
                        analysisComplete = true;
                        CameraActivity.this.runOnUiThread(() -> showPPGResult());
                    }
                    image.close();
                    return;
                }
                
                frameCount++;
                
                // Send frame to WebSocket server if connected
                if (webSocketClient != null && webSocketClient.isConnected()) {
                    // Send every 2nd frame to reduce bandwidth (15 FPS instead of 30)
                    if (frameCount % 2 == 0) {
                        webSocketClient.sendFrame(image);
                    }
                    
                    // Update timer every 30 frames (approximately every second)
                    if (frameCount % 30 == 0) {
                        CameraActivity.this.runOnUiThread(() -> {
                            String timerMessage = String.format("Progress: %d/40s | Analyzing...", elapsedSeconds);
                            statusText.setText(timerMessage);
                        });
                    }
                } else {
                    // Update UI to show connection status
                    if (frameCount % 30 == 0) { // Update every second
                        CameraActivity.this.runOnUiThread(() -> {
                            statusText.setText("Connecting to PPG server...");
                        });
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in frame analysis: " + e.getMessage());
            } finally {
                image.close();
            }
        }
    }
    
    // PPGWebSocketClient.PPGResultListener implementation
    @Override
    public void onResult(PPGWebSocketClient.PPGResult result) {
        mainHandler.post(() -> {
            try {
                // Update green signal value for real-time display AND chart
                if (result.rgb_values != null) {
                    currentGreenSignal = result.rgb_values.green;
                    greenSignalText.setText(String.format("Signal: %.1f", currentGreenSignal));
                    
                    // Update PPG Chart with green signal value
                    updatePPGChart(currentGreenSignal);
                }
                
                // Update heart rate and confidence if available
                if (result.heart_rate != null) {
                    currentHeartRate = result.heart_rate.heart_rate;
                    signalQuality = result.heart_rate.signal_quality != null ? 
                                  result.heart_rate.signal_quality : "Processing...";
                    
                    // Update heart rate display (just the number, larger)
                    heartRateText.setText(String.format("%d", currentHeartRate));
                    
                    // Update confidence/quality display
                    int confidence = result.heart_rate.confidence;
                    confidenceText.setText(String.format("Quality: %s (%d%%)", signalQuality, confidence));
                }
                
                // Handle enhanced BP analysis result with systolic/diastolic values
                if (result.bp_analysis_result != null && !bpAnalysisComplete) {
                    PPGWebSocketClient.PPGResult.BPAnalysisResult bpResult = result.bp_analysis_result;
                    
                    if (bpResult.bp_analysis != null) {
                        // Extract enhanced BP data
                        systolicBP = bpResult.bp_analysis.systolic_bp;
                        diastolicBP = bpResult.bp_analysis.diastolic_bp;
                        bpCategory = bpResult.bp_analysis.bp_category;
                        bpConfidence = bpResult.bp_analysis.confidence;
                        bpAnalysisComplete = true;
                        
                        // Extract interpretation data
                        if (bpResult.interpretation != null) {
                            bpRecommendation = bpResult.interpretation.recommendation != null ? 
                                             bpResult.interpretation.recommendation : "Consult healthcare provider";
                            bpRiskLevel = bpResult.interpretation.risk_level != null ? 
                                        bpResult.interpretation.risk_level : "Unknown";
                        }
                        
                        // Update BP display with enhanced information
                        String displayText = String.format("BP: %.0f/%.0f mmHg", systolicBP, diastolicBP);
                        int textColor = getBPCategoryColor(bpCategory);
                        
                        bpCategoryText.setText(displayText);
                        bpCategoryText.setTextColor(textColor);
                        bpConfidenceText.setText(String.format("Confidence: %d%% | %s", bpConfidence, bpCategory));
                        
                        Log.d(TAG, String.format("Enhanced BP Analysis Result: %s - %.0f/%.0f mmHg (%d%%)", 
                            bpCategory, systolicBP, diastolicBP, bpConfidence));
                        
                        // Trigger result display
                        if (!analysisComplete) {
                            analysisComplete = true;
                            showPPGResult();
                        }
                    }
                }
                
                // Don't use server elapsed time for timer - let local timer handle it
                // Just log server progress for debugging
                double elapsed = result.elapsed_time;
                Log.d(TAG, String.format("Server processing time: %.1fs", elapsed));
                
                // Only show server progress if we haven't reached 40 seconds yet
                if (!analysisComplete) {
                    // Status is handled by local timer, just update if we have heart rate
                    if (currentHeartRate > 0) {
                        // Don't override timer status, just log
                        Log.d(TAG, "Server result: HR " + currentHeartRate + " BPM");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing server result: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void onError(String error) {
        mainHandler.post(() -> {
            Log.e(TAG, "WebSocket error: " + error);
            signalQuality = "Server Error";
            statusText.setText("Server Error: " + error);
            Toast.makeText(this, "PPG Server Error: " + error, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onConnectionChanged(boolean connected) {
        mainHandler.post(() -> {
            serverConnected = connected;
            if (connected) {
                signalQuality = "Server Connected";
                statusText.setText("Connected to PPG Server - Place finger on camera lens");
                Log.d(TAG, "Connected to PPG WebSocket server");
                
                // Start timer automatically
                if (!timerRunning && !analysisComplete) {
                    startCountdownTimer();
                }
            } else {
                signalQuality = "Server Disconnected";
                statusText.setText("Disconnected from PPG Server");
                Log.d(TAG, "Disconnected from PPG WebSocket server");
            }
        });
    }
    
    private void setupPPGChart() {
        // Configure the chart
        ppgChart.getDescription().setEnabled(false);
        ppgChart.setTouchEnabled(false);
        ppgChart.setDragEnabled(false);
        ppgChart.setScaleEnabled(false);
        ppgChart.setDrawGridBackground(false);
        ppgChart.setPinchZoom(false);
        ppgChart.setBackgroundColor(Color.BLACK);
        
        // Configure X axis
        XAxis xAxis = ppgChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.GRAY);
        xAxis.setLabelCount(5);
        
        // Configure left Y axis
        YAxis leftAxis = ppgChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.GRAY);
        leftAxis.setLabelCount(6);
        
        // Disable right Y axis
        ppgChart.getAxisRight().setEnabled(false);
        
        // Configure legend
        ppgChart.getLegend().setTextColor(Color.WHITE);
        ppgChart.getLegend().setTextSize(12f);
        
        // Initialize data set
        ppgDataSet = new LineDataSet(ppgEntries, "PPG Signal");
        ppgDataSet.setColor(Color.GREEN);
        ppgDataSet.setLineWidth(2f);
        ppgDataSet.setDrawCircles(false);
        ppgDataSet.setDrawValues(false);
        ppgDataSet.setMode(LineDataSet.Mode.LINEAR);
        
        ppgLineData = new LineData(ppgDataSet);
        ppgChart.setData(ppgLineData);
        ppgChart.invalidate();
    }
    
    private void updatePPGChart(double greenValue) {
        // Add new data point
        ppgEntries.add(new Entry(sampleIndex++, (float) greenValue));
        
        // Remove old points to maintain window size
        if (ppgEntries.size() > MAX_VISIBLE_ENTRIES) {
            ppgEntries.remove(0);
            // Adjust x values
            for (int i = 0; i < ppgEntries.size(); i++) {
                ppgEntries.get(i).setX(i);
            }
            sampleIndex = ppgEntries.size();
        }
        
        // Update dataset
        ppgDataSet.notifyDataSetChanged();
        ppgLineData.notifyDataChanged();
        ppgChart.notifyDataSetChanged();
        
        // Auto-scale Y axis to fit data
        ppgChart.fitScreen();
        ppgChart.invalidate();
    }
    
    private void startCountdownTimer() {
        timerRunning = true;
        localTimerCount = 0;  // Internal counter starts from 0
        
        countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    // Count time normally
                    if (localTimerCount < 40 && !analysisComplete) {
                        localTimerCount++;
                        
                        // Calculate REMAINING time (countdown from 40 to 0)
                        int remainingTime = 40 - localTimerCount;
                        
                        // Update timer display - countdown format
                        timerText.setText(String.format("%02d:%02d", 
                            remainingTime / 60, remainingTime % 60));
                        
                        // Update status with remaining time
                        if (remainingTime > 0) {
                            statusText.setText(String.format("Analyzing BP... %d seconds remaining", remainingTime));
                        }
                        
                        // Progress bar (fills up as time counts down) 
                        if (progressBar != null) {
                            progressBar.setProgress((localTimerCount * 100) / 40);
                        }
                        
                        Log.d(TAG, String.format("Countdown Timer: %d seconds remaining (%d/40 elapsed)", 
                            remainingTime, localTimerCount));
                        
                    } else if (localTimerCount >= 40) {
                        // Timer completed - show 00:00
                        analysisComplete = true;
                        timerText.setText("00:00");
                        statusText.setText("Analysis Complete! Processing results...");
                        
                        // Stop the timer
                        if (countdownTimer != null) {
                            countdownTimer.cancel();
                            countdownTimer = null;
                            timerRunning = false;
                        }
                        
                        Log.d(TAG, "Countdown Timer completed - Analysis finished");
                    }
                });
            }
        }, 1000, 1000); // Update every second
    }
}
