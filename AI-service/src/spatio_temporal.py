import math
from datetime import datetime
from typing import Optional
from .config import CAMPUS_LOCATIONS

class SpatioTemporalScorer:
    """
    Computes real-world physical feasibility scores based on time decay
    and campus location landmark matching.
    """
    def __init__(self, time_half_life_days: float = 7.0):
        self.time_decay_factor = math.log(2.0) / time_half_life_days

    def compute_temporal_similarity(self, dt1_str: Optional[str], dt2_str: Optional[str]) -> float:
        """Computes exponential decay based on days difference."""
        if not dt1_str or not dt2_str:
            return 0.5  # Neutral default if one timestamp is missing

        try:
            # Parse ISO-8601 strings
            dt1 = datetime.fromisoformat(dt1_str.replace("Z", "+00:00"))
            dt2 = datetime.fromisoformat(dt2_str.replace("Z", "+00:00"))

            diff_seconds = abs((dt1 - dt2).total_seconds())
            diff_days = diff_seconds / 86400.0

            # Exponential decay: 0 days -> 1.0, 7 days -> 0.5, 14 days -> 0.25
            score = math.exp(-self.time_decay_factor * diff_days)
            return float(max(0.05, min(1.0, score)))
        except Exception:
            return 0.5

    def compute_spatial_similarity(self, loc1: Optional[str], loc2: Optional[str]) -> float:
        """Computes campus landmark overlap similarity."""
        if not loc1 or not loc2:
            return 0.4  # Neutral baseline

        l1 = loc1.lower().strip()
        l2 = loc2.lower().strip()

        if l1 == l2:
            return 1.0

        score = 0.0
        # Check campus landmarks
        matched_landmarks = 0
        for landmark in CAMPUS_LOCATIONS:
            if landmark in l1 and landmark in l2:
                matched_landmarks += 1

        if matched_landmarks > 0:
            score += 0.5 + min(0.4, matched_landmarks * 0.2)
        else:
            # Check basic word overlap
            words1 = set(l1.split())
            words2 = set(l2.split())
            overlap = len(words1.intersection(words2))
            if overlap > 0:
                score += min(0.6, overlap * 0.2)
            else:
                score = 0.2

        return float(min(1.0, score))

    def compute(self, dt1: Optional[str], dt2: Optional[str], loc1: Optional[str], loc2: Optional[str]) -> float:
        s_time = self.compute_temporal_similarity(dt1, dt2)
        s_space = self.compute_spatial_similarity(loc1, loc2)
        # Weighted combination: 50% time, 50% space
        return float(0.5 * s_time + 0.5 * s_space)
