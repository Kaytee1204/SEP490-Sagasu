import json
import logging
import re
import sqlite3
import threading
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
import numpy as np

from .config import WEIGHTS_DIR
from .vector_store import PersistentVectorStore

logger = logging.getLogger(__name__)

INDEX_DB_PATH = WEIGHTS_DIR / "vector_index.sqlite"

_INDEX_STOPWORDS: Set[str] = {
    "tìm", "mất", "rơi", "quên", "thất", "lạc", "nhặt", "được", "thấy", "gặp",
    "mình", "tôi", "em", "anh", "chị", "bạn", "ai", "người", "nào",
    "bị", "có", "là", "và", "với", "của", "cho", "từ", "đến", "ở", "tại", "trong", "ngoài", "trên", "dưới",
    "một", "hai", "ba", "các", "những", "chiếc", "cái", "bộ", "đôi", "số",
    "khu", "vực", "đại", "học", "trường", "fpt", "sakura", "tòa", "nhà", "phòng", "tầng",
    "xin", "vui", "lòng", "liên", "hệ", "cảm", "ơn", "trả", "lại", "giúp", "nhé", "nha", "ạ",
    "đồ", "vật", "dùng", "cá", "nhân", "bài", "đăng", "màu", "loại", "hiệu", "khoảng", "lúc", "ngày",
}


