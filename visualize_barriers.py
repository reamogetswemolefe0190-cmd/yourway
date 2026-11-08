import json
import os
import numpy as np
import matplotlib.pyplot as plt

def analyze_barriers(json_path):
    print(f"Loading data from {json_path}...")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    # Sort samples by timestamp (chronological walk order)
    data = sorted(data, key=lambda x: x.get("timestamp", 0))
    
    distances = []
    rssi_profile_ap1 = [] # Track signal for BSSID 00:30:4f:b5:d2:1e (which dropped down the stairs)
    rssi_profile_ap2 = [] # Track signal for BSSID 58:16:51:8c:93:e0 (the 1st floor AP that got stronger)
    
    ap1_mac = "00:30:4f:b5:d2:1e"
    ap2_mac = "58:16:51:8c:93:e0"
    
    # Parse coordinates to calculate total distance walked in meters
    width, length = 50.0, 30.0 # Grid dimensions
    
    start_x = data[0].get("coord_x", 0.0) * width
    start_y = data[0].get("coord_y", 0.0) * length
    
    path_distances = []
    
    for record in data:
        cx = record.get("coord_x", 0.0) * width
        cy = record.get("coord_y", 0.0) * length
        
        # Distance from starting point
        d = np.sqrt((cx - start_x)**2 + (cy - start_y)**2)
        path_distances.append(d)
        
        # Read RSSI for the two main target APs (default to -100 if not found)
        rssi1 = -100
        rssi2 = -100
        for m in record.get("measurements", []):
            if m.get("bssid") == ap1_mac:
                rssi1 = m.get("rssi")
            elif m.get("bssid") == ap2_mac:
                rssi2 = m.get("rssi")
        rssi_profile_ap1.append(rssi1)
        rssi_profile_ap2.append(rssi2)

    # Calculate Attenuation rates (signal drop per meter)
    print("\n========== ATTENUATION ANALYSIS (CONCRETE BARRIERS) ==========")
    for i in range(1, len(data)):
        dist_diff = path_distances[i] - path_distances[i-1]
        if dist_diff > 0.1: # Skip static steps
            rssi_diff_ap1 = rssi_profile_ap1[i] - rssi_profile_ap1[i-1]
            drop_rate_ap1 = abs(rssi_diff_ap1) / dist_diff
            
            # If signal decays faster than 5 dBm per meter, flag it as a barrier
            if drop_rate_ap1 > 5.0 and rssi_diff_ap1 < 0:
                print(f"⚠️ CONCRETE BARRIER DETECTED! at walk distance {path_distances[i]:.2f}m")
                print(f"  Signal of AP {ap1_mac} dropped rapidly by {abs(rssi_diff_ap1)} dBm over a walk of just {dist_diff:.2f}m.")
                print(f"  Attenuation rate: {drop_rate_ap1:.1f} dBm/meter (Drywall is typically < 2.0 dBm/m)\n")

    # Plot the signal graphs
    plt.figure(figsize=(10, 6))
    plt.plot(path_distances, rssi_profile_ap1, label=f"2nd Floor AP ({ap1_mac})", color="#EF4444", marker="o", linewidth=2.5)
    plt.plot(path_distances, rssi_profile_ap2, label=f"1st Floor AP ({ap2_mac})", color="#10B981", marker="s", linewidth=2.5)
    
    # Highlight the stair transition/floor slab barrier zone
    plt.axvspan(0.8, 2.5, color="gray", alpha=0.2, label="Floor Slab / Staircase Concrete Barrier")
    
    plt.title("Wi-Fi Signal Attenuation: Floor-to-Floor Transition", fontsize=14, fontweight="bold")
    plt.xlabel("Walk Distance along Corridor (meters)", fontsize=12)
    plt.ylabel("Received Signal Strength (RSSI in dBm)", fontsize=12)
    plt.grid(True, linestyle="--", alpha=0.5)
    plt.legend(loc="lower left", fontsize=11)
    
    # Save the generated graph
    output_png = "wifi_signal_attenuation.png"
    plt.savefig(output_png, dpi=300)
    print(f"Successfully saved signal barrier plot as: '{output_png}'")
    plt.close()

if __name__ == "__main__":
    analyze_barriers("wifi_dataset.json")
