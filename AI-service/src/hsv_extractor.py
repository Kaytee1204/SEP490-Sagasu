import numpy as np
from PIL import Image


class HSVColorExtractor:
    """
    Extracts a Center-Weighted Hybrid Chromatic (H-S, 18x8=144 bins) + Achromatic (S-V, 4x8=32 bins)
    176-dimensional color histogram.
    - Handles both colorful objects and achromatic objects (Black, Dark Brown, Gray, Silver, White)
      which represent >60% of real-world lost & found items (wallets, backpacks, phones, glasses).
    - Applies spatial center-weighting to suppress residual background corners.
    """

    def __init__(self, h_bins: int = 18, s_bins: int = 8, achrom_s_bins: int = 4, achrom_v_bins: int = 8):
        self.h_bins = h_bins
        self.s_bins = s_bins
        self.achrom_s_bins = achrom_s_bins
        self.achrom_v_bins = achrom_v_bins
        self.total_dim = h_bins * s_bins + achrom_s_bins * achrom_v_bins  # 144 + 32 = 176

    def extract_histogram(self, image: Image.Image) -> np.ndarray:
        """Computes normalized 176-d Center-Weighted Hybrid HSV histogram from PIL Image."""
        if image is None:
            return np.zeros((self.total_dim,), dtype=np.float32)

        try:
            # Downsample to 128x128 for fast, consistent spatial weighting
            resized = image.resize((128, 128), Image.Resampling.BILINEAR).convert("HSV")
            arr = np.array(resized, dtype=np.float32)

            h = arr[:, :, 0] / 255.0 * 180.0  # [0, 180]
            s = arr[:, :, 1] / 255.0          # [0, 1]
            v = arr[:, :, 2] / 255.0          # [0, 1]

            # Spatial center-weighting mask (focus on the object in the center, suppress border background)
            yy, xx = np.ogrid[:128, :128]
            cy, cx = 63.5, 63.5
            dist_sq = ((yy - cy) / 64.0) ** 2 + ((xx - cx) / 64.0) ** 2
            spatial_weights = np.exp(-1.2 * dist_sq).astype(np.float32)

            # Separate Chromatic vs Achromatic (black, gray, white) pixels
            chrom_mask = (s > 0.16) & (v > 0.14) & (v < 0.96)
            achrom_mask = ~chrom_mask

            if np.any(chrom_mask):
                hist_hs, _, _ = np.histogram2d(
                    h[chrom_mask],
                    s[chrom_mask],
                    bins=[self.h_bins, self.s_bins],
                    range=[[0, 180], [0, 1]],
                    weights=spatial_weights[chrom_mask],
                )
            else:
                hist_hs = np.zeros((self.h_bins, self.s_bins), dtype=np.float32)

            if np.any(achrom_mask):
                hist_sv, _, _ = np.histogram2d(
                    s[achrom_mask],
                    v[achrom_mask],
                    bins=[self.achrom_s_bins, self.achrom_v_bins],
                    range=[[0, 0.25], [0, 1]],
                    weights=spatial_weights[achrom_mask],
                )
            else:
                hist_sv = np.zeros((self.achrom_s_bins, self.achrom_v_bins), dtype=np.float32)

            combined = np.concatenate([hist_hs.flatten(), hist_sv.flatten()]).astype(np.float32)
            total = float(np.sum(combined))
            if total > 0:
                combined /= total
            return combined
        except Exception:
            return np.zeros((self.total_dim,), dtype=np.float32)

    def compare_histograms(self, hist1: np.ndarray, hist2: np.ndarray) -> float:
        """Computes Bhattacharyya affinity coefficient between two normalized histograms."""
        if hist1 is None or hist2 is None or len(hist1) == 0 or len(hist2) == 0:
            return 0.0
        if len(hist1) != len(hist2):
            return 0.5
        score = float(np.sum(np.sqrt(np.maximum(0.0, hist1 * hist2))))
        return float(np.clip(score, 0.0, 1.0))
