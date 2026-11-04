import hashlib
import logging
import sqlite3
import threading
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import numpy as np

from .config import WEIGHTS_DIR

logger = logging.getLogger(__name__)

DB_PATH = WEIGHTS_DIR / "feature_store.sqlite"


class PersistentVectorStore:
    """
    Persistent SQLite + In-Memory L1 Cache for Multimodal Feature Vectors:
    - Stores 512-d float32 CLIP Vision vectors + 176-d float32 HSV histograms + blur flags
    - Stores 512-d float32 Multilingual CLIP Text vectors
    - Eliminates redundant R2 image downloads and PyTorch CPU inferences across requests & restarts.
    """

    def __init__(self, db_path: Path = DB_PATH, expected_hsv_dim: int = 176):
        self.db_path = db_path
        self.expected_hsv_dim = expected_hsv_dim
        self._lock = threading.Lock()

        # L1 RAM Cache
        self.visual_cache: Dict[str, Tuple[bool, np.ndarray, np.ndarray]] = {}
        self.text_cache: Dict[str, np.ndarray] = {}

        # Telemetry metrics
        self.visual_hits = 0
        self.visual_misses = 0
        self.text_hits = 0
        self.text_misses = 0
        self.total_requests = 0
        self.last_latency_ms = 0.0

        self._init_db()
        self._load_all_into_memory()

    def _init_db(self) -> None:
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("PRAGMA journal_mode=WAL;")
            conn.execute("PRAGMA synchronous=NORMAL;")
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS visual_features (
                    canonical_key TEXT PRIMARY KEY,
                    is_blurry INTEGER NOT NULL,
                    vis_vec BLOB NOT NULL,
                    hsv_hist BLOB NOT NULL,
                    updated_at REAL NOT NULL
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS text_features (
                    text_hash TEXT PRIMARY KEY,
                    text_vec BLOB NOT NULL,
                    updated_at REAL NOT NULL
                )
                """
            )
            conn.commit()

    def _load_all_into_memory(self) -> None:
        t0 = time.perf_counter()
        try:
            with sqlite3.connect(self.db_path) as conn:
                cur = conn.execute("SELECT canonical_key, is_blurry, vis_vec, hsv_hist FROM visual_features")
                for key, is_blurry, vis_blob, hsv_blob in cur.fetchall():
                    vis_vec = np.frombuffer(vis_blob, dtype=np.float32).astype(float)
                    hsv_hist = np.frombuffer(hsv_blob, dtype=np.float32).astype(float)
                    if vis_vec.shape == (512,) and hsv_hist.shape == (self.expected_hsv_dim,):
                        self.visual_cache[key] = (bool(is_blurry), vis_vec, hsv_hist)

                cur_t = conn.execute("SELECT text_hash, text_vec FROM text_features")
                for thash, t_blob in cur_t.fetchall():
                    t_vec = np.frombuffer(t_blob, dtype=np.float32).astype(float)
                    if t_vec.shape == (512,):
                        self.text_cache[thash] = t_vec

            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            logger.info(
                f"PersistentVectorStore loaded {len(self.visual_cache)} visual vectors and "
                f"{len(self.text_cache)} text vectors from SQLite in {elapsed_ms:.1f} ms."
            )
        except Exception as ex:
            logger.warning(f"Failed to load PersistentVectorStore cache: {ex}")

    @staticmethod
    def hash_text(text: str) -> str:
        return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()

    def get_visual(self, key: Optional[str]) -> Optional[Tuple[bool, np.ndarray, np.ndarray]]:
        if not key:
            return None
        val = self.visual_cache.get(key)
        if val is not None:
            self.visual_hits += 1
            return val
        self.visual_misses += 1
        return None

    def put_visual_batch(self, items: List[Tuple[str, bool, np.ndarray, np.ndarray]]) -> None:
        if not items:
            return
        now = time.time()
        rows = []
        with self._lock:
            for key, is_blurry, vis_vec, hsv_hist in items:
                self.visual_cache[key] = (is_blurry, vis_vec, hsv_hist)
                rows.append((
                    key,
                    1 if is_blurry else 0,
                    np.asarray(vis_vec, dtype=np.float32).tobytes(),
                    np.asarray(hsv_hist, dtype=np.float32).tobytes(),
                    now,
                ))
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.executemany(
                        """
                        INSERT OR REPLACE INTO visual_features
                        (canonical_key, is_blurry, vis_vec, hsv_hist, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        rows,
                    )
                    conn.commit()
            except Exception as ex:
                logger.warning(f"Failed to persist visual vectors to SQLite: {ex}")

    def get_text(self, text: str) -> Optional[np.ndarray]:
        norm = (text or "").strip()
        if not norm:
            return None
        thash = self.hash_text(norm)
        val = self.text_cache.get(thash)
        if val is not None:
            self.text_hits += 1
            return val
        self.text_misses += 1
        return None

    def put_text_batch(self, items: List[Tuple[str, np.ndarray]]) -> None:
        if not items:
            return
        now = time.time()
        rows = []
        with self._lock:
            for text, vec in items:
                thash = self.hash_text(text)
                self.text_cache[thash] = vec
                rows.append((
                    thash,
                    np.asarray(vec, dtype=np.float32).tobytes(),
                    now,
                ))
            try:
                with sqlite3.connect(self.db_path) as conn:
                    conn.executemany(
                        """
                        INSERT OR REPLACE INTO text_features (text_hash, text_vec, updated_at)
                        VALUES (?, ?, ?)
                        """,
                        rows,
                    )
                    conn.commit()
            except Exception as ex:
                logger.warning(f"Failed to persist text vectors to SQLite: {ex}")

    def stats(self) -> Dict[str, object]:
        total_vis = self.visual_hits + self.visual_misses
        total_txt = self.text_hits + self.text_misses
        return {
            "cachedVisualVectors": len(self.visual_cache),
            "cachedTextVectors": len(self.text_cache),
            "visualCacheHitRate": round((self.visual_hits / total_vis * 100.0) if total_vis > 0 else 0.0, 1),
            "textCacheHitRate": round((self.text_hits / total_txt * 100.0) if total_txt > 0 else 0.0, 1),
            "totalRequests": self.total_requests,
            "lastLatencyMs": round(self.last_latency_ms, 1),
            "storageFile": str(self.db_path),
        }
