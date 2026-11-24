import math
from datetime import datetime
from typing import Optional
from .config import CAMPUS_LOCATIONS


class SpatioTemporalScorer:
    """
    Bộ chấm điểm tính hợp lý về Không gian (Địa điểm) và Thời gian giữa 2 bài LOST - FOUND.
    """

    def __init__(self, time_half_life_days: float = 7.0):
        # Chu kỳ bán rã = 7 ngày => hệ số suy giảm lambda = ln(2) / 7 ~= 0.09902
        self.time_decay_factor = math.log(2.0) / time_half_life_days

    def compute_temporal_similarity(self, dt1_str: Optional[str], dt2_str: Optional[str]) -> float:
        """
        Tính điểm tương đồng thời gian theo hàm suy giảm mũ (Exponential Decay):
            S_time = exp(-lambda * diff_days)
        Ví dụ:
        - Cách nhau 0 ngày  -> 1.00 (100%)
        - Cách nhau 7 ngày  -> 0.50 (50%)
        - Cách nhau 14 ngày -> 0.25 (25%)
        Nếu thiếu 1 trong 2 mốc thời gian, trả về mức trung tính 0.50.
        """
        if not dt1_str or not dt2_str:
            return 0.5

        try:
            dt1 = datetime.fromisoformat(dt1_str.replace("Z", "+00:00"))
            dt2 = datetime.fromisoformat(dt2_str.replace("Z", "+00:00"))

            diff_seconds = abs((dt1 - dt2).total_seconds())
            diff_days = diff_seconds / 86400.0

            score = math.exp(-self.time_decay_factor * diff_days)
            return float(max(0.05, min(1.0, score)))
        except Exception:
            return 0.5

    def compute_spatial_similarity(self, loc1: Optional[str], loc2: Optional[str]) -> float:
        """
        Tính điểm tương đồng địa điểm trong khuôn viên trường:
        - Giống hệt nhau (sau khi lowercase) -> 1.00
        - Trùng k địa danh trong CAMPUS_LOCATIONS (vd: "thư viện", "tầng 2") -> 0.5 + min(0.4, 0.2 * k)
        - Không trùng địa danh chuẩn nhưng có từ giao nhau -> min(0.6, overlap * 0.2)
        - Hoàn toàn khác nhau -> 0.20 (hoặc 0.40 nếu thiếu dữ liệu)
        """
        if not loc1 or not loc2:
            return 0.4

        l1 = loc1.lower().strip()
        l2 = loc2.lower().strip()

        if l1 == l2:
            return 1.0

        score = 0.0
        matched_landmarks = 0
        for landmark in CAMPUS_LOCATIONS:
            if landmark in l1 and landmark in l2:
                matched_landmarks += 1

        if matched_landmarks > 0:
            score += 0.5 + min(0.4, matched_landmarks * 0.2)
        else:
            words1 = set(l1.split())
            words2 = set(l2.split())
            overlap = len(words1.intersection(words2))
            if overlap > 0:
                score += min(0.6, overlap * 0.2)
            else:
                score = 0.2

        return float(min(1.0, score))

    def compute(self, dt1: Optional[str], dt2: Optional[str], loc1: Optional[str], loc2: Optional[str]) -> float:
        """
        Kết hợp 50% điểm Thời gian + 50% điểm Địa điểm:
            S_st = 0.5 * S_time + 0.5 * S_space
        """
        s_time = self.compute_temporal_similarity(dt1, dt2)
        s_space = self.compute_spatial_similarity(loc1, loc2)
        return float(0.5 * s_time + 0.5 * s_space)
