import numpy as np
from PIL import Image


class HSVColorExtractor:
    """
    Bộ trích xuất đặc trưng màu sắc lai (Center-Weighted Hybrid HSV Histogram) gồm 176 chiều:
    - Nhánh có sắc (Chromatic H-S: 18x8 = 144 bins): nhận diện màu rực rỡ (đỏ, xanh, vàng...).
    - Nhánh vô sắc (Achromatic S-V: 4x8 = 32 bins): nhận diện màu Đen, Xám, Bạc, Trắng
      (chiếm >60% đồ thất lạc ngoài đời thực như ví da, điện thoại, balo, laptop).
    - Áp dụng trọng số không gian Gaussian tập trung ở tâm ảnh để giảm nhiễu viền nền còn sót lại.
    """

    def __init__(self, h_bins: int = 18, s_bins: int = 8, achrom_s_bins: int = 4, achrom_v_bins: int = 8):
        self.h_bins = h_bins
        self.s_bins = s_bins
        self.achrom_s_bins = achrom_s_bins
        self.achrom_v_bins = achrom_v_bins
        self.total_dim = h_bins * s_bins + achrom_s_bins * achrom_v_bins  # 144 + 32 = 176 chiều

    def extract_histogram(self, image: Image.Image) -> np.ndarray:
        """
        Chuyển ảnh PIL sang phân bố xác suất màu HSV 176 chiều có tổng bằng 1.0.
        """
        if image is None:
            return np.zeros((self.total_dim,), dtype=np.float32)

        try:
            # 1. Resize ảnh về 128x128 và chuyển sang không gian màu HSV
            resized = image.resize((128, 128), Image.Resampling.BILINEAR).convert("HSV")
            arr = np.array(resized, dtype=np.float32)

            h = arr[:, :, 0] / 255.0 * 180.0  # Hue (tông màu): [0, 180]
            s = arr[:, :, 1] / 255.0          # Saturation (độ bão hòa): [0, 1]
            v = arr[:, :, 2] / 255.0          # Value (độ sáng): [0, 1]

            # 2. Tạo mặt nạ trọng số tâm Gaussian 2D:
            #    w(x,y) = exp(-1.2 * ((y - 63.5)^2 / 64^2 + (x - 63.5)^2 / 64^2))
            #    Pixel ở tâm ảnh (vật thể chính) có trọng số ~1.0, pixel ở 4 góc viền bị giảm xuống ~0.09
            yy, xx = np.ogrid[:128, :128]
            cy, cx = 63.5, 63.5
            dist_sq = ((yy - cy) / 64.0) ** 2 + ((xx - cx) / 64.0) ** 2
            spatial_weights = np.exp(-1.2 * dist_sq).astype(np.float32)

            # 3. Tách pixel Có sắc (Chromatic) và Vô sắc (Achromatic: đen, xám, trắng)
            chrom_mask = (s > 0.16) & (v > 0.14) & (v < 0.96)
            achrom_mask = ~chrom_mask

            # 4. Tính Histogram 2D cho nhánh Có sắc (144 bins)
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

            # 5. Tính Histogram 2D cho nhánh Vô sắc (32 bins)
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

            # 6. Ghép 2 nhánh thành vector 176 chiều và chuẩn hóa tổng xác suất = 1.0
            combined = np.concatenate([hist_hs.flatten(), hist_sv.flatten()]).astype(np.float32)
            total = float(np.sum(combined))
            if total > 0:
                combined /= total
            return combined
        except Exception:
            return np.zeros((self.total_dim,), dtype=np.float32)

    def compare_histograms(self, hist1: np.ndarray, hist2: np.ndarray) -> float:
        """
        Tính độ trùng khớp màu sắc bằng Hệ số Bhattacharyya (Bhattacharyya Coefficient):
            S_color = sum_{i=1..176} sqrt(hist1[i] * hist2[i])
        Kết quả nằm trong [0.0, 1.0]: 1.0 khi 2 ảnh có phân bố màu giống hệt nhau.
        """
        if hist1 is None or hist2 is None or len(hist1) == 0 or len(hist2) == 0:
            return 0.0
        if len(hist1) != len(hist2):
            return 0.5
        score = float(np.sum(np.sqrt(np.maximum(0.0, hist1 * hist2))))
        return float(np.clip(score, 0.0, 1.0))
