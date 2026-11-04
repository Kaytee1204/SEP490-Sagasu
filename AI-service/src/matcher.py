import io
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
import numpy as np
import requests
from PIL import Image

from .yolo_cropper import YOLOCropper
from .clip_encoder import MultilingualClipEncoder
from .hsv_extractor import HSVColorExtractor
from .spatio_temporal import SpatioTemporalScorer
from .visual_gating import VisualGatingLayer
from .vector_store import PersistentVectorStore

logger = logging.getLogger(__name__)


class SmartMatcher:
    def __init__(self):
        self.cropper = YOLOCropper()
        self.clip = MultilingualClipEncoder()
        self.hsv = HSVColorExtractor()
        self.spatio_temporal = SpatioTemporalScorer()
        self.gating = VisualGatingLayer()
        self.store = PersistentVectorStore(expected_hsv_dim=self.hsv.total_dim)

    @staticmethod
    def _canonical_url(url: Optional[str]) -> Optional[str]:
        if not url or not isinstance(url, str):
            return None
        return url.split("?", 1)[0]

    def _fetch_image(self, url: Optional[str]) -> Optional[Image.Image]:
        if not url or not isinstance(url, str):
            return None

        clean_path = url.replace("file:///", "").replace("file://", "")
        p = Path(clean_path)
        if p.exists() and p.is_file():
            try:
                return Image.open(p).convert("RGB")
            except Exception as e:
                logger.debug(f"Failed to open local image {p}: {e}")
                return None

        if not url.startswith(("http://", "https://")):
            return None
        try:
            resp = requests.get(url, timeout=5.0)
            if resp.status_code == 200:
                return Image.open(io.BytesIO(resp.content)).convert("RGB")
        except Exception as e:
            logger.debug(f"Failed to fetch image: {e}")
        return None

    def _ensure_visual_features(self, urls: List[Optional[str]], crop_first: bool = True) -> None:
        missing_keys: List[str] = []
        missing_urls: List[str] = []
        seen = set()

        for u in urls:
            key = self._canonical_url(u)
            if key and key not in self.store.visual_cache and key not in seen:
                seen.add(key)
                missing_keys.append(key)
                missing_urls.append(u)

        if not missing_urls:
            return

        with ThreadPoolExecutor(max_workers=min(16, len(missing_urls))) as pool:
            fetched_imgs = list(pool.map(self._fetch_image, missing_urls))

        valid_indices: List[int] = []
        crops_to_encode: List[Image.Image] = []
        blur_flags: List[bool] = []
        hsv_hists: List[np.ndarray] = []

        for idx, img_raw in enumerate(fetched_imgs):
            if img_raw is None:
                continue
            crop = self.cropper.crop_salient_object(img_raw) if (crop_first and idx == 0) else img_raw
            is_blurry = self.gating.check_image_blur(crop) if crop else False
            hsv_hist = self.hsv.extract_histogram(crop) if crop else np.zeros((self.hsv.total_dim,), dtype=np.float32)

            valid_indices.append(idx)
            crops_to_encode.append(crop)
            blur_flags.append(is_blurry)
            hsv_hists.append(hsv_hist)

        if crops_to_encode:
            vis_vecs = self.clip.encode_images_batch(crops_to_encode)
            to_persist: List[Tuple[str, bool, np.ndarray, np.ndarray]] = []
            for i, orig_idx in enumerate(valid_indices):
                key = missing_keys[orig_idx]
                vec = vis_vecs[i] if i < len(vis_vecs) else None
                if vec is not None:
                    to_persist.append((key, blur_flags[i], vec, hsv_hists[i]))
            if to_persist:
                self.store.put_visual_batch(to_persist)

    def _ensure_text_features(self, texts: List[str]) -> None:
        missing: List[str] = []
        seen = set()
        for t in texts:
            norm = (t or "").strip()
            if not norm:
                continue
            thash = self.store.hash_text(norm)
            if thash not in self.store.text_cache and norm not in seen:
                seen.add(norm)
                missing.append(norm)

        if not missing:
            return

        vecs = self.clip.encode_texts_batch(missing)
        to_persist: List[Tuple[str, np.ndarray]] = []
        for norm, vec in zip(missing, vecs):
            if vec is not None:
                to_persist.append((norm, vec))
        if to_persist:
            self.store.put_text_batch(to_persist)

    def _get_visual(self, url: Optional[str]) -> Tuple[bool, bool, Optional[np.ndarray], Optional[np.ndarray]]:
        key = self._canonical_url(url)
        val = self.store.get_visual(key)
        if val is not None:
            is_blurry, vis_vec, hsv_hist = val
            return True, is_blurry, vis_vec, hsv_hist
        return False, False, None, None

    def match(
        self,
        source_item: Dict[str, Any],
        candidate_items: List[Dict[str, Any]],
        include_rejected: bool = False
    ) -> List[Dict[str, Any]]:
        t0 = time.perf_counter()
        self.store.total_requests += 1

        if not candidate_items:
            return []

        src_url = source_item.get("imageUrl")
        all_urls = [src_url] + [c.get("imageUrl") for c in candidate_items]

        src_text = f"{source_item.get('title', '')} {source_item.get('categoryName', '')} {source_item.get('description', '')}"
        all_texts = [src_text] + [
            f"{c.get('title', '')} {c.get('categoryName', '')} {c.get('description', '')}"
            for c in candidate_items
        ]

        # Ensure all visual & text embeddings are present in PersistentVectorStore
        self._ensure_visual_features(all_urls, crop_first=True)
        self._ensure_text_features(all_texts)

        src_has_img, src_is_blurry, src_vis_vec, src_hsv_hist = self._get_visual(src_url)
        src_text_vec = self.store.get_text(src_text)
        src_cat = (source_item.get("categoryName") or "").strip().lower()

        results = []
        for idx, cand in enumerate(candidate_items):
            cand_img_url = cand.get("imageUrl")
            cand_has_img, cand_is_blurry, cand_vis_vec, cand_hsv_hist = self._get_visual(cand_img_url)
            cand_text = all_texts[idx + 1]
            cand_text_vec = self.store.get_text(cand_text)
            cand_cat = (cand.get("categoryName") or "").strip().lower()
            same_cat = bool(src_cat and cand_cat and src_cat == cand_cat)

            # 3. Compute Modality Similarities
            if src_vis_vec is not None and cand_vis_vec is not None:
                raw_vis = self.clip.cosine_similarity(src_vis_vec, cand_vis_vec)
                if raw_vis >= 0.42:
                    vis_sim = min(0.99, 0.55 + (raw_vis - 0.42) * 0.75 + (0.05 if same_cat else 0.0))
                else:
                    vis_sim = raw_vis + (0.08 if same_cat else 0.0)
            elif src_vis_vec is not None and cand_text_vec is not None:
                vis_sim = self.clip.cosine_similarity(src_vis_vec, cand_text_vec)
            elif src_text_vec is not None and cand_vis_vec is not None:
                vis_sim = self.clip.cosine_similarity(src_text_vec, cand_vis_vec)
            else:
                vis_sim = 0.40

            if src_text_vec is not None and cand_text_vec is not None:
                text_sim = self.clip.cosine_similarity(src_text_vec, cand_text_vec)
            else:
                w1 = set(src_text.lower().split())
                w2 = set(cand_text.lower().split())
                overlap = len(w1.intersection(w2))
                text_sim = min(1.0, overlap / max(1, min(len(w1), len(w2))))

            if src_has_img and cand_has_img:
                color_sim = self.hsv.compare_histograms(src_hsv_hist, cand_hsv_hist)
            else:
                color_sim = 0.50

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

            is_rejected = gated.get("isRejected", False)

            if is_rejected and not include_rejected:
                continue

            reasons = []
            if is_rejected:
                reasons.append(f"Bị loại: {gated.get('rejectReason', 'Visual < 55%')}")
            else:
                if same_cat:
                    reasons.append(f"Cùng danh mục {cand.get('categoryName')}")
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
                "isRejected": is_rejected,
                "matchScore": gated["matchScore"],
                "matchBreakdown": gated["breakdown"],
                "matchWeights": gated["weights"],
                "matchReason": reason_str
            })

        results.sort(key=lambda x: (not x.get("isRejected", False), x["matchScore"]), reverse=True)
        self.store.last_latency_ms = (time.perf_counter() - t0) * 1000.0
        return results
