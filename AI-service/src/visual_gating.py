from typing import Dict, Any, Optional
import numpy as np
from PIL import Image

class VisualGatingLayer:
    """
    Adaptive multimodal gating mechanism configured according to business rules:
    - When both photos are clear: Image is 55% (0.55), Color 15%, Text 15%, Time/Location 15%.
      Hard cutoff: If image similarity < 55%, candidate is REJECTED/DROPPED immediately.
    - When photo is blurry/low-res: Image weight decreases to 30%, shifting weight to Text (35%)
      and Spatio-temporal (20%), no hard cutoff applied.
    - When only 1 post has image: Cross-modal Text-to-Image 30%, Text 45%, Time/Location 25%.
    - When neither post has image: Text 60%, Time/Location 40%.
    """
    def __init__(self):
        pass

    @staticmethod
    def check_image_blur(image: Optional[Image.Image]) -> bool:
        """Returns True if image is blurry or very low resolution (< 100px or low gradient variance)."""
        if image is None:
            return True
        try:
            w, h = image.size
            if w < 100 or h < 100:
                return True
            gray = np.array(image.convert("L"), dtype=np.float32)
            # Estimate sharpness via gradient variance
            gx, gy = np.gradient(gray)
            grad_var = np.var(gx) + np.var(gy)
            return bool(grad_var < 18.0)
        except Exception:
            return False

    def gate_and_fuse(
        self,
        has_source_img: bool,
        has_cand_img: bool,
        is_source_blurry: bool,
        is_cand_blurry: bool,
        visual_sim: float,
        text_sim: float,
        color_sim: float,
        spatio_temporal_sim: float
    ) -> Dict[str, Any]:
        has_both_images = has_source_img and has_cand_img
        has_blurry_img = is_source_blurry or is_cand_blurry

        is_rejected = False

        if has_both_images:
            if not has_blurry_img:
                # ── RULE 1: Có ảnh rõ nét ──
                # Ảnh 55%, Dưới 55% loại luôn!
                w_vis = 0.55
                w_color = 0.15
                w_text = 0.15
                w_st = 0.15

                # Hard Cutoff threshold: < 55% loại luôn
                if visual_sim < 0.55:
                    is_rejected = True
            else:
                # ── RULE 2: Ảnh bị mờ, vỡ, chụp không rõ ──
                # Giảm tỷ lệ ảnh xuống 30%, tăng Text và Không/Thời gian, không phạt hard cutoff
                w_vis = 0.30
                w_color = 0.15
                w_text = 0.35
                w_st = 0.20
        elif has_source_img or has_cand_img:
            # ── RULE 3: 1 bên có ảnh, 1 bên chỉ có chữ mô tả (Cross-Modal) ──
            w_vis = 0.30
            w_color = 0.00
            w_text = 0.45
            w_st = 0.25
        else:
            # ── RULE 4: Cả 2 bên đều không có ảnh (Thuần bài chữ) ──
            w_vis = 0.00
            w_color = 0.00
            w_text = 0.60
            w_st = 0.40

        # Chuẩn hóa tổng trọng số = 1.0
        total_w = w_vis + w_color + w_text + w_st
        w_vis /= total_w
        w_color /= total_w
        w_text /= total_w
        w_st /= total_w

        composite_score = (
            w_vis * visual_sim +
            w_color * color_sim +
            w_text * text_sim +
            w_st * spatio_temporal_sim
        )

        final_percentage = round(min(99.0, max(5.0, composite_score * 100.0)), 1)

        # Ngưỡng tổng tối thiểu để hiển thị là 45%
        if final_percentage < 45.0:
            is_rejected = True

        return {
            "matchScore": final_percentage,
            "isRejected": is_rejected,
            "weights": {
                "visual": round(w_vis, 2),
                "color": round(w_color, 2),
                "text": round(w_text, 2),
                "spatioTemporal": round(w_st, 2)
            },
            "breakdown": {
                "visual": round(visual_sim * 100.0, 1),
                "color": round(color_sim * 100.0, 1),
                "text": round(text_sim * 100.0, 1),
                "spatioTemporal": round(spatio_temporal_sim * 100.0, 1)
            }
        }
