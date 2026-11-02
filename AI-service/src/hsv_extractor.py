import numpy as np
from PIL import Image

class HSVColorExtractor:
    """
    Extracts explicit Hue-Saturation color histograms to prevent color hallucination
    common in deep vision-language models.
    """
    def __init__(self, h_bins: int = 18, s_bins: int = 8):
        self.h_bins = h_bins
        self.s_bins = s_bins

    def extract_histogram(self, image: Image.Image) -> np.ndarray:
        """Computes normalized 2D Hue-Saturation histogram from PIL Image."""
        if image is None:
            return np.zeros((self.h_bins * self.s_bins,))

        try:
            # Convert RGB PIL Image to HSV array
            hsv = image.convert("HSV")
            arr = np.array(hsv, dtype=np.float32)

            h = arr[:, :, 0] / 255.0 * 180.0  # [0, 180]
            s = arr[:, :, 1] / 255.0          # [0, 1]
            v = arr[:, :, 2] / 255.0          # [0, 1]

            # Filter out extreme dark or bright pixels to focus on dominant object color
            mask = (v > 0.15) & (v < 0.95) & (s > 0.10)
            if not np.any(mask):
                mask = np.ones_like(v, dtype=bool)

            valid_h = h[mask]
            valid_s = s[mask]

            hist, _, _ = np.histogram2d(
                valid_h, valid_s,
                bins=[self.h_bins, self.s_bins],
                range=[[0, 180], [0, 1]]
            )
            hist = hist.flatten()
            total = np.sum(hist)
            if total > 0:
                hist = hist / total
            return hist
        except Exception:
            return np.zeros((self.h_bins * self.s_bins,))

    def compare_histograms(self, hist1: np.ndarray, hist2: np.ndarray) -> float:
        """Computes Bhattacharyya affinity coefficient between two normalized histograms."""
        if hist1 is None or hist2 is None or len(hist1) == 0 or len(hist2) == 0:
            return 0.0
        # Bhattacharyya coefficient: sum(sqrt(p * q))
        score = np.sum(np.sqrt(hist1 * hist2))
        return float(np.clip(score, 0.0, 1.0))
