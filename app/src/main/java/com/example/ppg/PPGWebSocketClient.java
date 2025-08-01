package com.example.ppg;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Base64;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class PPGWebSocketClient extends WebSocketListener {
    private static final String TAG = "PPGWebSocketClient";
    private static final String SERVER_URL = "wss://renderr-jk83.onrender.com/ws"; // Updated Render server URL
    
    private WebSocket webSocket;
    private PPGResultListener listener;
    private Gson gson = new Gson();
    private boolean isConnected = false;
    private int frameCount = 0;
    
    public interface PPGResultListener {
        void onResult(PPGResult result);
        void onError(String error);
        void onConnectionChanged(boolean connected);
    }
    
    public static class PPGResult {
        public String status;
        public int frame_count;
        public double elapsed_time;
        public RGBValues rgb_values;
        public HeartRate heart_rate;
        public Respiration respiration;
        public SpO2 spo2;
        public String error;
        public double green_signal_value;  // Current green value
        public double[] green_signal_history;  // Array of recent green values
        public BPAnalysisResult bp_analysis_result;  // BP analysis result
        
        public static class RGBValues {
            public double red;
            public double green;
            public double blue;
            public int width;
            public int height;
        }
        
        public static class HeartRate {
            public int heart_rate;
            public int confidence;
            public String method;
            public String signal_quality;
        }
        
        public static class Respiration {
            public int respiration_rate;
            public int confidence;
        }
        
        public static class SpO2 {
            public int spo2;
            public int confidence;
            public double ratio;
        }
        
        public static class BPAnalysisResult {
            public BPAnalysis bp_analysis;
            public Interpretation interpretation;
            public double collection_duration;
            public int samples_collected;
            public String model_version;
            public String status;
            
            public static class BPAnalysis {
                public float systolic_bp;      // Added systolic BP value
                public float diastolic_bp;     // Added diastolic BP value
                public String bp_category;
                public int confidence;
                public String quality;
            }
            
            public static class Interpretation {
                public String category;
                public String description;
                public String recommendation;
                public String risk_level;      // Added risk level
                public String[] details;
            }
        }
    }
    
    public PPGWebSocketClient(PPGResultListener listener) {
        this.listener = listener;
    }
    
    public void connect() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
            
            Request request = new Request.Builder()
                    .url(SERVER_URL)
                    .build();
            
            webSocket = client.newWebSocket(request, this);
            Log.d(TAG, "Attempting to connect to WebSocket server...");
            
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket: " + e.getMessage());
            if (listener != null) {
                listener.onError("Connection failed: " + e.getMessage());
            }
        }
    }
    
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Disconnecting");
            webSocket = null;
        }
        isConnected = false;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public void sendFrame(ImageProxy imageProxy) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, skipping frame");
            return;
        }
        
        try {
            // Convert ImageProxy to Base64 string
            String frameData = imageProxyToBase64(imageProxy);
            if (frameData == null) {
                Log.e(TAG, "Failed to convert image to Base64");
                return;
            }
            
            // Create message
            JsonObject message = new JsonObject();
            message.addProperty("type", "frame");
            message.addProperty("frame", frameData);
            message.addProperty("timestamp", System.currentTimeMillis() / 1000.0);
            message.addProperty("frame_count", ++frameCount);
            
            // Send message
            String jsonMessage = gson.toJson(message);
            webSocket.send(jsonMessage);
            
            Log.d(TAG, "Sent frame " + frameCount + " to server");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending frame: " + e.getMessage());
            if (listener != null) {
                listener.onError("Failed to send frame: " + e.getMessage());
            }
        }
    }
    
    public void reset() {
        if (!isConnected || webSocket == null) {
            return;
        }
        
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", "reset");
            message.addProperty("timestamp", System.currentTimeMillis() / 1000.0);
            
            String jsonMessage = gson.toJson(message);
            webSocket.send(jsonMessage);
            frameCount = 0;
            
            Log.d(TAG, "Sent reset command to server");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending reset: " + e.getMessage());
        }
    }

    public void sendResetSignal() {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, cannot send reset signal");
            return;
        }
        
        try {
            JsonObject resetMessage = new JsonObject();
            resetMessage.addProperty("type", "reset");
            
            String jsonMessage = gson.toJson(resetMessage);
            webSocket.send(jsonMessage);
            Log.d(TAG, "Sent reset signal to server");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending reset signal: " + e.getMessage());
        }
    }
    
    private String imageProxyToBase64(ImageProxy imageProxy) {
        try {
            // Get the image
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ImageProxy.PlaneProxy yPlane = planes[0];
            ImageProxy.PlaneProxy uPlane = planes[1];
            ImageProxy.PlaneProxy vPlane = planes[2];
            
            ByteBuffer yBuffer = yPlane.getBuffer();
            ByteBuffer uBuffer = uPlane.getBuffer();
            ByteBuffer vBuffer = vPlane.getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            
            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);
            
            // Copy U and V planes (interleaved for NV21)
            byte[] uvPixels = new byte[uSize + vSize];
            uBuffer.get(uvPixels, 0, uSize);
            vBuffer.get(uvPixels, uSize, vSize);
            
            // Interleave U and V for NV21 format
            int uvPixelStride = uPlane.getPixelStride();
            if (uvPixelStride == 1) {
                System.arraycopy(uvPixels, 0, nv21, ySize, uSize + vSize);
            } else {
                // Interleave manually if pixel stride > 1
                int uvPos = ySize;
                for (int i = 0; i < uSize; i += uvPixelStride) {
                    nv21[uvPos++] = uvPixels[i];
                    nv21[uvPos++] = uvPixels[i + uSize];
                }
            }
            
            // Convert YUV to JPEG
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, 
                                           imageProxy.getWidth(), imageProxy.getHeight(), null);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 
                                  80, outputStream);
            
            byte[] jpegData = outputStream.toByteArray();
            
            // Convert to Base64
            return Base64.encodeToString(jpegData, Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Base64: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "WebSocket connected successfully");
        isConnected = true;
        if (listener != null) {
            listener.onConnectionChanged(true);
        }
    }
    
    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            Log.d(TAG, "Received message from server: " + text.substring(0, Math.min(100, text.length())));
            
            JsonObject response = gson.fromJson(text, JsonObject.class);
            String type = response.get("type").getAsString();
            
            if ("result".equals(type)) {
                JsonObject data = response.getAsJsonObject("data");
                PPGResult result = gson.fromJson(data, PPGResult.class);
                
                if (listener != null) {
                    listener.onResult(result);
                }
            } else if ("error".equals(type)) {
                String error = response.get("error").getAsString();
                Log.e(TAG, "Server error: " + error);
                if (listener != null) {
                    listener.onError("Server error: " + error);
                }
            } else if ("reset_ack".equals(type)) {
                Log.d(TAG, "Reset acknowledged by server");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing server response: " + e.getMessage());
            if (listener != null) {
                listener.onError("Failed to parse server response: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket connection failed: " + t.getMessage());
        isConnected = false;
        if (listener != null) {
            listener.onConnectionChanged(false);
            listener.onError("Connection failed: " + t.getMessage());
        }
    }
    
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closing: " + reason);
        isConnected = false;
        if (listener != null) {
            listener.onConnectionChanged(false);
        }
    }
    
    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closed: " + reason);
        isConnected = false;
        if (listener != null) {
            listener.onConnectionChanged(false);
        }
    }
}
