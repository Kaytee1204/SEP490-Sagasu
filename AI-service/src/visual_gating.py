from typing import Dict, Any

class VisualGatingLayer:
    """
    Adaptive multimodal gating mechanism.
    Dynamically modulates the weights between Visual, Textual, Color,
    and Spatio-temporal modalities based on data availability and confidence.
    """
    def __init__(self):
        pass

    def gate_and_fuse(
        self,
        has_source_img: bool,
        has_cand_img: bool,
        visual_sim: float,
        text_sim: float,
        color_sim: float,
        spatio_temporal_sim: float
    ) -> Dict[str, Any]:
        has_both_images = has_source_img and has_cand_img

        if has_both_images:
            # Full multimodal regime: Visual + HSV + Text + Spatio-Temporal
            w_vis = 0.40
            w_color = 0.20
            w_text = 0.25
            w_st = 0.15
        elif has_source_img or has_cand_img:
            # Cross-modal regime (One image, one text description):
            # Text similarity between image embedding and description
            w_vis = 0.25
            w_color = 0.00
            w_text = 0.50
            w_st = 0.25
        else:
            # Pure text & spatio-temporal regime
            w_vis = 0.00
            w_color = 0.00
            w_text = 0.65
            w_st = 0.35

        # Normalize weights to sum to 1.0
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

        # Scale to 0-100% with standard clipping
        final_percentage = round(min(99.0, max(5.0, composite_score * 100.0)), 1)

        return {
            "matchScore": final_percentage,
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
