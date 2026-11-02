import logging
from typing import Optional, List
import numpy as np
from PIL import Image
from .config import CLIP_MODEL_NAME, DEVICE

logger = logging.getLogger(__name__)

class MultilingualClipEncoder:
    def __init__(self, model_name: str = CLIP_MODEL_NAME, device: str = DEVICE):
        self.model = None
        self.model_name = model_name
        self.device = device
        self._load_model()

    def _load_model(self):
        try:
            from sentence_transformers import SentenceTransformer
            self.model = SentenceTransformer(self.model_name, device=self.device)
            logger.info(f"Multilingual CLIP model '{self.model_name}' loaded on {self.device}.")
        except Exception as e:
            logger.warning(f"Could not load SentenceTransformer CLIP ({e}). Using lexical fallback mode.")
            self.model = None

    def encode_image(self, image: Image.Image) -> Optional[np.ndarray]:
        """Encodes PIL image into a 512-d normalized vector."""
        if self.model is None or image is None:
            return None
        try:
            vec = self.model.encode(image, convert_to_numpy=True, show_progress_bar=False)
            norm = np.linalg.norm(vec)
            return vec / (norm + 1e-9)
        except Exception as ex:
            logger.debug(f"Failed to encode image: {ex}")
            return None

    def encode_text(self, text: str) -> Optional[np.ndarray]:
        """Encodes Vietnamese text into a 512-d normalized vector."""
        if self.model is None or not text or not text.strip():
            return None
        try:
            vec = self.model.encode(text.strip(), convert_to_numpy=True, show_progress_bar=False)
            norm = np.linalg.norm(vec)
            return vec / (norm + 1e-9)
        except Exception as ex:
            logger.debug(f"Failed to encode text: {ex}")
            return None

    @staticmethod
    def cosine_similarity(vec1: Optional[np.ndarray], vec2: Optional[np.ndarray]) -> float:
        if vec1 is None or vec2 is None:
            return 0.0
        return float(np.clip(np.dot(vec1, vec2), 0.0, 1.0))
