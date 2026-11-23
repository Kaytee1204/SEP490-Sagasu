import logging
import re
from typing import List, Optional, Tuple
import numpy as np
import torch
from PIL import Image
from .config import CLIP_MODEL_NAME, CLIP_VISION_MODEL_NAME, DEVICE

logger = logging.getLogger(__name__)

# Bảng ánh xạ từ khóa đồ thất lạc & màu sắc Việt -> Anh (dùng cho Text Encoder tích hợp sẵn của CLIP ViT-B/16
# để vector mô tả nằm CÙNG 100% hệ trục tọa độ 512 chiều với vector ảnh ViT-B/16)
VI_TO_EN_PHRASES: List[Tuple[str, str]] = [
    ("thẻ sinh viên", "student id card"),
    ("căn cước công dân", "national id card"),
    ("chứng minh nhân dân", "id card"),
    ("giấy phép lái xe", "driver license card"),
    ("giấy tờ cá nhân", "personal documents id card"),
    ("giấy tờ", "documents id card"),
    ("thẻ ngân hàng", "bank credit card"),
    ("thẻ atm", "atm bank card"),
    ("thẻ xe", "parking card"),
    ("ví da nam", "men leather wallet"),
    ("ví da nữ", "women leather wallet"),
    ("ví da", "leather wallet"),
    ("bóp da", "leather wallet"),
    ("ví tiền", "wallet"),
    ("chìa khóa xe máy", "motorbike keys"),
    ("chìa khóa xe", "vehicle keys"),
    ("chìa khóa phòng", "room keys"),
    ("chìa khóa", "keys keychain"),
    ("tai nghe không dây", "wireless earbuds airpods"),
    ("tai nghe chụp tai", "over-ear headphones"),
    ("tai nghe", "earphones earbuds"),
    ("điện thoại", "smartphone mobile phone"),
    ("máy tính xách tay", "laptop computer"),
    ("máy tính cầm tay", "scientific calculator"),
    ("máy tính bảng", "tablet ipad"),
    ("chuột máy tính", "computer mouse"),
    ("chuột không dây", "wireless mouse"),
    ("bàn phím", "keyboard"),
    ("sạc dự phòng", "power bank charger"),
    ("cục sạc", "power adapter charger"),
    ("dây sạc", "charging cable"),
    ("bình giữ nhiệt", "insulated thermos water bottle"),
    ("bình nước", "water bottle"),
    ("cốc giữ nhiệt", "insulated tumbler cup"),
    ("ba lô", "backpack"),
    ("balo", "backpack"),
    ("túi xách", "handbag tote bag"),
    ("túi đeo chéo", "crossbody shoulder bag"),
    ("cặp sách", "school bag"),
    ("kính cận", "prescription eyeglasses"),
    ("kính râm", "sunglasses"),
    ("mắt kính", "eyeglasses"),
    ("đồng hồ thông minh", "smartwatch"),
    ("đồng hồ đeo tay", "wristwatch"),
    ("đồng hồ", "watch"),
    ("áo khoác", "jacket coat"),
    ("mũ bảo hiểm", "motorcycle helmet"),
    ("nón bảo hiểm", "motorcycle helmet"),
    ("ô dù", "umbrella"),
    ("cây dù", "umbrella"),
    ("sách giáo trình", "textbook book"),
    ("quyển vở", "notebook"),
    ("hộp bút", "pencil case"),
    ("móc khóa", "keychain"),
    ("gấu bông", "plush teddy bear"),
    ("màu đen", "black"),
    ("màu trắng", "white"),
    ("màu nâu", "brown"),
    ("màu xám", "gray"),
    ("màu bạc", "silver"),
    ("màu xanh dương", "blue"),
    ("màu xanh lá", "green"),
    ("màu xanh", "blue green"),
    ("màu đỏ", "red"),
    ("màu hồng", "pink"),
    ("màu vàng", "yellow"),
    ("màu tím", "purple"),
    ("màu cam", "orange"),
    ("màu be", "beige"),
    ("đen", "black"),
    ("trắng", "white"),
    ("nâu", "brown"),
    ("xám", "gray"),
    ("bạc", "silver"),
    ("xanh", "blue"),
    ("đỏ", "red"),
    ("hồng", "pink"),
    ("vàng", "yellow"),
    ("ví", "wallet"),
    ("bóp", "wallet"),
    ("kính", "glasses"),
    ("sạc", "charger"),
    ("chuột", "mouse"),
    ("mũ", "hat cap"),
    ("nón", "hat cap"),
    ("áo", "shirt jacket"),
    ("dù", "umbrella"),
    ("sách", "book"),
]


