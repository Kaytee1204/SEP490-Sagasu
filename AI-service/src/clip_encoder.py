import logging
from typing import Optional
import numpy as np
import torch
from PIL import Image
from .config import CLIP_MODEL_NAME, CLIP_VISION_MODEL_NAME, DEVICE

logger = logging.getLogger(__name__)

class MultilingualClipEncoder:
    def __init__(self, text_model_name: str = CLIP_MODEL_NAME, vision_model_name: str = CLIP_VISION_MODEL_NAME, device: str = DEVICE):
        self.device = device
        self.text_model_name = text_model_name
        self.vision_model_name = vision_model_name

        self.vision_model = None
        self.vision_processor = None
        self.text_model = None

        self._load_vision_model()
        self._load_text_model()

    def _load_vision_model(self):
        try:
            from transformers import CLIPProcessor, CLIPModel
            try:
                self.vision_model = CLIPModel.from_pretrained(self.vision_model_name, local_files_only=True).to(self.device)
                self.vision_processor = CLIPProcessor.from_pretrained(self.vision_model_name, local_files_only=True)
            except Exception:
                self.vision_model = CLIPModel.from_pretrained(self.vision_model_name).to(self.device)
                self.vision_processor = CLIPProcessor.from_pretrained(self.vision_model_name)
            self.vision_model.eval()
            logger.info(f"CLIP Vision Model '{self.vision_model_name}' loaded on {self.device}.")
        except Exception as e:
            logger.warning(f"Could not load CLIP Vision Model ({e}).")
            self.vision_model = None
            self.vision_processor = None

    def _load_text_model(self):
        try:
            from sentence_transformers import SentenceTransformer
            try:
                self.text_model = SentenceTransformer(self.text_model_name, device=self.device, local_files_only=True)
            except Exception:
                self.text_model = SentenceTransformer(self.text_model_name, device=self.device)
            logger.info(f"Multilingual CLIP Text Model '{self.text_model_name}' loaded on {self.device}.")
        except Exception as e:
            logger.warning(f"Could not load SentenceTransformer CLIP Text Model ({e}).")
            self.text_model = None

    def encode_image(self, image: Image.Image) -> Optional[np.ndarray]:
        """Encodes PIL image into a 512-d normalized vector using ViT-B/16."""
        if self.vision_model is None or self.vision_processor is None or image is None:
            return None
        try:
            inputs = self.vision_processor(images=image, return_tensors="pt")
            if self.device != "cpu":
                inputs = {k: v.to(self.device) for k, v in inputs.items()}
            with torch.no_grad():
                outputs = self.vision_model.get_image_features(**inputs)
                if hasattr(outputs, "pooler_output"):
                    vec = outputs.pooler_output.squeeze().cpu().numpy()
                else:
                    vec = outputs.squeeze().cpu().numpy()

            norm = np.linalg.norm(vec)
            if norm > 0:
                return (vec / norm).astype(float)
            return vec
        except Exception as ex:
            logger.debug(f"Failed to encode image: {ex}")
            return None

    def encode_text(self, text: str) -> Optional[np.ndarray]:
        """Encodes Vietnamese text into a 512-d normalized vector."""
        if self.text_model is None or not text or not text.strip():
            return None
        try:
            vec = self.text_model.encode(text.strip(), convert_to_numpy=True, show_progress_bar=False)
            norm = np.linalg.norm(vec)
            if norm > 0:
                return (vec / norm).astype(float)
            return vec
        except Exception as ex:
            logger.debug(f"Failed to encode text: {ex}")
            return None

    def cosine_similarity(self, vec1: np.ndarray, vec2: np.ndarray) -> float:
        """Returns cosine similarity bounded in [0.0, 1.0]."""
        if vec1 is None or vec2 is None:
            return 0.0
        val = float(np.dot(vec1, vec2))
        return max(0.0, min(1.0, val))
