import os
import sys
import json
import re
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error

def parse_floor_plan_dimensions(floor_plan_str):
    """
    Parses building width and length from the floor plan string.
    Example: 'Default Grid (50.0mx30.0m)' -> 50.0, 30.0
    """
    try:
        match = re.search(r'\(?([\d\.]+)mx([\d\.]+)m\)?', floor_plan_str)
        if match:
            width = float(match.group(1))
            length = float(match.group(2))
            return width, length
    except Exception:
        pass
    return 50.0, 30.0  # Fallback defaults

def load_and_preprocess_dataset(json_path):
    print(f"Loading dataset from: {json_path}")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    if not data:
        print("Error: The dataset is empty.")
        sys.exit(1)
        
    print(f"Found {len(data)} Wi-Fi fingerprint records.")
    
    # 1. Identify all unique BSSID MAC addresses in the dataset
    all_bssids = set()
    for record in data:
        for m in record.get("measurements", []):
            bssid = m.get("bssid")
            if bssid:
                all_bssids.add(bssid)
                
    all_bssids = sorted(list(all_bssids))
    print(f"Detected {len(all_bssids)} unique Access Points (BSSIDs).")
    
    # 2. Build feature matrix X (RSSI signals) and target vector Y (Physical coordinates)
    # Default value for undetected APs is -100 dBm
    X_list = []
    Y_list = []
    
    for i, record in enumerate(data):
        coord_x = record.get("coord_x")
        coord_y = record.get("coord_y")
        
        # Skip records without coordinates
        if coord_x is None or coord_y is None:
            continue
            
        floor_plan = record.get("floor_plan", "Default Grid (50.0mx30.0m)")
        width, length = parse_floor_plan_dimensions(floor_plan)
        
        # Target labels in physical meters
        real_x = coord_x * width
        real_y = coord_y * length
        
        # Initialize fingerprint profile with default weak signal value
        fingerprint = {bssid: -100 for bssid in all_bssids}
        
        # Fill in actual detected RSSI values
        for m in record.get("measurements", []):
            bssid = m.get("bssid")
            rssi = m.get("rssi")
            if bssid in fingerprint and rssi is not None:
                # Keep the strongest signal if multiple AP readings exist
                fingerprint[bssid] = max(fingerprint[bssid], rssi)
                
        X_list.append([fingerprint[b] for b in all_bssids])
        Y_list.append([real_x, real_y])
        
    X = np.array(X_list)
    Y = np.array(Y_list)
    
    print(f"Processed {len(X)} valid training samples.")
    return X, Y, all_bssids

def main():
    if len(sys.argv) < 2:
        print("Usage: python train_model.py <path_to_wifi_dataset.json>")
        sys.exit(1)
        
    json_path = sys.argv[1]
    if not os.path.exists(json_path):
        print(f"Error: File does not exist at '{json_path}'")
        sys.exit(1)
        
    # Load and preprocess data
    X, Y, bssids = load_and_preprocess_dataset(json_path)
    
    if len(X) < 5:
        print("Warning: Very small dataset. Split evaluation might be unreliable.")
        test_size = 0.2 if len(X) >= 5 else 1.0  # Avoid splitting if data size is extremely tiny
    else:
        test_size = 0.2
        
    # Split into train/validation sets
    if test_size < 1.0:
        X_train, X_test, Y_train, Y_test = train_test_split(X, Y, test_size=test_size, random_state=42)
        print(f"Split data into {len(X_train)} training samples and {len(X_test)} validation samples.")
    else:
        X_train, Y_train = X, Y
        X_test, Y_test = X, Y
        print("Using entire dataset for training and testing due to tiny sample count.")
        
    # 3. Train Random Forest Regressor model
    print("\nTraining Random Forest Regressor...")
    model = RandomForestRegressor(n_estimators=100, random_state=42, n_jobs=-1)
    model.fit(X_train, Y_train)
    print("Training complete.")
    
    # 4. Evaluate positioning accuracy
    predictions = model.predict(X_test)
    
    mae_x = mean_absolute_error(Y_test[:, 0], predictions[:, 0])
    mae_y = mean_absolute_error(Y_test[:, 1], predictions[:, 1])
    
    # Euclidean distance error in meters
    errors = np.sqrt(np.sum((Y_test - predictions) ** 2, axis=1))
    mean_euclidean_error = np.mean(errors)
    min_error = np.min(errors)
    max_error = np.max(errors)
    
    print("\n================ EVALUATION SUMMARY ================")
    print(f"Mean Error in X direction: {mae_x:.2f} meters")
    print(f"Mean Error in Y direction: {mae_y:.2f} meters")
    print(f"Mean Physical Positioning Error (Blue Dot Distance): {mean_euclidean_error:.2f} meters")
    print(f"Min Distance Error: {min_error:.2f} meters")
    print(f"Max Distance Error: {max_error:.2f} meters")
    print("====================================================")
    
    # 5. Show Top 10 most informative Access Points (Feature Importance)
    importances = model.feature_importances_
    indices = np.argsort(importances)[::-1]
    
    print("\nTop 10 Most Important Access Points for Localization:")
    for i in range(min(10, len(bssids))):
        idx = indices[i]
        print(f"{i+1}. BSSID: {bssids[idx]} | Importance: {importances[idx]:.4f}")
        
    # Save the model
    import joblib
    model_filename = "wifi_localization_model.joblib"
    bssids_filename = "model_bssids.json"
    
    joblib.dump(model, model_filename)
    with open(bssids_filename, 'w') as f:
        json.dump(bssids, f)
        
    print(f"\nModel saved successfully as: '{model_filename}'")
    print(f"Model BSSID list saved as: '{bssids_filename}'")

if __name__ == "__main__":
    main()
