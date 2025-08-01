#!/usr/bin/env python3
"""
Real PPG Data BP Regression Model
Trained on actual PPG signals with corresponding BP values
Target: High accuracy BP prediction using real data
"""

import os
import pandas as pd
import numpy as np
import pywt
from scipy.signal import butter, filtfilt, find_peaks, welch, savgol_filter
from scipy.stats import skew, kurtosis
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.preprocessing import RobustScaler
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import xgboost as xgb
import joblib
import matplotlib.pyplot as plt
import seaborn as sns

print("🚀 Creating Real Data BP Regression Model")
print("=" * 50)

# ----- PREPROCESSING FUNCTIONS -----

def remove_spike_outliers(signal, threshold=3):
    """Remove spike outliers from signal"""
    z = np.abs((signal - np.mean(signal)) / np.std(signal))
    signal[z > threshold] = np.median(signal)
    return signal

def normalize_signal(signal):
    """Normalize signal to 0-1 range"""
    min_val = np.min(signal)
    max_val = np.max(signal)
    if max_val - min_val == 0:
        return signal
    return (signal - min_val) / (max_val - min_val)

def wavelet_denoise(signal, wavelet='db6', level=3):
    """Apply wavelet denoising"""
    if len(signal) < 2**level:
        return signal
    coeff = pywt.wavedec(signal, wavelet, level=level)
    sigma = np.median(np.abs(coeff[-level])) / 0.6745
    uthresh = sigma * np.sqrt(2 * np.log(len(signal)))
    coeff[1:] = [pywt.threshold(i, value=uthresh, mode='soft') for i in coeff[1:]]
    return pywt.waverec(coeff, wavelet)

def bandpass_filter(signal, low=0.5, high=8.0, fs=125, order=4):
    """Apply bandpass filter"""
    nyq = 0.5 * fs
    low /= nyq
    high /= nyq
    b, a = butter(order, [low, high], btype='band')
    return filtfilt(b, a, signal)

def segment_signal(signal, window_size=1000, overlap=500):
    """Segment signal into overlapping windows"""
    segments = []
    for start in range(0, len(signal) - window_size + 1, window_size - overlap):
        segments.append(signal[start:start+window_size])
    return segments

def remove_feature_outliers(df, feature_cols):
    """Remove feature-level outliers"""
    for col in feature_cols:
        Q1 = df[col].quantile(0.25)
        Q3 = df[col].quantile(0.75)
        IQR = Q3 - Q1
        lower = Q1 - 1.5 * IQR
        upper = Q3 + 1.5 * IQR
        df = df[(df[col] >= lower) & (df[col] <= upper)]
    return df

# ----- ENHANCED FEATURE EXTRACTION -----

