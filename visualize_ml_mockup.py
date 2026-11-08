import numpy as np
import matplotlib.pyplot as plt

def generate_mockup_plot():
    # Set seed for reproducibility
    np.random.seed(42)
    
    # 1. Simulate progress along the path (0.0 = Start, 1.0 = Finish)
    true_progress = np.linspace(0, 1, 100)
    
    # Simulate signal profiles for 3 routers along this path
    # AP1 (Start Anchor)
    ap1_base = -40 - 45 * true_progress**1.5
    # AP2 (Midpoint Anchor)
    ap2_base = -85 + 40 * np.exp(-((true_progress - 0.5)/0.2)**2)
    # AP3 (End Anchor)
    ap3_base = -85 + 45 * true_progress**1.5
    
    # 2. Simulate 3 separate noisy walk passes
    passes_ap1 = [ap1_base + np.random.normal(0, 4, 100) for _ in range(3)]
    passes_ap2 = [ap2_base + np.random.normal(0, 4, 100) for _ in range(3)]
    passes_ap3 = [ap3_base + np.random.normal(0, 4, 100) for _ in range(3)]
    
    # Reconciled (Averaged) Signals
    rec_ap1 = np.mean(passes_ap1, axis=0)
    rec_ap2 = np.mean(passes_ap2, axis=0)
    rec_ap3 = np.mean(passes_ap3, axis=0)
    
    # 3. Simulate ML Position Predictions
    # Raw prediction (PDR or single-pass walk) has noise/jitter
    raw_predictions = true_progress + np.random.normal(0, 0.09, 100)
    raw_predictions = np.clip(raw_predictions, 0, 1)
    
    # Reconciled ML predictions (trained on smoothed data) are clean and linear
    reconciled_predictions = true_progress + np.random.normal(0, 0.02, 100)
    reconciled_predictions = np.clip(reconciled_predictions, 0, 1)
    
    # Set dark-themed styling for a premium developer feel
    plt.style.use('dark_background')
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
    fig.patch.set_facecolor('#0F172A')
    
    # Subplot 1: Signal Reconciliation (Input Data)
    ax1.set_facecolor('#1E293B')
    # Plot raw noisy points for one pass to show input noise
    ax1.scatter(true_progress, passes_ap1[0], color='#EF4444', alpha=0.3, s=15, label="Raw Pass 1 (AP1)")
    ax1.scatter(true_progress, passes_ap2[0], color='#38BDF8', alpha=0.3, s=15, label="Raw Pass 1 (AP2)")
    ax1.scatter(true_progress, passes_ap3[0], color='#10B981', alpha=0.3, s=15, label="Raw Pass 1 (AP3)")
    
    # Plot reconciled smooth curves
    ax1.plot(true_progress, rec_ap1, color='#EF4444', linewidth=2.5, label="Reconciled AP1 (Stairs)")
    ax1.plot(true_progress, rec_ap2, color='#38BDF8', linewidth=2.5, label="Reconciled AP2 (Midway)")
    ax1.plot(true_progress, rec_ap3, color='#10B981', linewidth=2.5, label="Reconciled AP3 (Library)")
    
    ax1.set_title("1. Input Signal Reconciliation & Smoothing", fontsize=12, fontweight='bold', pad=12)
    ax1.set_xlabel("Normalized Walk Progress (0.0 = Start, 1.0 = Finish)", fontsize=10)
    ax1.set_ylabel("Signal Strength (RSSI in dBm)", fontsize=10)
    ax1.grid(True, color='#334155', linestyle='--', alpha=0.5)
    ax1.legend(loc="lower left", framealpha=0.8, facecolor='#0F172A', edgecolor='#334155')
    
    # Subplot 2: ML Positioning Accuracy (Output Prediction)
    ax2.set_facecolor('#1E293B')
    ax2.plot(true_progress, true_progress, color='#64748B', linestyle='--', linewidth=2, label="Perfect Actual Path")
    ax2.scatter(true_progress, raw_predictions, color='#F43F5E', alpha=0.5, s=20, label="Single-Walk Prediction Jitter")
    ax2.plot(true_progress, reconciled_predictions, color='#10B981', linewidth=3, label="Reconciled ML Positioning (Blue Dot)")
    
    ax2.set_title("2. Machine Learning Position Prediction", fontsize=12, fontweight='bold', pad=12)
    ax2.set_xlabel("Actual Normalized Distance (meters)", fontsize=10)
    ax2.set_ylabel("ML Predicted Distance (meters)", fontsize=10)
    ax2.grid(True, color='#334155', linestyle='--', alpha=0.5)
    ax2.legend(loc="upper left", framealpha=0.8, facecolor='#0F172A', edgecolor='#334155')
    
    plt.tight_layout()
    output_png = "ml_positioning_mockup.png"
    plt.savefig(output_png, dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    print(f"Successfully generated visual ML mockup: '{output_png}'")
    plt.close()

if __name__ == "__main__":
    generate_mockup_plot()
