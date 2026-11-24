import os
from pathlib import Path

# Đường dẫn thư mục gốc của AI-service và thư mục lưu trọng số model / SQLite vector cache
BASE_DIR = Path(__file__).resolve().parent.parent
WEIGHTS_DIR = BASE_DIR / "weights"
WEIGHTS_DIR.mkdir(parents=True, exist_ok=True)

# 1. Model YOLOv8n (Ultralytics, pretrained COCO-80) dùng để phát hiện & cắt vật thể chính khỏi ảnh nền
YOLO_MODEL_PATH = os.getenv("YOLO_MODEL_PATH", str(WEIGHTS_DIR / "yolov8n.pt"))

# 2. Model OpenAI CLIP ViT-B/16 (HuggingFace) dùng mã hóa hình ảnh thành vector 512 chiều chuẩn hóa L2
CLIP_VISION_MODEL_NAME = os.getenv("CLIP_VISION_MODEL_NAME", "openai/clip-vit-base-patch16")

# 3. Model Multilingual CLIP (Sentence-Transformers) dùng mã hóa mô tả tiếng Việt thành vector 512 chiều
#    cùng không gian nhúng với CLIP để so sánh Mô tả <-> Mô tả và Ảnh <-> Mô tả
CLIP_MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "sentence-transformers/clip-ViT-B-32-multilingual-v1")

# Thiết bị chạy suy luận PyTorch ("cpu" hoặc "cuda")
DEVICE = os.getenv("DEVICE", "cpu")

# Danh sách từ khóa địa danh đặc trưng trong khuôn viên trường để tính điểm khớp không gian
CAMPUS_LOCATIONS = [
    "alpha", "beta", "gamma", "delta", "epsilon",
    "thư viện", "canteen", "nhà ăn", "sảnh", "hội trường",
    "sân bóng", "nhà xe", "phòng gym", "tầng 1", "tầng 2",
    "tầng 3", "tầng 4", "tầng 5", "tầng 6", "tầng 7"
]