class MultilingualClipEncoder:
    """
    Bộ mã hóa đa phương thức (Multimodal Encoder) gồm 2 nhánh:
    1. Nhánh Vision (OpenAI CLIP ViT-B/16): mã hóa ảnh -> vector 512 chiều chuẩn hóa L2.
    2. Nhánh Text (Sentence-Transformers Multilingual CLIP): mã hóa mô tả tiếng Việt -> vector 512 chiều chuẩn hóa L2.
    Vì cả 2 nhánh đều ánh xạ về không gian vector 512 chiều chuẩn hóa L2, ta có thể tính Cosine Similarity cho cả:
    - Ảnh <-> Ảnh (Image-to-Image)
    - Mô tả <-> Mô tả (Text-to-Text)
    - Ảnh <-> Mô tả (Cross-Modal Image-to-Text / Text-to-Image)
    """

    def __init__(
        self,
        text_model_name: str = CLIP_MODEL_NAME,
        vision_model_name: str = CLIP_VISION_MODEL_NAME,
        device: str = DEVICE,
    ):
        self.device = device
        self.text_model_name = text_model_name
        self.vision_model_name = vision_model_name

        self.vision_model = None
        self.vision_processor = None
        self.text_model = None

        self._load_vision_model()
        self._load_text_model()

    def _load_vision_model(self):
        """Nạp model CLIP ViT-B/16 (ưu tiên đọc từ cache ổ cứng trước để khởi động nhanh)."""
        try:
            from transformers import CLIPProcessor, CLIPModel
            try:
                self.vision_model = CLIPModel.from_pretrained(self.vision_model_name, local_files_only=True).to(self.device)
                self.vision_processor = CLIPProcessor.from_pretrained(self.vision_model_name, local_files_only=True)
            except Exception:
                self.vision_model = CLIPModel.from_pretrained(self.vision_model_name).to(self.device)
                self.vision_processor = CLIPProcessor.from_pretrained(self.vision_model_name)
            # Chuyển sang chế độ eval() để tắt Dropout khi suy luận
            self.vision_model.eval()
            logger.info(f"CLIP Vision Model '{self.vision_model_name}' loaded on {self.device}.")
        except Exception as e:
            logger.warning(f"Could not load CLIP Vision Model ({e}).")
            self.vision_model = None
            self.vision_processor = None

    def _load_text_model(self):
        """Nạp model Multilingual CLIP Text hỗ trợ tiếng Việt (50+ ngôn ngữ)."""
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
        """Mã hóa 1 ảnh PIL thành vector 512 chiều chuẩn hóa L2."""
        res = self.encode_images_batch([image])
        return res[0] if res else None

    def encode_images_batch(self, images: List[Image.Image]) -> List[Optional[np.ndarray]]:
        """
        Mã hóa hàng loạt nhiều ảnh trong 1 lần forward pass (nhanh hơn ~5x trên CPU so với chạy từng ảnh).
        Bước 1: CLIPProcessor resize ảnh về 224x224 và chuẩn hóa pixel.
        Bước 2: Vision Transformer (ViT-B/16) trích xuất vector đặc trưng 512 chiều (không tính gradient).
        Bước 3: Chuẩn hóa L2: v_hat = v / ||v||_2 để tích vô hướng trở thành Cosine Similarity.
        """
        if self.vision_model is None or self.vision_processor is None or not images:
            return [None] * len(images)
        try:
            inputs = self.vision_processor(images=images, return_tensors="pt")
            if self.device != "cpu":
                inputs = {k: v.to(self.device) for k, v in inputs.items()}
            with torch.no_grad():
                outputs = self.vision_model.get_image_features(**inputs)
                if hasattr(outputs, "pooler_output"):
                    mat = outputs.pooler_output.cpu().numpy()
                elif isinstance(outputs, torch.Tensor):
                    mat = outputs.cpu().numpy()
                else:
                    mat = outputs[0].cpu().numpy()

            if mat.ndim == 1:
                mat = mat.reshape(1, -1)

            results: List[Optional[np.ndarray]] = []
            for row in mat:
                # Chuẩn hóa L2 từng vector ảnh về độ dài đơn vị (||v||_2 = 1)
                norm = np.linalg.norm(row)
                if norm > 0:
                    results.append((row / norm).astype(float))
                else:
                    results.append(row.astype(float))
            return results
        except Exception as ex:
            logger.debug(f"Failed to batch-encode images: {ex}")
            return [None] * len(images)

    def encode_text(self, text: str) -> Optional[np.ndarray]:
        """Mã hóa 1 đoạn mô tả tiếng Việt thành vector 512 chiều chuẩn hóa L2."""
        if self.text_model is None or not text or not text.strip():
            return None
        res = self.encode_texts_batch([text.strip()])
        return res[0] if res else None

    def encode_texts_batch(self, texts: List[str]) -> List[Optional[np.ndarray]]:
        """
        Mã hóa hàng loạt các câu mô tả (tiêu đề + mô tả) thành ma trận vector 512 chiều chuẩn hóa L2.
        """
        if self.text_model is None or not texts:
            return [None] * len(texts)
        try:
            clean = [t.strip() if t else "" for t in texts]
            vecs = self.text_model.encode(clean, batch_size=32, convert_to_numpy=True, show_progress_bar=False)
            if vecs.ndim == 1:
                vecs = vecs.reshape(1, -1)
            results: List[Optional[np.ndarray]] = []
            for i, row in enumerate(vecs):
                if not clean[i]:
                    results.append(None)
                    continue
                # Chuẩn hóa L2 từng vector mô tả về độ dài đơn vị
                norm = np.linalg.norm(row)
                if norm > 0:
                    results.append((row / norm).astype(float))
                else:
                    results.append(row.astype(float))
            return results
        except Exception as ex:
            logger.debug(f"Failed to batch-encode texts: {ex}")
            return [None] * len(texts)

    @staticmethod
    def vi_to_clip_prompt(text: str) -> str:
        """
        Chuyển câu mô tả tiếng Việt sang prompt tiếng Anh súc tích cho CLIP ViT-B/16 Text Encoder
        (giữ nguyên các từ tiếng Anh / thương hiệu như iPhone, MacBook, Pedro, Casio, Dell, AirPods...).
        """
        if not text:
            return ""
        lowered = text.lower()
        found_terms: List[str] = []
        used_en = set()

        for vi_phrase, en_phrase in VI_TO_EN_PHRASES:
            if vi_phrase in lowered and en_phrase not in used_en:
                used_en.add(en_phrase)
                found_terms.append(en_phrase)
                # Xóa cụm đã khớp để tránh từ ngắn khớp đè lên cụm dài (vd: "ví" đè "ví da nam")
                lowered = lowered.replace(vi_phrase, " ")

        # Giữ lại các từ ASCII (thương hiệu, mã model: iphone, pedro, casio, macbook, sony, dell...)
        ascii_words = re.findall(r"\b[a-zA-Z][a-zA-Z0-9\-]{2,}\b", text)
        stop_ascii = {"tim", "mat", "nhat", "duoc", "tai", "nha", "tang", "toa", "alpha", "beta", "gamma", "delta"}
        for w in ascii_words:
            wl = w.lower()
            if wl not in stop_ascii and wl not in used_en:
                used_en.add(wl)
                found_terms.append(w)

        if not found_terms:
            return text[:120]
        return "a photo of " + " ".join(found_terms[:16])

    def encode_cross_modal_texts_batch(self, texts: List[str]) -> List[Optional[np.ndarray]]:
        """
        Mã hóa mô tả bằng chính Text Encoder tích hợp sẵn của `openai/clip-vit-base-patch16`.
        Kết quả là vector 512 chiều chuẩn hóa L2 nằm CÙNG 100% HỆ TRỤC TỌA ĐỘ với vector ảnh ViT-B/16,
        khắc phục hoàn toàn lỗi lệch không gian giữa ViT-B/16 và ViT-B/32.
        """
        if self.vision_model is None or self.vision_processor is None or not texts:
            return [None] * len(texts)
        try:
            prompts = [self.vi_to_clip_prompt(t) if (t and t.strip()) else "" for t in texts]
            non_empty_indices = [i for i, p in enumerate(prompts) if p]
            if not non_empty_indices:
                return [None] * len(texts)

            batch_prompts = [prompts[i] for i in non_empty_indices]
            inputs = self.vision_processor(
                text=batch_prompts,
                return_tensors="pt",
                padding=True,
                truncation=True,
                max_length=77,
            )
            if self.device != "cpu":
                inputs = {k: v.to(self.device) for k, v in inputs.items()}
            with torch.no_grad():
                outputs = self.vision_model.get_text_features(**inputs)
                if hasattr(outputs, "pooler_output"):
                    mat = outputs.pooler_output.cpu().numpy()
                elif isinstance(outputs, torch.Tensor):
                    mat = outputs.cpu().numpy()
                else:
                    mat = outputs[0].cpu().numpy()

            if mat.ndim == 1:
                mat = mat.reshape(1, -1)

            results: List[Optional[np.ndarray]] = [None] * len(texts)
            for idx_pos, orig_i in enumerate(non_empty_indices):
                row = mat[idx_pos]
                norm = np.linalg.norm(row)
                results[orig_i] = (row / norm).astype(float) if norm > 0 else row.astype(float)
            return results
        except Exception as ex:
            logger.debug(f"Failed to batch-encode cross-modal texts: {ex}")
            return [None] * len(texts)

    def cosine_similarity(self, vec1: np.ndarray, vec2: np.ndarray) -> float:
        """
        Tính độ tương đồng Cosine giữa 2 vector đã chuẩn hóa L2:
            cos(theta) = vec1 · vec2
        Kết quả được kẹp trong đoạn [0.0, 1.0] (1.0 = giống hệt nhau, 0.0 = không liên quan).
        """
        if vec1 is None or vec2 is None:
            return 0.0
        val = float(np.dot(vec1, vec2))
        return max(0.0, min(1.0, val))
