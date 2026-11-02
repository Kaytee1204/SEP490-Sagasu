import logging
from typing import Optional
from PIL import Image
from .config import YOLO_MODEL_PATH

logger = logging.getLogger(__name__)

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
        Detects primary object bounding box using YOLOv8 and crops it.
        Removes background tables, floors, hands, etc.
        """
        if self.model is None or image is None:
            return image

        try:
            results = self.model(image, verbose=False)
            if not results or len(results) == 0:
                return image

            boxes = results[0].boxes
            if boxes is None or len(boxes) == 0:
                return image

            # Select the largest bounding box or highest confidence
            best_box = None
            max_area = 0
            for box in boxes:
                xyxy = box.xyxy[0].tolist()
                area = (xyxy[2] - xyxy[0]) * (xyxy[3] - xyxy[1])
                if area > max_area:
                    max_area = area
                    best_box = xyxy

            if best_box:
                w, h = image.size
                # Add 5% margin padding around object
                pad_x = (best_box[2] - best_box[0]) * 0.05
                pad_y = (best_box[3] - best_box[1]) * 0.05
                x1 = max(0, int(best_box[0] - pad_x))
                y1 = max(0, int(best_box[1] - pad_y))
                x2 = min(w, int(best_box[2] + pad_x))
                y2 = min(h, int(best_box[3] + pad_y))
                if x2 > x1 + 10 and y2 > y1 + 10:
                    return image.crop((x1, y1, x2, y2))
        except Exception as ex:
            logger.debug(f"Error during YOLO crop: {ex}")

        return image
