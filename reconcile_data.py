import json
import os
import pandas as pd
import numpy as np

def reconcile_walks(json_path):
    if not os.path.exists(json_path):
        print(f"Dataset '{json_path}' not found.")
        return
        
    print(f"Loading survey data from '{json_path}'...")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    print(f"Total scans loaded: {len(data)}")
    
    # Parse records into a flat list of signal measurements
    flat_records = []
    for record in data:
        label = record.get("label", "")
        # Check if this is a path walk (either 'Path:' or 'Interpolated')
        if "to" in label.lower():
            # Extract path name and progress
            # Labels look like: "Path: Room 201 to Library (Progress: 35%)"
            # Or "Interpolated (X.Xm, Y.Ym)"
            path_name = label.split(" (Progress:")[0] if " (Progress:" in label else label
            progress = record.get("coord_x", 0.0) # Our progress ratio is saved on X
            
            for m in record.get("measurements", []):
                flat_records.append({
                    "path": path_name,
                    "progress": progress,
                    "bssid": m.get("bssid"),
                    "ssid": m.get("ssid"),
                    "rssi": m.get("rssi")
                })
                
    if not flat_records:
        print("No path walk data found to reconcile. Make sure to collect data using the Landmark walk mode.")
        return
        
    df = pd.DataFrame(flat_records)
    
    # 1. Show paths found in the data
    print("\nDetected Landmark Paths:")
    for path, group in df.groupby("path"):
        unique_samples = len(group[["progress", "bssid"]].drop_duplicates())
        unique_aps = group["bssid"].nunique()
        print(f" * '{path}': {unique_samples} aligned data points | {unique_aps} unique routers detected.")
        
    # 2. Reconcile data (group by path, AP, and progress step to calculate mean signal strength)
    # We round progress to nearest 10% interval (0.0, 0.1, 0.2...) to align multiple walks
    df["progress_bin"] = np.round(df["progress"], 1)
    
    reconciled_df = df.groupby(["path", "bssid", "ssid", "progress_bin"])["rssi"].mean().reset_index()
    
    # Sort for readability
    reconciled_df = reconciled_df.sort_values(by=["path", "bssid", "progress_bin"])
    
    # Output statistics
    print("\n========== RECONCILIATION SUMMARY ==========")
    print(f"Aligned and cleaned dataset size: {len(reconciled_df)} reconciled signal bins.")
    print("Multi-pass noise has been smoothed out via average aggregation.")
    print("============================================")
    
    # Save the reconciled dataset
    output_path = "reconciled_wifi_dataset.json"
    reconciled_records = []
    # Group back into JSON-friendly format
    for (path, progress_bin), group in reconciled_df.groupby(["path", "progress_bin"]):
        measurements = []
        for _, row in group.iterrows():
            measurements.append({
                "ssid": row["ssid"],
                "bssid": row["bssid"],
                "rssi": int(row["rssi"])
            })
        reconciled_records.append({
            "label": f"{path} (Reconciled Progress: {int(progress_bin*100)}%)",
            "coord_x": progress_bin,
            "coord_y": 0.0,
            "floor_plan": "Reconciled Landmark Path",
            "measurements": measurements
        })
        
    with open(output_path, 'w', encoding='utf-8') as out_f:
        json.dump(reconciled_records, out_f, indent=2)
    print(f"Saved reconciled dataset to: '{output_path}'")

if __name__ == "__main__":
    analyze_file = "wifi_dataset.json"
    reconcile_walks(analyze_file)
