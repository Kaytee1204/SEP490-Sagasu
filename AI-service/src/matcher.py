import io
import logging
import re
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Dict, Any, List, Optional, Set, Tuple
import numpy as np
import requests
from PIL import Image

from .yolo_cropper import YOLOCropper
from .clip_encoder import MultilingualClipEncoder
from .hsv_extractor import HSVColorExtractor
from .spatio_temporal import SpatioTemporalScorer
from .visual_gating import VisualGatingLayer
from .vector_store import PersistentVectorStore
from .vector_index import MultimodalVectorIndex

logger = logging.getLogger(__name__)

# Tập từ đệm / câu mẫu phổ biến trong bài báo mất & nhặt đồ cần lọc bỏ trước khi tính độ trùng từ khóa đồ vật
VI_BOILERPLATE_STOPWORDS: Set[str] = {
    "tìm", "mất", "rơi", "quên", "thất", "lạc", "nhặt", "được", "thấy", "gặp",
    "mình", "tôi", "em", "anh", "chị", "bạn", "ai", "người", "nào",
    "bị", "có", "là", "và", "với", "của", "cho", "từ", "đến", "ở", "tại", "trong", "ngoài", "trên", "dưới",
    "một", "hai", "ba", "các", "những", "chiếc", "cái", "bộ", "đôi", "số",
    "khu", "vực", "đại", "học", "trường", "fpt", "sakura", "tòa", "nhà", "phòng", "tầng",
    "xin", "vui", "lòng", "liên", "hệ", "cảm", "ơn", "trả", "lại", "giúp", "nhé", "nha", "ạ",
    "đồ", "vật", "dùng", "cá", "nhân", "bài", "đăng", "màu", "loại", "hiệu", "khoảng", "lúc", "ngày",
    "căng", "tin", "canteen", "thư", "viện", "alpha", "beta", "gamma", "delta", "lab", "sảnh",
}

# Các nhóm đồng nghĩa cốt lõi của đồ vật thất lạc (để "bóp" khớp "ví", "airpods" khớp "tai nghe", "ba lô" khớp "balo"...)
ITEM_SYNONYM_GROUPS: List[Tuple[str, Set[str]]] = [
    ("__wallet__", {"ví", "bóp", "wallet"}),
    ("__backpack__", {"balo", "ba lô", "túi xách", "túi đeo", "cặp sách", "backpack"}),
    ("__earphone__", {"tai nghe", "airpod", "airpods", "earbuds", "headphone", "bluetooth"}),
    ("__phone__", {"điện thoại", "iphone", "samsung", "xiaomi", "oppo", "smartphone", "phone"}),
    ("__laptop__", {"laptop", "macbook", "máy tính xách tay", "thinkpad", "asus", "dell"}),
    ("__charger__", {"sạc", "adapter", "dây sạc", "củ sạc", "type-c", "charger"}),
    ("__glasses__", {"kính", "mắt kính", "kính cận", "kính râm", "glasses"}),
    ("__key__", {"chìa khóa", "chìa khoá", "khóa xe", "khoá xe", "key", "keys"}),
    ("__card__", {"thẻ sinh viên", "cccd", "cmnd", "căn cước", "giấy tờ", "thẻ atm", "thẻ xe", "bằng lái"}),
    ("__bottle__", {"bình nước", "bình giữ nhiệt", "ly giữ nhiệt", "cốc giữ nhiệt", "bottle"}),
    ("__watch__", {"đồng hồ", "smartwatch", "apple watch", "casio", "watch"}),
    ("__helmet__", {"mũ bảo hiểm", "nón bảo hiểm", "helmet"}),
    ("__umbrella__", {"ô dù", "cây dù", "cây ô", "umbrella"}),
    ("__book__", {"sách", "giáo trình", "vở", "sổ tay", "notebook"}),
    ("__mouse__", {"chuột", "mouse", "logitech"}),
    ("__calculator__", {"máy tính cầm tay", "máy tính bỏ túi", "casio fx", "calculator"}),
]


