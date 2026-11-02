import io
import logging
from typing import Dict, Any, List, Optional
import requests
from PIL import Image

from .yolo_cropper import YOLOCropper
from .clip_encoder import MultilingualClipEncoder
from .hsv_extractor import HSVColorExtractor
from .spatio_temporal import SpatioTemporalScorer
from .visual_gating import VisualGatingLayer

logger = logging.getLogger(__name__)

class SmartMatcher:
    def __init__(self):
        self.cropper = YOLOCropper()
        self.clip = MultilingualClipEncoder()
        self.hsv = HSVColorExtractor()
        self.spatio_temporal = SpatioTemporalScorer()
        self.gating = VisualGatingLayer()

    def _fetch_image(self, url: Optional[str]) -> Optional[Image.Image]:
        if not url or not isinstance(url, str) or not url.startswith(("http://", "https://")):
            return None
        try:
            resp = requests.get(url, timeout=8.0)
            if resp.status_code == 200:
                img = Image.open(io.BytesIO(resp.content)).convert("RGB")
                return img
        except Exception as e:
            logger.debug(f"Failed to fetch image: {e}")
        return None

    def match(self, source_item: Dict[str, Any], candidate_items: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        if not candidate_items:
            return []

        # 1. Process Source Item
        src_img_url = source_item.get("imageUrl")
        src_img_raw = self._fetch_image(src_img_url)
        src_has_img = src_img_raw is not None
        src_crop = self.cropper.crop_salient_object(src_img_raw) if src_has_img else None
        src_is_blurry = self.gating.check_image_blur(src_crop) if src_crop else False
        src_vis_vec = self.clip.encode_image(src_crop) if src_crop else None
        src_hsv_hist = self.hsv.extract_histogram(src_crop) if src_crop else None

        src_text = f"{source_item.get('title', '')} {source_item.get('categoryName', '')} {source_item.get('description', '')}"
        src_text_vec = self.clip.encode_text(src_text)

        results = []
        for cand in candidate_items:
            # 2. Process Candidate Item
            cand_img_url = cand.get("imageUrl")
            cand_img_raw = self._fetch_image(cand_img_url)
            cand_has_img = cand_img_raw is not None
            cand_crop = self.cropper.crop_salient_object(cand_img_raw) if cand_has_img else None
            cand_is_blurry = self.gating.check_image_blur(cand_crop) if cand_crop else False
            cand_vis_vec = self.clip.encode_image(cand_crop) if cand_crop else None
            cand_hsv_hist = self.hsv.extract_histogram(cand_crop) if cand_crop else None

            cand_text = f"{cand.get('title', '')} {cand.get('categoryName', '')} {cand.get('description', '')}"
            cand_text_vec = self.clip.encode_text(cand_text)

            # 3. Compute Modality Similarities
            # Visual similarity
            if src_vis_vec is not None and cand_vis_vec is not None:
                vis_sim = self.clip.cosine_similarity(src_vis_vec, cand_vis_vec)
            elif src_vis_vec is not None and cand_text_vec is not None:
                vis_sim = self.clip.cosine_similarity(src_vis_vec, cand_text_vec)
            elif src_text_vec is not None and cand_vis_vec is not None:
                vis_sim = self.clip.cosine_similarity(src_text_vec, cand_vis_vec)
            else:
                vis_sim = 0.40

            # Text similarity (CLIP Multilingual semantic embedding)
            if src_text_vec is not None and cand_text_vec is not None:
                text_sim = self.clip.cosine_similarity(src_text_vec, cand_text_vec)
            else:
                w1 = set(src_text.lower().split())
                w2 = set(cand_text.lower().split())
                overlap = len(w1.intersection(w2))
                text_sim = min(1.0, overlap / max(1, min(len(w1), len(w2))))

            # Color similarity (HSV)
            if src_has_img and cand_has_img:
                color_sim = self.hsv.compare_histograms(src_hsv_hist, cand_hsv_hist)
            else:
                color_sim = 0.50

            # Spatio-temporal similarity
            st_sim = self.spatio_temporal.compute(
                source_item.get("eventAt"),
                cand.get("eventAt"),
                source_item.get("locationText"),
                cand.get("locationText")
            )

            # 4. Multimodal Gating and Fusion
            gated = self.gating.gate_and_fuse(
                has_source_img=src_has_img,
                has_cand_img=cand_has_img,
                is_source_blurry=src_is_blurry,
                is_cand_blurry=cand_is_blurry,
                visual_sim=vis_sim,
                text_sim=text_sim,
                color_sim=color_sim,
                spatio_temporal_sim=st_sim
            )

            # 5. Nếu bị loại bởi Hard Cutoff (ví dụ ảnh dưới 55%) -> Bỏ qua không gợi ý
            if gated.get("isRejected", False):
                continue

            # 6. Formulate human-readable reason
            reasons = []
            if gated["breakdown"]["visual"] >= 75 and src_has_img and cand_has_img:
                reasons.append("Đặc điểm hình ảnh rất tương đồng")
            if gated["breakdown"]["color"] >= 75 and src_has_img and cand_has_img:
                reasons.append("Trùng khớp màu sắc")
            if gated["breakdown"]["spatioTemporal"] >= 70:
                reasons.append("Thời gian & địa điểm gần nhau")
            if gated["breakdown"]["text"] >= 70:
                reasons.append("Mô tả khớp")

            reason_str = " • ".join(reasons) if reasons else "Có một số đặc điểm tương đồng"

            results.append({
                **cand,
                "matchScore": gated["matchScore"],
                "matchBreakdown": gated["breakdown"],
                "matchWeights": gated["weights"],
                "matchReason": reason_str
            })

        # Sort descending by matchScore
        results.sort(key=lambda x: x["matchScore"], reverse=True)
        return results