class MultimodalVectorIndex:
    """
    LỚP 2 TRONG KIẾN TRÚC 3 LỚP: Kho chỉ mục Vector Đa phương thức & Bộ Sàng lọc Đa kênh (Multi-Channel Recall).

    Giải quyết bài toán quy mô vài chục nghìn bài đăng (50.000+ bài):
    - Lưu metadata của toàn bộ bài đăng đang OPEN vào SQLite (`vector_index.sqlite`) + giữ ma trận vector
      float32 liên tiếp trong RAM (`img_matrix: (N_imgs, 512)` và `txt_matrix: (N_posts, 512)`).
    - Thay vì chạy vòng lặp `for` Python O(N) từng bài, dùng phép nhân ma trận BLAS/SIMD (`matrix @ query_vec`):
      quét 50.000 vector 512 chiều chỉ mất ~5 - 15 ms!
    - Chạy đồng thời 3 kênh Recall (Không giới hạn Category, lọc cứng khác người đăng & ngược loại LOST <-> FOUND):
        + Kênh 1 (Ảnh <-> Ảnh)  : `img_matrix @ src_vis_vec`  -> Top K bài giống ảnh nhất.
        + Kênh 2 (Mô tả <-> Mô tả): `txt_matrix @ src_text_vec` -> Top K bài giống mô tả nhất.
        + Kênh 3 (Ảnh <-> Mô tả): `img_matrix @ src_text_vec` & `txt_matrix @ src_vis_vec` -> Top K khớp chéo.
    - Gộp (Union) 3 kênh -> chỉ giữ lại ~100 - 150 ứng viên sáng giá nhất để đẩy sang Lớp 3 (Fine Re-ranking).
    """

    def __init__(self, store: PersistentVectorStore, db_path: Path = INDEX_DB_PATH):
        self.store = store
        self.db_path = db_path
        self._lock = threading.Lock()

        # Kho metadata bài đăng đang active trong RAM: post_id (str) -> dict bài đăng
        self.posts: Dict[str, Dict[str, Any]] = {}

        # Ma trận tìm kiếm nhanh trong RAM (được build lại tự động khi có bài mới/xóa bài)
        self._dirty = True
        self._img_matrix: Optional[np.ndarray] = None       # Shape: (M_imgs, 512), float32
        self._img_post_ids: List[str] = []
        self._img_post_types: np.ndarray = np.array([], dtype=object)
        self._img_author_keys: np.ndarray = np.array([], dtype=object)

        self._txt_matrix: Optional[np.ndarray] = None       # Shape: (N_posts, 512), float32
        self._txt_post_ids: List[str] = []
        self._txt_post_types: np.ndarray = np.array([], dtype=object)
        self._txt_author_keys: np.ndarray = np.array([], dtype=object)

        # Inverted Index cho từ khóa đồ vật (Kênh 4 Recall: đảm bảo không bao giờ bỏ sót bài mà tìm thủ công thấy)
        self._kw_inverted: Dict[str, Set[str]] = defaultdict(set)

        self._init_db()
        self._load_all_posts()

    def _init_db(self) -> None:
        """Khởi tạo bảng SQLite lưu danh mục bài đăng đã được lập chỉ mục (chế độ WAL tốc độ cao)."""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("PRAGMA journal_mode=WAL;")
            conn.execute("PRAGMA synchronous=NORMAL;")
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS indexed_posts (
                    post_id TEXT PRIMARY KEY,
                    post_type TEXT NOT NULL,
                    author_key TEXT NOT NULL,
                    status TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    updated_at REAL NOT NULL
                )
                """
            )
            conn.commit()

    def _load_all_posts(self) -> None:
        """Nạp toàn bộ danh sách bài đang OPEN từ SQLite lên RAM ngay khi khởi động server."""
        t0 = time.perf_counter()
        try:
            with sqlite3.connect(self.db_path) as conn:
                cur = conn.execute("SELECT post_id, payload_json FROM indexed_posts WHERE status = 'OPEN'")
                for pid, payload_json in cur.fetchall():
                    try:
                        self.posts[str(pid)] = json.loads(payload_json)
                    except Exception:
                        continue
            self._dirty = True
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            logger.info(f"MultimodalVectorIndex loaded {len(self.posts)} active posts in {elapsed_ms:.1f} ms.")
        except Exception as ex:
            logger.warning(f"Failed to load MultimodalVectorIndex: {ex}")

    @staticmethod
    def _canonical_url(url: Optional[str]) -> Optional[str]:
        if not url or not isinstance(url, str):
            return None
        cleaned = url.strip()
        return cleaned.split("?", 1)[0] if cleaned else None

    @staticmethod
    def _extract_author_key(item: Dict[str, Any]) -> str:
        """Chuẩn hóa khóa định danh người đăng (ưu tiên authorId, fallback authorName) để chặn tự so sánh."""
        aid = item.get("authorId")
        if aid is not None and str(aid).strip():
            return f"id:{str(aid).strip()}"
        aname = (item.get("authorName") or "").strip().lower()
        if aname:
            return f"name:{aname}"
        return ""

    @staticmethod
    def _extract_image_urls(item: Dict[str, Any]) -> List[str]:
        urls: List[str] = []
        seen = set()
        raw_list = item.get("imageUrls")
        if isinstance(raw_list, list):
            for u in raw_list:
                if isinstance(u, str) and u.strip():
                    k = MultimodalVectorIndex._canonical_url(u)
                    if k and k not in seen:
                        seen.add(k)
                        urls.append(u.strip())
        single = item.get("imageUrl")
        if isinstance(single, str) and single.strip():
            k = MultimodalVectorIndex._canonical_url(single)
            if k and k not in seen:
                seen.add(k)
                urls.append(single.strip())
        return urls

    @staticmethod
    def _build_text(item: Dict[str, Any]) -> str:
        return f"{item.get('title', '')} {item.get('description', '')}".strip()

    @staticmethod
    def _extract_keywords(text: str) -> Set[str]:
        if not text:
            return set()
        cleaned = re.sub(r"#\d+", " ", text.lower())
        tokens = re.findall(r"[^\W\d_]{2,}|[a-z0-9]{2,}", cleaned, flags=re.UNICODE)
        return {t for t in tokens if t not in _INDEX_STOPWORDS and not t.isdigit()}

    def get_indexed_ids_summary(self) -> Dict[str, Any]:
        """Trả về toàn bộ danh sách ID đang có trong Vector Index kèm số lượng LOST / FOUND để BE đồng bộ chênh lệch."""
        with self._lock:
            lost_count = 0
            found_count = 0
            ids: List[str] = []
            for pid_str, item in self.posts.items():
                ids.append(pid_str)
                pt = (item.get("postType") or "").strip().upper()
                if pt == "LOST":
                    lost_count += 1
                elif pt == "FOUND":
                    found_count += 1
            return {
                "ids": ids,
                "total": len(ids),
                "lostCount": lost_count,
                "foundCount": found_count,
            }

    def delete_posts_batch(self, post_ids: List[Any]) -> int:
        """Xóa hàng loạt các bài đã đóng / trả đồ / ẩn khỏi Vector Index."""
        if not post_ids:
            return 0
        removed = 0
        to_del = []
        with self._lock:
            for pid in post_ids:
                pid_str = str(pid)
                if self.posts.pop(pid_str, None) is not None:
                    removed += 1
                to_del.append((pid_str,))
            if to_del:
                try:
                    with sqlite3.connect(self.db_path) as conn:
                        conn.executemany("DELETE FROM indexed_posts WHERE post_id = ?", to_del)
                        conn.commit()
                except Exception as ex:
                    logger.warning(f"Failed to batch delete indexed posts: {ex}")
                self._dirty = True
        return removed

    def upsert_posts(self, items: List[Dict[str, Any]]) -> int:
        """
        LỚP 1 (Async Indexing): Thêm mới hoặc cập nhật metadata của 1 hoặc nhiều bài đăng vào Vector Index.
        Nếu bài có status != 'OPEN' (vd: RETURNED / CLOSED), tự động xóa khỏi index active.
        """
        if not items:
            return 0

        now = time.time()
        to_upsert = []
        to_delete = []

        with self._lock:
            for item in items:
                pid = item.get("id")
                if pid is None:
                    continue
                pid_str = str(pid)
                status = (item.get("status") or "OPEN").strip().upper()
                post_type = (item.get("postType") or "").strip().upper()

                if status != "OPEN" or post_type not in ("LOST", "FOUND"):
                    if self.posts.pop(pid_str, None) is not None:
                        to_delete.append((pid_str,))
                    continue

                # Nếu bài đăng đã tồn tại với nội dung y hệt -> bỏ qua, không đánh dấu _dirty để tránh rebuild ma trận
                existing = self.posts.get(pid_str)
                if existing is not None and existing == item:
                    continue

                author_key = self._extract_author_key(item)
                self.posts[pid_str] = item
                to_upsert.append((
                    pid_str,
                    post_type,
                    author_key,
                    status,
                    json.dumps(item, ensure_ascii=False),
                    now,
                ))

            if not to_upsert and not to_delete:
                return 0

            try:
                with sqlite3.connect(self.db_path) as conn:
                    if to_upsert:
                        conn.executemany(
                            """
                            INSERT OR REPLACE INTO indexed_posts
                            (post_id, post_type, author_key, status, payload_json, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                            to_upsert,
                        )
                    if to_delete:
                        conn.executemany("DELETE FROM indexed_posts WHERE post_id = ?", to_delete)
                    conn.commit()
            except Exception as ex:
                logger.warning(f"Failed to persist indexed posts: {ex}")

            self._dirty = True
            return len(to_upsert)

    def delete_post(self, post_id: Any) -> bool:
        """Xóa 1 bài đăng khỏi Vector Index khi bài bị xóa, ẩn hoặc đã trả đồ (RETURNED)."""
        pid_str = str(post_id)
        with self._lock:
            existed = self.posts.pop(pid_str, None) is not None
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.execute("DELETE FROM indexed_posts WHERE post_id = ?", (pid_str,))
                    conn.commit()
            except Exception as ex:
                logger.warning(f"Failed to delete post {pid_str} from index: {ex}")
            self._dirty = True
            return existed

    def _rebuild_matrices_if_needed(self) -> None:
        """
        Xây dựng ma trận float32 liên tiếp trong RAM từ kho `PersistentVectorStore`.
        Ma trận này cho phép tính tích vô hướng Cosine trên hàng chục nghìn vector bằng 1 lệnh BLAS duy nhất.
        """
        if not self._dirty:
            return

        with self._lock:
            if not self._dirty:
                return

            img_vecs: List[np.ndarray] = []
            img_pids: List[str] = []
            img_types: List[str] = []
            img_authors: List[str] = []

            txt_vecs: List[np.ndarray] = []
            txt_pids: List[str] = []
            txt_types: List[str] = []
            txt_authors: List[str] = []

            kw_inverted: Dict[str, Set[str]] = defaultdict(set)

            for pid_str, item in self.posts.items():
                ptype = (item.get("postType") or "").strip().upper()
                akey = self._extract_author_key(item)

                # Gom toàn bộ vector ảnh của bài đăng vào ma trận ảnh (1 bài nhiều ảnh -> nhiều dòng)
                for u in self._extract_image_urls(item):
                    key = self._canonical_url(u)
                    cached = self.store.visual_cache.get(key) if key else None
                    if cached is not None:
                        _, vis_vec, _ = cached
                        img_vecs.append(np.asarray(vis_vec, dtype=np.float32))
                        img_pids.append(pid_str)
                        img_types.append(ptype)
                        img_authors.append(akey)

                # Gom vector mô tả (title + description) vào ma trận văn bản + Inverted Index từ khóa
                text_str = self._build_text(item)
                if text_str:
                    for kw in self._extract_keywords(text_str):
                        kw_inverted[kw].add(pid_str)
                    thash = self.store.hash_text(text_str)
                    t_vec = self.store.text_cache.get(thash)
                    if t_vec is not None:
                        txt_vecs.append(np.asarray(t_vec, dtype=np.float32))
                        txt_pids.append(pid_str)
                        txt_types.append(ptype)
                        txt_authors.append(akey)

            self._img_matrix = np.vstack(img_vecs) if img_vecs else None
            self._img_post_ids = img_pids
            self._img_post_types = np.array(img_types, dtype=object)
            self._img_author_keys = np.array(img_authors, dtype=object)

            self._txt_matrix = np.vstack(txt_vecs) if txt_vecs else None
            self._txt_post_ids = txt_pids
            self._txt_post_types = np.array(txt_types, dtype=object)
            self._txt_author_keys = np.array(txt_authors, dtype=object)

            self._kw_inverted = kw_inverted
            self._dirty = False

    @staticmethod
    def _top_k_post_ids(
        matrix: Optional[np.ndarray],
        post_ids: List[str],
        post_types: np.ndarray,
        author_keys: np.ndarray,
        query_vec: np.ndarray,
        opposite_type: str,
        src_author_key: str,
        src_id_str: str,
        top_k: int,
    ) -> List[str]:
        """
        Thực hiện 1 lượt tìm kiếm vector tốc độ cao (BLAS Matrix-Vector Multiplication):
        1. Lọc mặt nạ boolean: đúng loại ngược nhau (LOST <-> FOUND) & khác tác giả.
        2. Tính tích vô hướng Cosine cho toàn bộ vector hợp lệ cùng lúc: `scores = matrix[mask] @ query_vec`.
        3. Dùng `np.argpartition` (độ phức tạp O(N) C-level, nhanh hơn sort toàn mảng) để lấy Top K.
        """
        if matrix is None or len(post_ids) == 0 or query_vec is None:
            return []

        mask = (post_types == opposite_type)
        if src_author_key:
            mask = mask & (author_keys != src_author_key)

        valid_indices = np.flatnonzero(mask)
        if len(valid_indices) == 0:
            return []

        q = np.asarray(query_vec, dtype=np.float32)
        scores = matrix[valid_indices] @ q

        # Lấy nhiều hơn 1 chút nếu 1 bài có nhiều ảnh để đảm bảo đủ top_k bài khác nhau
        k = min(len(scores), max(top_k * 2, top_k))
        if k < len(scores):
            part_idx = np.argpartition(scores, -k)[-k:]
            ordered_local = part_idx[np.argsort(scores[part_idx])[::-1]]
        else:
            ordered_local = np.argsort(scores)[::-1]

        selected: List[str] = []
        seen: Set[str] = set()
        for loc in ordered_local:
            pid = post_ids[int(valid_indices[int(loc)])]
            if pid == src_id_str or pid in seen:
                continue
            seen.add(pid)
            selected.append(pid)
            if len(selected) >= top_k:
                break
        return selected

    def recall_candidates(
        self,
        source_item: Dict[str, Any],
        src_vis_vecs: List[np.ndarray],
        src_text_vec: Optional[np.ndarray],
        top_k_per_channel: int = 60,
        src_cross_text_vec: Optional[np.ndarray] = None,
    ) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
        """
        TẦNG 1 CỦA TÌM KIẾM (Multi-Channel Recall):
        Quét toàn bộ kho chỉ mục (hàng chục nghìn bài, KHÔNG giới hạn category) qua 3 kênh song song:
        - Kênh 1 (Ảnh <-> Ảnh)       : Top K bài có ảnh giống nhất với bất kỳ ảnh nào của bài gốc.
        - Kênh 2 (Mô tả <-> Mô tả)   : Top K bài có mô tả giống nhất.
        - Kênh 3 (Ảnh <-> Mô tả 2 chiều): Top K bài có ảnh khớp mô tả gốc + mô tả khớp ảnh gốc.
        Trả về tập hợp nhất (Union) khoảng 100 - 150 bài sáng giá nhất để chuyển sang Lớp 3 (Re-ranking).
        """
        t0 = time.perf_counter()
        self._rebuild_matrices_if_needed()

        src_id_str = str(source_item.get("id")) if source_item.get("id") is not None else ""
        src_type = (source_item.get("postType") or "").strip().upper()
        opposite_type = "FOUND" if src_type == "LOST" else "LOST"
        src_author_key = self._extract_author_key(source_item)

        recalled_ids: Set[str] = set()

        # ── KÊNH 1: ẢNH <-> ẢNH (quét toàn bộ ảnh trong kho) ──
        for v_vec in src_vis_vecs:
            recalled_ids.update(
                self._top_k_post_ids(
                    self._img_matrix, self._img_post_ids, self._img_post_types, self._img_author_keys,
                    v_vec, opposite_type, src_author_key, src_id_str, top_k_per_channel,
                )
            )

        # ── KÊNH 2: MÔ TẢ <-> MÔ TẢ ──
        if src_text_vec is not None:
            recalled_ids.update(
                self._top_k_post_ids(
                    self._txt_matrix, self._txt_post_ids, self._txt_post_types, self._txt_author_keys,
                    src_text_vec, opposite_type, src_author_key, src_id_str, top_k_per_channel,
                )
            )

        # ── KÊNH 3: ẢNH <-> MÔ TẢ CÙNG KHÔNG GIAN ViT-B/16 (Cross-Modal Recall) ──
        # Dùng vector chữ từ Text Encoder của chính ViT-B/16 để nhân với ma trận ảnh ViT-B/16
        cross_q = src_cross_text_vec if src_cross_text_vec is not None else src_text_vec
        if cross_q is not None:
            recalled_ids.update(
                self._top_k_post_ids(
                    self._img_matrix, self._img_post_ids, self._img_post_types, self._img_author_keys,
                    cross_q, opposite_type, src_author_key, src_id_str, top_k_per_channel,
                )
            )

        # ── KÊNH 4: TỪ KHÓA ĐỒ VẬT (Inverted Index Recall) ──
        # Đảm bảo mọi bài khớp từ khóa đồ vật mà tìm thủ công tìm thấy đều được đưa vào Lớp 3 chấm điểm tinh
        src_kws = self._extract_keywords(self._build_text(source_item))
        if src_kws:
            hit_counter: Counter = Counter()
            for kw in src_kws:
                for pid in self._kw_inverted.get(kw, ()):
                    if pid == src_id_str:
                        continue
                    cand_meta = self.posts.get(pid)
                    if not cand_meta:
                        continue
                    if (cand_meta.get("postType") or "").strip().upper() != opposite_type:
                        continue
                    if src_author_key and self._extract_author_key(cand_meta) == src_author_key:
                        continue
                    hit_counter[pid] += 1
            for pid, _ in hit_counter.most_common(top_k_per_channel):
                recalled_ids.add(pid)

        candidates = [self.posts[pid] for pid in recalled_ids if pid in self.posts]
        recall_ms = (time.perf_counter() - t0) * 1000.0

        telemetry = {
            "totalIndexedPosts": len(self.posts),
            "indexedImageVectors": int(self._img_matrix.shape[0]) if self._img_matrix is not None else 0,
            "indexedTextVectors": int(self._txt_matrix.shape[0]) if self._txt_matrix is not None else 0,
            "recalledCandidates": len(candidates),
            "recallLatencyMs": round(recall_ms, 2),
        }
        return candidates, telemetry

    def stats(self) -> Dict[str, Any]:
        lost_count = sum(1 for p in self.posts.values() if (p.get("postType") or "").strip().upper() == "LOST")
        found_count = sum(1 for p in self.posts.values() if (p.get("postType") or "").strip().upper() == "FOUND")
        return {
            "totalIndexedPosts": len(self.posts),
            "lostIndexedPosts": lost_count,
            "foundIndexedPosts": found_count,
            "indexedImageVectors": int(self._img_matrix.shape[0]) if self._img_matrix is not None else 0,
            "indexedTextVectors": int(self._txt_matrix.shape[0]) if self._txt_matrix is not None else 0,
            "indexFile": str(self.db_path),
        }
