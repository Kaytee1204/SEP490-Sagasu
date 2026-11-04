import logging
import math
from typing import Optional, Set
from PIL import Image
from .config import YOLO_MODEL_PATH

logger = logging.getLogger(__name__)

# COCO classes representing portable personal / lost-and-found items
PORTABLE_ITEM_CLASSES: Set[int] = {
    24,  # backpack
    25,  # umbrella
    26,  # handbag
    27,  # tie
    28,  # suitcase
    39,  # bottle
    41,  # cup
    63,  # laptop
    64,  # mouse
    65,  # remote
    66,  # keyboard
    67,  # cell phone
    73,  # book
    74,  # clock
    76,  # scissors
    77,  # teddy bear
}

# COCO classes representing background furniture or person holding the item
BACKGROUND_NOISE_CLASSES: Set[int] = {
    0,   # person
    56,  # chair
    57,  # couch
    58,  # potted plant
    59,  # bed
    60,  # dining table
    61,  # toilet
    62,  # tv
    71,  # sink
    72,  # refrigerator
}


class YOLOCropper:
    def __init__(self, weights_path: str = YOLO_MODEL_PATH):
        self.model = None
        self.weights_path = weights_path
        self._load_model()

    def _load_model(self):
        try:
            from ultralytics import YOLO
            self.model = YOLO(self.weights_path)
            logger.info("YOLOv8 model loaded successfully.")
        except Exception as e:
            logger.warning(f"Could not load YOLOv8 model ({e}). Fallback to full-frame mode.")
            self.model = None

    def crop_salient_object(self, image: Image.Image) -> Image.Image:
        """
        Multi-Object Foreground-Aware Salient Object Cropping:
        - Filters out background tables, chairs, and person bodies when a portable object is present.
        - Scores candidate bounding boxes by: class_priority * confidence * sqrt(area_ratio) * center_weight.
        """
        if self.model is None or image is None:
            return image

        try:
            w, h = image.size
            if w <= 0 or h <= 0:
                return image
            img_area = float(w * h)

            results = self.model(image, verbose=False)
            if not results or len(results) == 0:
                return image

            boxes = results[0].boxes
            if boxes is None or len(boxes) == 0:
                return image

            best_box = None
            best_score = -1.0

            for box in boxes:
                xyxy = box.xyxy[0].tolist()
                conf = float(box.conf[0].item()) if box.conf is not None else 0.5
                cls_id = int(box.cls[0].item()) if box.cls is not None else -1

                bw = max(1.0, xyxy[2] - xyxy[0])
                bh = max(1.0, xyxy[3] - xyxy[1])
                area_ratio = (bw * bh) / img_area

                # Skip tiny noise boxes (< 2% of image area)
                if area_ratio < 0.02:
                    continue

                # Center proximity weight in [0.5, 1.0]
                cx = (xyxy[0] + xyxy[2]) * 0.5 / w
                cy = (xyxy[1] + xyxy[3]) * 0.5 / h
                dist_center = math.hypot(cx - 0.5, cy - 0.5)
                center_weight = max(0.5, 1.0 - 0.6 * dist_center)

                if cls_id in PORTABLE_ITEM_CLASSES:
                    cls_priority = 2.2
                elif cls_id in BACKGROUND_NOISE_CLASSES:
                    cls_priority = 0.15
                else:
                    cls_priority = 1.0

                salience_score = cls_priority * conf * math.sqrt(area_ratio) * center_weight
                if salience_score > best_score:
                    best_score = salience_score
                    best_box = xyxy

            if best_box is not None:
                # Add 6% margin padding around object
                pad_x = (best_box[2] - best_box[0]) * 0.06
                pad_y = (best_box[3] - best_box[1]) * 0.06
                x1 = max(0, int(best_box[0] - pad_x))
                y1 = max(0, int(best_box[1] - pad_y))
                x2 = min(w, int(best_box[2] + pad_x))
                y2 = min(h, int(best_box[3] + pad_y))
                if x2 > x1 + 24 and y2 > y1 + 24:
                    return image.crop((x1, y1, x2, y2))
        except Exception as ex:
            logger.debug(f"Error during YOLO crop: {ex}")

        return image
