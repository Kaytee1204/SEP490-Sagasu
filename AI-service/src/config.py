import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
WEIGHTS_DIR = BASE_DIR / "weights"
WEIGHTS_DIR.mkdir(parents=True, exist_ok=True)

YOLO_MODEL_PATH = os.getenv("YOLO_MODEL_PATH", str(WEIGHTS_DIR / "yolov8n.pt"))
CLIP_VISION_MODEL_NAME = os.getenv("CLIP_VISION_MODEL_NAME", "openai/clip-vit-base-patch16")
CLIP_MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "sentence-transformers/clip-ViT-B-32-multilingual-v1")
DEVICE = os.getenv("DEVICE", "cpu")

# Campus building landmarks for spatial matching
CAMPUS_LOCATIONS = [
    "alpha", "beta", "gamma", "delta", "epsilon",
    "thư viện", "canteen", "nhà ăn", "sảnh", "hội trường",
    "sân bóng", "nhà xe", "phòng gym", "tầng 1", "tầng 2",
    "tầng 3", "tầng 4", "tầng 5", "tầng 6", "tầng 7"
]