def extract_enhanced_features(ppg_segment, fs=125):
    """Enhanced feature extraction for BP regression"""
    features = {}
    
    # Statistical features
    features['mean'] = np.mean(ppg_segment)
    features['std'] = np.std(ppg_segment)
    features['skewness'] = skew(ppg_segment)
    features['kurtosis'] = kurtosis(ppg_segment)
    features['variance'] = np.var(ppg_segment)
    features['rms'] = np.sqrt(np.mean(ppg_segment**2))
    features['mad'] = np.mean(np.abs(ppg_segment - np.mean(ppg_segment)))
    features['cv'] = np.std(ppg_segment) / (np.mean(ppg_segment) + 1e-8)
    
    # Peak analysis
    peaks, properties = find_peaks(ppg_segment, height=np.mean(ppg_segment), distance=fs*0.6)
    
    if len(peaks) >= 3:
        peak_intervals = np.diff(peaks) / fs
        peak_heights = properties['peak_heights']
        
        features['peak_count'] = len(peaks)
        features['peak_mean_height'] = np.mean(peak_heights)
        features['peak_std_height'] = np.std(peak_heights)
        features['peak_mean_interval'] = np.mean(peak_intervals)
        features['peak_std_interval'] = np.std(peak_intervals)
        features['peak_cv_interval'] = np.std(peak_intervals) / (np.mean(peak_intervals) + 1e-8)
        
        # HRV features
        rr_intervals = np.diff(peaks) / fs * 1000  # Convert to ms
        features['hrv_rmssd'] = np.sqrt(np.mean(np.diff(rr_intervals)**2))
        features['hrv_sdnn'] = np.std(rr_intervals)
        features['hrv_mean'] = np.mean(rr_intervals)
        features['hrv_cv'] = np.std(rr_intervals) / (np.mean(rr_intervals) + 1e-8)
        
        # Heart rate
        hr_values = 60 / peak_intervals
        features['mean_hr'] = np.mean(hr_values)
        features['hr_std'] = np.std(hr_values)
        
    else:
        # Default values when insufficient peaks
        for key in ['peak_count', 'peak_mean_height', 'peak_std_height', 'peak_mean_interval', 
                   'peak_std_interval', 'peak_cv_interval', 'hrv_rmssd', 'hrv_sdnn', 
                   'hrv_mean', 'hrv_cv', 'mean_hr', 'hr_std']:
            features[key] = 0
    
    # Frequency domain features
    freqs, psd = welch(ppg_segment, fs=fs, nperseg=min(len(ppg_segment)//4, 256))
    
    # Frequency bands
    lf_band = (freqs >= 0.04) & (freqs < 0.15)
    hf_band = (freqs >= 0.15) & (freqs < 0.4)
    
    lf_power = np.trapz(psd[lf_band], freqs[lf_band]) if np.any(lf_band) else 0
    hf_power = np.trapz(psd[hf_band], freqs[hf_band]) if np.any(hf_band) else 0
    
    features['freq_lf_power'] = lf_power
    features['freq_hf_power'] = hf_power
    features['freq_lf_hf_ratio'] = lf_power / (hf_power + 1e-8)
    features['freq_peak_frequency'] = freqs[np.argmax(psd)] if len(psd) > 0 else 0
    
    # Spectral entropy
    psd_norm = psd / (np.sum(psd) + 1e-8)
    features['freq_spectral_entropy'] = -np.sum(psd_norm * np.log(psd_norm + 1e-10))
    
    # Morphological features
    if len(ppg_segment) > 5:
        smoothed = savgol_filter(ppg_segment, window_length=5, polyorder=2)
    else:
        smoothed = ppg_segment
    
    first_derivative = np.gradient(smoothed)
    second_derivative = np.gradient(first_derivative)
    
    features['morph_signal_energy'] = np.sum(ppg_segment**2)
    features['morph_first_deriv_mean'] = np.mean(first_derivative)
    features['morph_first_deriv_std'] = np.std(first_derivative)
    features['morph_second_deriv_mean'] = np.mean(second_derivative)
    features['morph_second_deriv_std'] = np.std(second_derivative)
    features['morph_zero_crossings_1st'] = len(np.where(np.diff(np.signbit(first_derivative)))[0])
    features['morph_zero_crossings_2nd'] = len(np.where(np.diff(np.signbit(second_derivative)))[0])
    features['morph_signal_complexity'] = np.std(first_derivative) / (np.std(ppg_segment) + 1e-8)
    
    return features

# ----- PROCESS REAL DATASET -----

print("📊 Processing real dataset for BP regression...")

base_folder = r"/home/avij/AndroidStudioProjects/PPG/dataset"
raw_folder = os.path.join(base_folder, "raw_data")

csv_files = [f for f in os.listdir(raw_folder) if f.endswith(".csv")]
csv_files.sort()

regression_features = []

print(f"Found {len(csv_files)} patient files")

for file in csv_files:
    print(f"📊 Processing {file}...")
    df = pd.read_csv(os.path.join(raw_folder, file))
    df = df.dropna().drop_duplicates()
    
    ppg = df['PPG'].values
    sbp = df['SBP'].iloc[0]  # Use first value as patient's BP
    dbp = df['DBP'].iloc[0]
    
    # Apply preprocessing pipeline
    ppg = remove_spike_outliers(ppg.copy())
    ppg = normalize_signal(ppg)
    ppg = wavelet_denoise(ppg)
    ppg = bandpass_filter(ppg)
    
    # Segment signal
    segments = segment_signal(ppg, window_size=1000, overlap=500)
    
    for i, segment in enumerate(segments):
        features = extract_enhanced_features(segment)
        features['source_file'] = file
        features['segment_index'] = i
        features['sbp'] = sbp
        features['dbp'] = dbp
        regression_features.append(features)

# Create regression DataFrame
regression_df = pd.DataFrame(regression_features)

# Remove outliers
feature_cols = [col for col in regression_df.columns if col not in ['source_file', 'segment_index', 'sbp', 'dbp']]
regression_df = remove_feature_outliers(regression_df, feature_cols)

print("✅ Real data processing completed!")
print(f"Final Shape: {regression_df.shape}")
print(f"BP Range - SBP: {regression_df['sbp'].min():.1f}-{regression_df['sbp'].max():.1f}")
print(f"BP Range - DBP: {regression_df['dbp'].min():.1f}-{regression_df['dbp'].max():.1f}")

# ----- TRAIN REGRESSION MODELS -----

print("\n🎯 Training BP Regression Models...")

X = regression_df.drop(columns=['source_file', 'segment_index', 'sbp', 'dbp'])
y_systolic = regression_df['sbp']
y_diastolic = regression_df['dbp']

# Train-test split
X_train, X_test, y_sys_train, y_sys_test, y_dia_train, y_dia_test = train_test_split(
    X, y_systolic, y_diastolic, test_size=0.2, random_state=42
)

# Scale features
scaler = RobustScaler()
X_train_scaled = scaler.fit_transform(X_train)
X_test_scaled = scaler.transform(X_test)

# Train models
models = {
    'systolic': {
        'rf': RandomForestRegressor(n_estimators=200, max_depth=15, random_state=42),
        'gb': GradientBoostingRegressor(n_estimators=200, max_depth=8, random_state=42),
        'xgb': xgb.XGBRegressor(n_estimators=200, max_depth=8, random_state=42)
    },
    'diastolic': {
        'rf': RandomForestRegressor(n_estimators=200, max_depth=15, random_state=42),
        'gb': GradientBoostingRegressor(n_estimators=200, max_depth=8, random_state=42),
        'xgb': xgb.XGBRegressor(n_estimators=200, max_depth=8, random_state=42)
    }
}

results = {}

for bp_type in ['systolic', 'diastolic']:
    print(f"\n📈 Training {bp_type.upper()} BP models...")
    
    y_train = y_sys_train if bp_type == 'systolic' else y_dia_train
    y_test = y_sys_test if bp_type == 'systolic' else y_dia_test
    
    bp_results = {}
    
    for model_name, model in models[bp_type].items():
        print(f"  Training {model_name}...")
        model.fit(X_train_scaled, y_train)
        
        y_pred = model.predict(X_test_scaled)
        
        mae = mean_absolute_error(y_test, y_pred)
        rmse = np.sqrt(mean_squared_error(y_test, y_pred))
        r2 = r2_score(y_test, y_pred)
        
        bp_results[model_name] = {
            'model': model,
            'mae': mae,
            'rmse': rmse,
            'r2': r2,
            'accuracy': max(0, r2 * 100)
        }
        
        print(f"    MAE: {mae:.2f}, RMSE: {rmse:.2f}, R²: {r2:.3f}, Accuracy: {r2*100:.1f}%")
    
    results[bp_type] = bp_results

# ----- SELECT BEST MODELS -----

print("\n🏆 Best Models:")

best_models = {}
for bp_type in ['systolic', 'diastolic']:
    best_model_name = max(results[bp_type].keys(), 
                         key=lambda x: results[bp_type][x]['r2'])
    best_models[bp_type] = results[bp_type][best_model_name]['model']
    
    print(f"{bp_type.upper()}: {best_model_name} - "
          f"Accuracy: {results[bp_type][best_model_name]['accuracy']:.1f}%")

# ----- SAVE REAL DATA MODEL -----

model_data = {
    'models': best_models,
    'scaler': scaler,
    'feature_names': list(X.columns)
}

joblib.dump(model_data, 'real_ppg_bp_model.joblib')

print(f"\n✅ Real data BP model saved!")
print(f"📁 Model file: real_ppg_bp_model.joblib")
print(f"🎯 Features: {len(X.columns)}")
print(f"📊 Training samples: {len(X_train)}")
print(f"🧪 Test samples: {len(X_test)}")

# ----- PLOT RESULTS -----

fig, axes = plt.subplots(2, 2, figsize=(15, 10))

for i, bp_type in enumerate(['systolic', 'diastolic']):
    y_test = y_sys_test if bp_type == 'systolic' else y_dia_test
    y_pred = best_models[bp_type].predict(X_test_scaled)
    
    # Scatter plot
    axes[i, 0].scatter(y_test, y_pred, alpha=0.6)
    axes[i, 0].plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()], 'r--', lw=2)
    axes[i, 0].set_xlabel(f'Actual {bp_type.upper()} BP')
    axes[i, 0].set_ylabel(f'Predicted {bp_type.upper()} BP')
    axes[i, 0].set_title(f'{bp_type.upper()} BP Prediction')
    axes[i, 0].grid(True, alpha=0.3)
    
    # Residual plot
    residuals = y_test - y_pred
    axes[i, 1].scatter(y_pred, residuals, alpha=0.6)
    axes[i, 1].axhline(y=0, color='r', linestyle='--')
    axes[i, 1].set_xlabel(f'Predicted {bp_type.upper()} BP')
    axes[i, 1].set_ylabel('Residuals')
    axes[i, 1].set_title(f'{bp_type.upper()} BP Residuals')
    axes[i, 1].grid(True, alpha=0.3)

plt.tight_layout()
plt.show()

print("\n🎉 Real Data BP Regression Model Complete!")
print("=" * 50) 