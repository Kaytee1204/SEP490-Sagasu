from typing import Dict, Any, Optional
import numpy as np
from PIL import Image


class VisualGatingLayer:
    """
    Tầng cổng thích nghi đa phương thức (Adaptive Multimodal Gating Layer).
    Tự động phân bổ bộ trọng số (w_vis, w_color, w_text, w_cross, w_st) tùy vào chất lượng dữ liệu đầu vào
    để kết hợp 5 thành phần điểm [0..1] thành điểm phần trăm cuối cùng (matchScore %):

    1. visual_sim          : Điểm so sánh Ảnh <-> Ảnh (CLIP ViT-B/16, lấy cặp ảnh khớp nhất giữa 2 bài).
    2. color_sim           : Điểm so sánh Màu sắc (HSV 176-dim Bhattacharyya).
    3. text_sim            : Điểm so sánh Mô tả <-> Mô tả (Multilingual CLIP Text).
    4. cross_modal_sim     : Điểm so sánh chéo Ảnh <-> Mô tả 2 chiều (Ảnh A <-> Mô tả B và Mô tả A <-> Ảnh B).
    5. spatio_temporal_sim : Điểm Thời gian (Exponential Decay) & Địa điểm (Campus Landmarks).

    Công thức tính phần trăm cuối cùng:
        composite_score = w_vis * visual_sim
                        + w_color * color_sim
                        + w_text * text_sim
                        + w_cross * cross_modal_sim
                        + w_st * spatio_temporal_sim
        matchScore (%) = round(clip(composite_score * 100.0, 5.0, 99.0), 1)
    """

    def __init__(self):
        pass

    @staticmethod
    def check_image_blur(image: Optional[Image.Image]) -> bool:
        """
        Kiểm tra ảnh có bị mờ / vỡ nét hay độ phân giải quá thấp không:
        - Nếu rộng < 100px hoặc cao < 100px -> coi là ảnh kém chất lượng (True).
        - Chuyển sang ảnh xám (L), tính đạo hàm bậc 1 theo phương ngang (Gx) và dọc (Gy).
        - Tính tổng phương sai gradient: grad_var = Var(Gx) + Var(Gy).
        - Nếu grad_var < 18.0 -> ảnh thiếu cạnh sắc nét (bị mờ -> True).
        """
        if image is None:
            return True
        try:
            w, h = image.size
            if w < 100 or h < 100:
                return True
            gray = np.array(image.convert("L"), dtype=np.float32)
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
        spatio_temporal_sim: float,
        cross_modal_sim: float = 0.0,
    ) -> Dict[str, Any]:
        """
        Hợp nhất 5 điểm thành phần bằng cơ chế chia trọng số thích nghi theo 4 kịch bản:
        - RULE 1 (Cả 2 có ảnh rõ nét):
            Ảnh-Ảnh 45% | Màu 15% | Mô tả-Mô tả 15% | Ảnh-Mô tả 15% | Thời gian & Địa điểm 10%
            Hard Cutoff: Nếu điểm Ảnh-Ảnh < 55% (0.55) VÀ Ảnh-Mô tả < 70% (0.70) -> loại ngay.
        - RULE 2 (Cả 2 có ảnh nhưng >= 1 ảnh bị mờ/vỡ):
            Ảnh-Ảnh 25% | Màu 10% | Mô tả-Mô tả 30% | Ảnh-Mô tả 20% | Thời gian & Địa điểm 15%
            (Không áp dụng Hard Cutoff để tránh loại nhầm do ảnh chụp rung tay/thiếu sáng).
        - RULE 3 (Chỉ 1 bên có ảnh, 1 bên chỉ có mô tả chữ):
            Ảnh-Ảnh 0%  | Màu 0%  | Mô tả-Mô tả 40% | Ảnh-Mô tả 40% | Thời gian & Địa điểm 20%
        - RULE 4 (Cả 2 bên chỉ có mô tả chữ, không có ảnh):
            Ảnh-Ảnh 0%  | Màu 0%  | Mô tả-Mô tả 65% | Ảnh-Mô tả 0%  | Thời gian & Địa điểm 35%
        """
        has_both_images = has_source_img and has_cand_img
        has_blurry_img = is_source_blurry or is_cand_blurry

        is_rejected = False
        reject_reason = None

        if has_both_images:
            if not has_blurry_img:
                # ── RULE 1: Cả 2 bài đều có ảnh rõ nét ──
                # 45% Ảnh-Ảnh (ViT-B/16) + 25% Mô tả-Mô tả (Hybrid Keyword + M-CLIP)
                # + 12% Màu HSV + 10% Ảnh-Mô tả (ViT-B/16) + 8% Thời gian & Địa điểm
                w_vis = 0.45
                w_text = 0.25
                w_color = 0.12
                w_cross = 0.10
                w_st = 0.08

                # Hard Cutoff 1 (Chặn ảnh khác biệt nhau hoàn toàn):
                # Nếu ảnh < 50% (raw_vis < 0.70) VÀ mô tả cũng không khớp rõ (< 55%) -> LOẠI NGAY
                if visual_sim < 0.50 and text_sim < 0.55:
                    is_rejected = True
                    reject_reason = "Hình ảnh và mô tả đều không tương đồng"
                # Hard Cutoff 2: Ảnh quá khác nhau (< 42%, tức raw_vis < 0.64) -> LOẠI NGAY dù mô tả giống
                elif visual_sim < 0.42:
                    is_rejected = True
                    reject_reason = "Hình ảnh quá khác biệt (< 42%)"
                # Hard Cutoff 3 (Lệch màu cực mạnh khi ảnh chưa rất giống):
                elif color_sim < 0.25 and visual_sim < 0.75:
                    is_rejected = True
                    reject_reason = "Màu sắc quá khác biệt (< 25%)"
                # Hard Cutoff 4 (Chặn 2 bài gọi tên rõ ràng 2 loại đồ vật hoàn toàn trái ngược nhau, vd: Balo vs Ví da):
                elif text_sim < 0.12:
                    is_rejected = True
                    reject_reason = "Tên và mô tả đồ vật hoàn toàn khác loại"
            else:
                # ── RULE 2: Có ảnh bị mờ, vỡ, thiếu sáng -> giảm trọng số ảnh, tăng Mô tả & Ảnh-Mô tả ──
                w_vis = 0.25
                w_color = 0.10
                w_text = 0.35
                w_cross = 0.18
                w_st = 0.12

                if visual_sim < 0.38 and text_sim < 0.50:
                    is_rejected = True
                    reject_reason = "Ảnh mờ và mô tả đều không tương đồng"
        elif has_source_img or has_cand_img:
            # ── RULE 3: 1 bên có ảnh, 1 bên chỉ có mô tả -> đẩy mạnh Mô tả & Ảnh-Mô tả ──
            w_vis = 0.00
            w_color = 0.00
            w_text = 0.45
            w_cross = 0.40
            w_st = 0.15
        else:
            # ── RULE 4: Cả 2 bên đều không có ảnh -> thuần Mô tả <-> Mô tả + Không/Thời gian ──
            w_vis = 0.00
            w_color = 0.00
            w_text = 0.70
            w_cross = 0.00
            w_st = 0.30

        # Chuẩn hóa tổng trọng số luôn bằng đúng 1.0 (100%)
        total_w = w_vis + w_color + w_text + w_cross + w_st
        w_vis /= total_w
        w_color /= total_w
        w_text /= total_w
        w_cross /= total_w
        w_st /= total_w

        # Tính tổ hợp tuyến tính ra điểm tổng [0.0 .. 1.0]
        composite_score = (
            w_vis * visual_sim
            + w_color * color_sim
            + w_text * text_sim
            + w_cross * cross_modal_sim
            + w_st * spatio_temporal_sim
        )

        # Quy đổi ra thang phần trăm [5.0% .. 99.0%], làm tròn 1 chữ số thập phân
        final_percentage = round(min(99.0, max(5.0, composite_score * 100.0)), 1)

        # Ngưỡng sàn hiển thị: 50.0% (đủ chặt để loại rác, đủ thoáng để không làm rớt món đồ đúng bị khác ngày/vị trí)
        if final_percentage < 50.0:
            is_rejected = True
            reject_reason = reject_reason or "Tổng điểm trùng khớp < 50%"

        return {
            "matchScore": final_percentage,
            "isRejected": is_rejected,
            "rejectReason": reject_reason,
            "weights": {
                "visual": round(w_vis, 2),
                "color": round(w_color, 2),
                "text": round(w_text, 2),
                "crossModal": round(w_cross, 2),
                "spatioTemporal": round(w_st, 2),
            },
            "breakdown": {
                "visual": round(visual_sim * 100.0, 1),
                "color": round(color_sim * 100.0, 1),
                "text": round(text_sim * 100.0, 1),
                "crossModal": round(cross_modal_sim * 100.0, 1),
                "spatioTemporal": round(spatio_temporal_sim * 100.0, 1),
            },
        }