class SmartMatcher:
    """
    Bộ điều phối trung tâm của thuật toán AI Smart Match (Kiến trúc 3 lớp - Không giới hạn Category):
    - Lớp 1 (Async Indexing)       : `index_posts()` / `remove_post()` mã hóa sẵn vector & lưu kho chỉ mục.
    - Lớp 2 (Multi-Channel Recall) : `MultimodalVectorIndex.recall_candidates()` quét toàn bộ hàng chục nghìn
                                     bài bằng ma trận BLAS/SIMD qua 3 kênh (Ảnh-Ảnh, Mô tả-Mô tả, Ảnh-Mô tả).
    - Lớp 3 (Fine Re-ranking)      : `match()` chấm điểm tinh bằng HSV 176-dim + Spatio-Temporal + Visual Gating.
    """

    def __init__(self):
        self.cropper = YOLOCropper()
        self.clip = MultilingualClipEncoder()
        self.hsv = HSVColorExtractor()
        self.spatio_temporal = SpatioTemporalScorer()
        self.gating = VisualGatingLayer()
        self.store = PersistentVectorStore(expected_hsv_dim=self.hsv.total_dim)
        self.index = MultimodalVectorIndex(store=self.store)
        # RAM cache cho vector mô tả từ Text Encoder của chính ViT-B/16 (dùng cho so sánh Ảnh <-> Mô tả cùng hệ trục)
        self._cross_text_cache: Dict[str, np.ndarray] = {}

    @staticmethod
    def _canonical_url(url: Optional[str]) -> Optional[str]:
        """Loại bỏ query string (?token=..., ?X-Amz-...) để dùng URL sạch làm khóa cache vector."""
        if not url or not isinstance(url, str):
            return None
        cleaned = url.strip()
        if not cleaned:
            return None
        return cleaned.split("?", 1)[0]

    @staticmethod
    def _extract_image_urls(item: Dict[str, Any]) -> List[str]:
        """
        Thu thập TOÀN BỘ danh sách URL ảnh của 1 bài đăng (từ mảng `imageUrls` và trường đơn `imageUrl`),
        loại trùng lặp theo canonical URL để so sánh mọi ảnh của bài gốc với mọi ảnh của bài ứng viên.
        """
        urls: List[str] = []
        seen_keys = set()

        raw_list = item.get("imageUrls")
        if isinstance(raw_list, list):
            for u in raw_list:
                if isinstance(u, str) and u.strip():
                    key = SmartMatcher._canonical_url(u)
                    if key and key not in seen_keys:
                        seen_keys.add(key)
                        urls.append(u.strip())

        single = item.get("imageUrl")
        if isinstance(single, str) and single.strip():
            key = SmartMatcher._canonical_url(single)
            if key and key not in seen_keys:
                seen_keys.add(key)
                urls.append(single.strip())

        return urls

    @staticmethod
    def _is_same_author(source_item: Dict[str, Any], cand: Dict[str, Any]) -> bool:
        """
        Kiểm tra 2 bài đăng có cùng một người đăng hay không.
        Ưu tiên so sánh `authorId`; nếu thiếu `authorId` thì fallback so sánh `authorName` (không phân biệt hoa thường).
        Trả về True -> BỎ QUA ứng viên, tuyệt đối không so sánh đồ do cùng 1 người đăng.
        """
        src_author_id = source_item.get("authorId")
        cand_author_id = cand.get("authorId")
        if src_author_id is not None and cand_author_id is not None:
            return str(src_author_id).strip() == str(cand_author_id).strip()

        src_author_name = (source_item.get("authorName") or "").strip().lower()
        cand_author_name = (cand.get("authorName") or "").strip().lower()
        if src_author_name and cand_author_name:
            return src_author_name == cand_author_name

        return False

    @staticmethod
    def _is_opposite_post_type(src_type: Optional[str], cand_type: Optional[str]) -> bool:
        """
        Đảm bảo chỉ so sánh chéo giữa bài Mất đồ (LOST) và bài Tìm/Nhặt được (FOUND).
        - Nếu cả 2 cùng là LOST hoặc cùng là FOUND -> trả về False (bỏ qua).
        - Chỉ trả về True khi 1 bên là LOST và bên kia là FOUND.
        """
        s = (src_type or "").strip().upper()
        c = (cand_type or "").strip().upper()
        if not s or not c:
            return True
        return (s == "LOST" and c == "FOUND") or (s == "FOUND" and c == "LOST")

    @staticmethod
    def _calibrate_visual_cosine(raw_vis: float) -> float:
        """
        Hiệu chuẩn điểm Cosine thô giữa 2 ảnh của CLIP ViT-B/16 dựa trên phân bố thực tế của tập ảnh đồ thất lạc:
        - Đo thực nghiệm trên dữ liệu thật:
          + Median (50% các cặp ngẫu nhiên khác đồ): raw_vis ~= 0.668
          + Top 10% (cùng loại đồ vật): raw_vis >= 0.808
          + Top 5%  (rất giống / cùng món đồ): raw_vis >= 0.840
          + Top 1%  (gần như giống hệt): raw_vis >= 0.887
        - Công thức ánh xạ sắc nét về [0.0 .. 0.99]:
          + Nếu raw_vis >= 0.68: min(0.99, 0.45 + ((raw_vis - 0.68) / 0.24) * 0.54)
            (vd: 0.68 -> 45.0%, 0.74 -> 58.5%, 0.80 -> 72.0%, 0.86 -> 85.5%, 0.92 -> 99.0%)
          + Nếu raw_vis < 0.68 : (raw_vis / 0.68) * 0.45 (luôn < 45%, bị loại nếu mô tả cũng không khớp)
        """
        if raw_vis >= 0.68:
            return float(min(0.99, 0.45 + ((raw_vis - 0.68) / 0.24) * 0.54))
        return float(max(0.0, (raw_vis / 0.68) * 0.45))

    @staticmethod
    def _extract_item_keywords(text: str) -> Set[str]:
        """Tách các từ khóa cốt lõi + khái niệm đồng nghĩa của món đồ (loại bỏ câu mẫu & địa điểm thừa)."""
        if not text:
            return set()
        lowered = re.sub(r"#\d+", " ", text.lower())
        tokens = re.findall(r"[^\W\d_]{2,}|[a-z0-9]{2,}", lowered, flags=re.UNICODE)
        kws = {t for t in tokens if t not in VI_BOILERPLATE_STOPWORDS and not t.isdigit()}
        # Chuẩn hóa thêm nhóm khái niệm đồng nghĩa (vd: "ba lô" / "balo" / "túi xách" -> "__backpack__")
        for canonical, phrases in ITEM_SYNONYM_GROUPS:
            if any(p in lowered for p in phrases):
                kws.add(canonical)
        return kws

    @staticmethod
    def _extract_item_concepts(text: str) -> Set[str]:
        """Trích xuất tập loại vật thể cốt lõi (vd: __wallet__, __backpack__, __earphone__...)."""
        if not text:
            return set()
        lowered = text.lower()
        return {canonical for canonical, phrases in ITEM_SYNONYM_GROUPS if any(p in lowered for p in phrases)}

    @staticmethod
    def _compute_hybrid_text_similarity(
        src_title: str,
        src_text: str,
        cand_title: str,
        cand_text: str,
        raw_sem_cosine: Optional[float],
    ) -> float:
        """
        Tính độ tương đồng Mô tả <-> Mô tả bằng kiến trúc Hybrid 3 tầng:
        1. `concept_sim` (Khớp loại vật thể cốt lõi trên Tiêu đề & Mô tả):
           - Nếu 2 bài nhắc cùng 1 vật thể cốt lõi (vd cùng là 'balo', cùng là 'ví/bóp', cùng là 'tai nghe')
             -> concept_sim = 0.85..1.0!
           - Nếu 2 bài nhắc 2 vật thể HOÀN TOÀN KHÁC NHAU (vd 'balo' vs 'ví', 'tai nghe' vs 'kính mắt')
             -> concept_sim = 0.0 (và giảm mạnh điểm text để không ghép nhầm khác vật!).
        2. `kw_sim` (Độ trùng từ khóa chi tiết trên tiêu đề + mô tả, không bị pha loãng bởi câu dài).
        3. `sem_sim` (Ngữ nghĩa Multilingual CLIP đã co giãn tương phản từ [0.78 .. 0.98] -> [0.0 .. 1.0]).
        """
        c1_title = SmartMatcher._extract_item_concepts(src_title)
        c2_title = SmartMatcher._extract_item_concepts(cand_title)
        c1_all = c1_title | SmartMatcher._extract_item_concepts(src_text)
        c2_all = c2_title | SmartMatcher._extract_item_concepts(cand_text)

        title_concept_overlap = len(c1_title.intersection(c2_title))
        all_concept_overlap = len(c1_all.intersection(c2_all))

        k1 = SmartMatcher._extract_item_keywords(src_text)
        k2 = SmartMatcher._extract_item_keywords(cand_text)
        if k1 and k2:
            overlap = len(k1.intersection(k2))
            cover = overlap / min(len(k1), len(k2))
            dice = (2.0 * overlap) / (len(k1) + len(k2))
            kw_sim = min(1.0, 0.65 * cover + 0.35 * dice)
        else:
            kw_sim = 0.0

        sem_sim = (
            max(0.0, min(1.0, (raw_sem_cosine - 0.78) / 0.20))
            if raw_sem_cosine is not None
            else kw_sim
        )

        # Trường hợp 1: Khớp chính xác vật thể cốt lõi ngay trên tiêu đề (vd: Balo <-> Balo, Ví <-> Ví)
        if title_concept_overlap > 0:
            return float(min(0.99, 0.60 + 0.25 * kw_sim + 0.15 * sem_sim))

        # Trường hợp 2: Khớp vật thể cốt lõi trong phần mô tả (vd: Balo đựng sạc laptop <-> Tìm sạc laptop)
        if all_concept_overlap > 0:
            return float(min(0.95, 0.45 + 0.30 * kw_sim + 0.20 * sem_sim))

        # Trường hợp 3: Cả 2 tiêu đề đều nêu rõ 2 loại vật thể KHÁC HẲN NHAU (vd: Balo vs Ví, Tai nghe vs Kính mắt)
        if c1_title and c2_title and all_concept_overlap == 0:
            return float(min(0.20, 0.15 * sem_sim + 0.10 * kw_sim))

        # Trường hợp 4: 1 trong 2 bài mô tả chung chung (vd: "Tìm đồ dùng cá nhân thất lạc") -> dùng hybrid chuẩn
        return float(max(0.0, min(0.95, 0.55 * kw_sim + 0.45 * sem_sim)))

    @staticmethod
    def _calibrate_cross_modal_cosine(raw_cross: float) -> float:
        """
        Hiệu chuẩn điểm Cosine chéo giữa Ảnh và Mô tả (Image <-> Text) trong CÙNG không gian CLIP ViT-B/16.
        Theo bài báo gốc CLIP (Radford et al., 2021), Cosine thô giữa Ảnh và Chữ của ViT-B/16 dao động
        quanh [0.18, 0.34] (ảnh và chữ khớp đúng vật thể thường đạt 0.25 - 0.33).
        Công thức co giãn tuyến tính về [0.0, 0.98]:
            S_cross = clip((raw_cross - 0.18) / (0.34 - 0.18), 0.0, 0.98)
        """
        if raw_cross <= 0.18:
            return 0.0
        scaled = (raw_cross - 0.18) / (0.34 - 0.18)
        return float(max(0.0, min(0.98, scaled)))

    def _fetch_image(self, url: Optional[str]) -> Optional[Image.Image]:
        """Tải ảnh từ ổ cứng local (file://) hoặc qua HTTP/HTTPS (timeout 5s) và chuyển sang hệ màu RGB."""
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

    def _ensure_visual_features(self, urls: List[Optional[str]], crop_all: bool = True) -> None:
        """
        Đảm bảo mọi URL ảnh đều đã có vector đặc trưng trong PersistentVectorStore (L1 RAM + L2 SQLite):
        - Chỉ tải và chạy AI cho các ảnh CHƯA có trong cache.
        - Tải ảnh song song bằng ThreadPoolExecutor (tối đa 16 luồng).
        - Chạy YOLOv8 cắt vật thể chính -> kiểm tra độ mờ -> trích xuất HSV 176 chiều -> batch-encode CLIP 512 chiều.
        """
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
        raw_valid_imgs: List[Image.Image] = []
        for idx, img_raw in enumerate(fetched_imgs):
            if img_raw is not None:
                valid_indices.append(idx)
                raw_valid_imgs.append(img_raw)

        if not raw_valid_imgs:
            return

        # Cắt vật thể chính hàng loạt theo lô bằng YOLOv8n (imgsz=384, resize<=640px) để tăng tốc 3-4x trên CPU
        crops_to_encode: List[Image.Image] = (
            self.cropper.crop_salient_objects_batch(raw_valid_imgs) if crop_all else raw_valid_imgs
        )
        blur_flags: List[bool] = [self.gating.check_image_blur(crop) if crop else False for crop in crops_to_encode]
        hsv_hists: List[np.ndarray] = [
            self.hsv.extract_histogram(crop) if crop else np.zeros((self.hsv.total_dim,), dtype=np.float32)
            for crop in crops_to_encode
        ]

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
        """
        Đảm bảo mọi chuỗi mô tả (title + description) đều đã có vector 512 chiều trong cache.
        Những chuỗi chưa có trong cache sẽ được batch-encode 1 lần bằng Multilingual CLIP Text.
        """
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

    def _ensure_cross_text_features(self, texts: List[str]) -> None:
        """
        Mã hóa hàng loạt các mô tả bằng Text Encoder tích hợp sẵn của CLIP ViT-B/16 và cache trong RAM.
        Nhờ đó, mọi so sánh Ảnh <-> Mô tả đều nằm trong CÙNG 100% không gian 512 chiều của ViT-B/16.
        """
        missing: List[str] = []
        missing_hashes: List[str] = []
        seen = set()
        for t in texts:
            norm = (t or "").strip()
            if not norm:
                continue
            thash = self.store.hash_text(norm)
            if thash not in self._cross_text_cache and thash not in seen:
                seen.add(thash)
                missing.append(norm)
                missing_hashes.append(thash)

        if not missing:
            return

        vecs = self.clip.encode_cross_modal_texts_batch(missing)
        for thash, vec in zip(missing_hashes, vecs):
            if vec is not None:
                self._cross_text_cache[thash] = vec

    def _get_cross_text(self, text: str) -> Optional[np.ndarray]:
        norm = (text or "").strip()
        if not norm:
            return None
        return self._cross_text_cache.get(self.store.hash_text(norm))

    def _get_visual_list(self, urls: List[str]) -> List[Tuple[bool, np.ndarray, np.ndarray]]:
        """Lấy toàn bộ danh sách đặc trưng (is_blurry, vis_vec_512, hsv_hist_176) của các ảnh thuộc 1 bài đăng."""
        feats: List[Tuple[bool, np.ndarray, np.ndarray]] = []
        for u in urls:
            key = self._canonical_url(u)
            val = self.store.get_visual(key)
            if val is not None:
                feats.append(val)
        return feats

    def match(
        self,
        source_item: Dict[str, Any],
        candidate_items: List[Dict[str, Any]],
        include_rejected: bool = False,
        top_n: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        Hàm đối sánh chính: nhận 1 bài gốc (source_item) và danh sách bài ứng viên (candidate_items),
        trả về danh sách ứng viên đạt chuẩn đã tính điểm % trùng khớp (matchScore) và xếp hạng giảm dần.
        """
        t0 = time.perf_counter()
        self.store.total_requests += 1

        if not candidate_items:
            return []

        src_id = source_item.get("id")
        src_type = source_item.get("postType")

        # ── BƯỚC 1: LỌC CỨNG CÁC QUY TẮC NGHIỆP VỤ ──
        # 1) Không so sánh bài với chính nó.
        # 2) Chỉ so sánh chéo bài Tìm đồ (FOUND) với bài Mất đồ (LOST).
        # 3) Tuyệt đối không so sánh đồ do cùng 1 người đăng.
        # 4) KHÔNG lọc theo categoryName -> so sánh toàn bộ đồ vật ở tất cả các danh mục.
        eligible_candidates: List[Dict[str, Any]] = []
        for cand in candidate_items:
            if src_id is not None and cand.get("id") is not None and str(src_id) == str(cand.get("id")):
                continue
            if not self._is_opposite_post_type(src_type, cand.get("postType")):
                continue
            if self._is_same_author(source_item, cand):
                continue
            eligible_candidates.append(cand)

        if not eligible_candidates:
            self.store.last_latency_ms = (time.perf_counter() - t0) * 1000.0
            return []

        # ── BƯỚC 2: GOM TOÀN BỘ ẢNH VÀ MÔ TẢ ĐỂ TRÍCH XUẤT / NẠP TỪ CACHE ──
        src_urls = self._extract_image_urls(source_item)
        cand_urls_list = [self._extract_image_urls(c) for c in eligible_candidates]

        all_urls: List[str] = list(src_urls)
        for ulist in cand_urls_list:
            all_urls.extend(ulist)

        # Chỉ dùng Tiêu đề + Mô tả (không nhét categoryName vào làm lệch điểm giữa các danh mục)
        src_text = f"{source_item.get('title', '')} {source_item.get('description', '')}".strip()
        cand_texts = [
            f"{c.get('title', '')} {c.get('description', '')}".strip()
            for c in eligible_candidates
        ]

        self._ensure_visual_features(all_urls, crop_all=True)
        self._ensure_text_features([src_text] + cand_texts)
        self._ensure_cross_text_features([src_text] + cand_texts)

        src_visuals = self._get_visual_list(src_urls)
        src_has_img = len(src_visuals) > 0
        src_text_vec = self.store.get_text(src_text)
        src_cross_text_vec = self._get_cross_text(src_text)

        results = []
        for idx, cand in enumerate(eligible_candidates):
            cand_visuals = self._get_visual_list(cand_urls_list[idx])
            cand_has_img = len(cand_visuals) > 0
            cand_text = cand_texts[idx]
            cand_text_vec = self.store.get_text(cand_text)
            cand_cross_text_vec = self._get_cross_text(cand_text)

            # ── BƯỚC 3A: SO SÁNH ẢNH <-> ẢNH (TOÀN BỘ TẤT CẢ CÁC ẢNH CỦA 2 BÀI VỚI NHAU) ──
            # Nếu bài gốc có M ảnh và ứng viên có N ảnh -> so sánh đủ M x N cặp ảnh,
            # chọn cặp (s*, c*) có độ tương đồng CLIP cao nhất, đồng thời lấy độ khớp màu HSV & cờ mờ của cặp đó.
            vis_sim = 0.0
            color_sim = 0.50
            best_pair_src_blurry = False
            best_pair_cand_blurry = False

            if src_has_img and cand_has_img:
                best_raw_vis = -1.0
                for s_blur, s_vec, s_hsv in src_visuals:
                    for c_blur, c_vec, c_hsv in cand_visuals:
                        raw_v = self.clip.cosine_similarity(s_vec, c_vec)
                        if raw_v > best_raw_vis:
                            best_raw_vis = raw_v
                            best_pair_src_blurry = s_blur
                            best_pair_cand_blurry = c_blur
                            color_sim = self.hsv.compare_histograms(s_hsv, c_hsv)
                vis_sim = self._calibrate_visual_cosine(max(0.0, best_raw_vis))
            else:
                best_pair_src_blurry = all(v[0] for v in src_visuals) if src_visuals else False
                best_pair_cand_blurry = all(v[0] for v in cand_visuals) if cand_visuals else False

            # ── BƯỚC 3B: SO SÁNH MÔ TẢ <-> MÔ TẢ (HYBRID: KHÁI NIỆM ĐỒ VẬT + TỪ KHÓA + M-CLIP) ──
            raw_sem = (
                self.clip.cosine_similarity(src_text_vec, cand_text_vec)
                if (src_text_vec is not None and cand_text_vec is not None)
                else None
            )
            text_sim = self._compute_hybrid_text_similarity(
                source_item.get("title", ""),
                src_text,
                cand.get("title", ""),
                cand_text,
                raw_sem,
            )

            # ── BƯỚC 3C: SO SÁNH ẢNH <-> MÔ TẢ 2 CHIỀU CÙNG KHÔNG GIAN ViT-B/16 (CROSS-MODAL) ──
            # Chiều 1: Mọi ảnh của bài gốc (ViT-B/16) so với Mô tả ứng viên (ViT-B/16 Text Encoder).
            # Chiều 2: Mô tả bài gốc (ViT-B/16 Text Encoder) so với mọi ảnh ứng viên (ViT-B/16).
            cross_scores: List[float] = []
            if src_has_img and cand_cross_text_vec is not None:
                best_s2t = max(self.clip.cosine_similarity(s_vec, cand_cross_text_vec) for _, s_vec, _ in src_visuals)
                cross_scores.append(self._calibrate_cross_modal_cosine(best_s2t))
            if cand_has_img and src_cross_text_vec is not None:
                best_t2c = max(self.clip.cosine_similarity(src_cross_text_vec, c_vec) for _, c_vec, _ in cand_visuals)
                cross_scores.append(self._calibrate_cross_modal_cosine(best_t2c))

            cross_modal_sim = float(sum(cross_scores) / len(cross_scores)) if cross_scores else 0.0

            # ── BƯỚC 3D: SO SÁNH THỜI GIAN & ĐỊA ĐIỂM (SPATIO-TEMPORAL) ──
            st_sim = self.spatio_temporal.compute(
                source_item.get("eventAt"),
                cand.get("eventAt"),
                source_item.get("locationText"),
                cand.get("locationText"),
            )

            # ── BƯỚC 4: HỢP NHẤT ĐA PHƯƠNG THỨC QUA VISUAL GATING LAYER RA SỐ PHẦN TRĂM ──
            gated = self.gating.gate_and_fuse(
                has_source_img=src_has_img,
                has_cand_img=cand_has_img,
                is_source_blurry=best_pair_src_blurry,
                is_cand_blurry=best_pair_cand_blurry,
                visual_sim=vis_sim,
                text_sim=text_sim,
                color_sim=color_sim,
                spatio_temporal_sim=st_sim,
                cross_modal_sim=cross_modal_sim,
            )

            is_rejected = gated.get("isRejected", False)
            if is_rejected and not include_rejected:
                continue

            # ── BƯỚC 5: SINH LÝ DO TRÙNG KHỚP DỄ HIỂU CHO NGƯỜI DÙNG ──
            reasons = []
            if is_rejected:
                reasons.append(f"Bị loại: {gated.get('rejectReason', 'Không đạt ngưỡng')}")
            else:
                if gated["breakdown"]["visual"] >= 75 and src_has_img and cand_has_img:
                    reasons.append("Hình ảnh rất tương đồng")
                if gated["breakdown"]["color"] >= 75 and src_has_img and cand_has_img:
                    reasons.append("Trùng khớp màu sắc")
                if gated["breakdown"]["crossModal"] >= 65 and (src_has_img or cand_has_img):
                    reasons.append("Ảnh khớp với mô tả")
                if gated["breakdown"]["text"] >= 70:
                    reasons.append("Mô tả rất khớp")
                if gated["breakdown"]["spatioTemporal"] >= 70:
                    reasons.append("Thời gian & địa điểm gần nhau")

            reason_str = " • ".join(reasons) if reasons else "Có nhiều đặc điểm tương đồng"

            results.append({
                **cand,
                "isRejected": is_rejected,
                "matchScore": gated["matchScore"],
                "matchBreakdown": gated["breakdown"],
                "matchWeights": gated["weights"],
                "matchReason": reason_str,
            })

        # Sắp xếp danh sách kết quả theo matchScore giảm dần và chỉ giữ tối đa Top N (mặc định Top 10)
        results.sort(key=lambda x: (not x.get("isRejected", False), x["matchScore"]), reverse=True)
        if top_n and top_n > 0:
            results = results[:top_n]
        self.store.last_latency_ms = (time.perf_counter() - t0) * 1000.0
        return results

    def index_posts(self, items: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        LỚP 1 (Async Indexing):
        Nhận 1 bài đăng mới (hoặc 1 lô nhiều bài khi đồng bộ DB), trích xuất sẵn toàn bộ vector:
        - Toàn bộ ảnh -> YOLOv8 cắt vật thể -> CLIP ViT-B/16 (512d) + HSV (176d) + cờ mờ.
        - Tiêu đề + Mô tả -> Multilingual CLIP (512d).
        Lưu vào PersistentVectorStore + MultimodalVectorIndex để lúc tìm kiếm không phải chạy lại AI.
        """
        t0 = time.perf_counter()
        if not items:
            return {"indexedCount": 0, "totalIndexedPosts": len(self.index.posts), "latencyMs": 0.0}

        all_urls: List[str] = []
        all_texts: List[str] = []
        for item in items:
            status = (item.get("status") or "OPEN").strip().upper()
            if status != "OPEN":
                continue
            all_urls.extend(self._extract_image_urls(item))
            text_str = f"{item.get('title', '')} {item.get('description', '')}".strip()
            if text_str:
                all_texts.append(text_str)

        # Trích xuất & lưu cache cho ảnh/chữ chưa có trong vector store
        self._ensure_visual_features(all_urls, crop_all=True)
        self._ensure_text_features(all_texts)

        # Cập nhật metadata bài đăng vào MultimodalVectorIndex
        indexed_count = self.index.upsert_posts(items)
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        return {
            "indexedCount": indexed_count,
            "totalIndexedPosts": len(self.index.posts),
            "latencyMs": round(elapsed_ms, 1),
        }

    def remove_post(self, post_id: Any) -> Dict[str, Any]:
        """LỚP 1: Xóa bài đăng đã đóng / trả đồ / bị xóa khỏi Vector Index."""
        removed = self.index.delete_post(post_id)
        return {
            "removed": removed,
            "postId": post_id,
            "totalIndexedPosts": len(self.index.posts),
        }

    def search_indexed(
        self,
        source_item: Dict[str, Any],
        top_k_per_channel: int = 40,
        top_n: int = 10,
        include_rejected: bool = False,
    ) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
        """
        TÌM KIẾM 2 TẦNG TRÊN TOÀN BỘ KHO CHỈ MỤC (Hỗ trợ 50.000+ bài với độ trễ < 50ms):
        - Bước 1: Đảm bảo bài gốc (source_item) đã có vector ảnh & mô tả trong cache.
        - Bước 2 (Lớp 2 - Multi-Channel Recall): Quét TOÀN BỘ kho chỉ mục qua 3 kênh song song
                  (Ảnh-Ảnh, Mô tả-Mô tả, Ảnh-Mô tả 2 chiều), lọc cứng khác người đăng & ngược loại LOST <-> FOUND,
                  KHÔNG giới hạn category -> rút gọn hàng chục nghìn bài xuống ~100-150 bài sáng giá nhất.
        - Bước 3 (Lớp 3 - Fine Re-ranking): Chạy thuật toán chấm điểm đầy đủ `self.match()`
                  (HSV + Spatio-Temporal + Visual Gating 4 Rule) trên ~100-150 bài đó -> trả Top N kết quả.
        """
        t0 = time.perf_counter()

        # Đảm bảo bài gốc đã có vector (chỉ tốn công cho duy nhất 1 bài gốc nếu chưa có trong cache)
        src_urls = self._extract_image_urls(source_item)
        src_text = f"{source_item.get('title', '')} {source_item.get('description', '')}".strip()
        self._ensure_visual_features(src_urls, crop_all=True)
        self._ensure_text_features([src_text])
        self._ensure_cross_text_features([src_text])

        # Đồng thời tự động cập nhật chính bài gốc vào index nếu đang OPEN
        if (source_item.get("status") or "OPEN").strip().upper() == "OPEN":
            self.index.upsert_posts([source_item])

        src_visuals = self._get_visual_list(src_urls)
        src_vis_vecs = [v[1] for v in src_visuals]
        src_text_vec = self.store.get_text(src_text)
        src_cross_text_vec = self._get_cross_text(src_text)

        # LỚP 2: Sàng lọc nhanh 3 kênh trên toàn bộ kho bài
        recalled_candidates, recall_meta = self.index.recall_candidates(
            source_item=source_item,
            src_vis_vecs=src_vis_vecs,
            src_text_vec=src_text_vec,
            top_k_per_channel=top_k_per_channel,
            src_cross_text_vec=src_cross_text_vec,
        )

        # LỚP 3: Chấm điểm tinh (Re-ranking) trên tập ứng viên đã sàng lọc -> cắt gọn Top N (mặc định Top 10)
        ranked = self.match(
            source_item=source_item,
            candidate_items=recalled_candidates,
            include_rejected=include_rejected,
            top_n=top_n,
        )

        total_ms = (time.perf_counter() - t0) * 1000.0
        recall_meta["rerankCount"] = len(ranked)
        recall_meta["totalLatencyMs"] = round(total_ms, 2)
        return ranked, recall_meta
