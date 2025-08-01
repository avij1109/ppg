package com.example.ppg.api;

import android.util.Log;

import com.example.ppg.models.Subject;
import com.example.ppg.models.Measurement;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {
    private static final String TAG = "ApiService";
    private static final String BASE_URL = "https://renderr-jk83.onrender.com/api";
    
    private final OkHttpClient client;
    private final Gson gson;

    public ApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    // Callback interfaces
    public interface SubjectsCallback {
        void onSuccess(List<Subject> subjects);
        void onError(String error);
    }

    public interface SubjectCreateCallback {
        void onSuccess(Subject subject);
        void onError(String error);
    }

    public interface HistoryCallback {
        void onSuccess(List<Measurement> measurements, SubjectStats stats);
        void onError(String error);
    }

    // Get all subjects
    public void getSubjects(SubjectsCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/subjects")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to get subjects", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "Subjects response: " + jsonResponse);
                        
                        Type responseType = new TypeToken<ApiResponse<Subject>>(){}.getType();
                        ApiResponse<Subject> apiResponse = gson.fromJson(jsonResponse, responseType);
                        
                        if (apiResponse.success && apiResponse.subjects != null) {
                            callback.onSuccess(apiResponse.subjects);
                        } else {
                            callback.onError("Failed to parse subjects response");
                        }
                    } else {
                        callback.onError("Server error: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing subjects response", e);
                    callback.onError("Error parsing response: " + e.getMessage());
                }
            }
        });
    }

    // Create a new subject
    public void createSubject(String name, Integer age, String gender, String notes, SubjectCreateCallback callback) {
        SubjectCreateRequest request = new SubjectCreateRequest(name, age, gender, notes);
        String json = gson.toJson(request);
        
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request httpRequest = new Request.Builder()
                .url(BASE_URL + "/subjects")
                .post(body)
                .build();

        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to create subject", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "Create subject response: " + jsonResponse);
                        
                        Type responseType = new TypeToken<ApiResponse<Subject>>(){}.getType();
                        ApiResponse<Subject> apiResponse = gson.fromJson(jsonResponse, responseType);
                        
                        if (apiResponse.success && apiResponse.subject != null) {
                            callback.onSuccess(apiResponse.subject);
                        } else {
                            callback.onError("Failed to create subject");
                        }
                    } else {
                        callback.onError("Server error: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing create subject response", e);
                    callback.onError("Error parsing response: " + e.getMessage());
                }
            }
        });
    }

    // Get subject history
    public void getSubjectHistory(String subjectId, HistoryCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/subjects/" + subjectId + "/history")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to get subject history", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, "History response: " + jsonResponse);
                        
                        HistoryResponse historyResponse = gson.fromJson(jsonResponse, HistoryResponse.class);
                        
                        if (historyResponse.success && historyResponse.measurements != null) {
                            callback.onSuccess(historyResponse.measurements, historyResponse.stats);
                        } else {
                            callback.onError("Failed to parse history response");
                        }
                    } else {
                        callback.onError("Server error: " + response.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing history response", e);
                    callback.onError("Error parsing response: " + e.getMessage());
                }
            }
        });
    }

    // Response models
    private static class ApiResponse<T> {
        boolean success;
        T subject;
        List<T> subjects;
    }

    private static class HistoryResponse {
        boolean success;
        String subject_id;
        List<Measurement> measurements;
        SubjectStats stats;
    }

    private static class SubjectCreateRequest {
        String subject_name;
        Integer age;
        String gender;
        String notes;

        SubjectCreateRequest(String name, Integer age, String gender, String notes) {
            this.subject_name = name;
            this.age = age;
            this.gender = gender;
            this.notes = notes;
        }
    }

    public static class SubjectStats {
        public double avg_systolic;
        public double avg_diastolic;
        public double avg_heart_rate;
        public int measurement_count;
        public int total_measurements;
        public double min_heart_rate;
        public double max_heart_rate;
    }
}
