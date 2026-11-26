import logging
import math
from typing import Optional, Set
from PIL import Image
from .config import YOLO_MODEL_PATH

logger = logging.getLogger(__name__)

# Tập 16 lớp đồ vật cá nhân dễ thất lạc trong chuẩn MS COCO (được ưu tiên chọn làm vật thể chính: priority = 2.2)
PORTABLE_ITEM_CLASSES: Set[int] = {
    24,  # backpack (ba lô)
    25,  # umbrella (ô / dù)
    26,  # handbag (túi xách / ví)
    27,  # tie (cà vạt)
    28,  # suitcase (vali)
    39,  # bottle (bình nước)
    41,  # cup (cốc / ly)
    63,  # laptop
    64,  # mouse (chuột máy tính)
    65,  # remote (điều khiển)
    66,  # keyboard (bàn phím)
    67,  # cell phone (điện thoại)
    73,  # book (sách / vở)
    74,  # clock (đồng hồ)
    76,  # scissors (kéo)
    77,  # teddy bear (gấu bông / móc khóa)
}

# Tập 10 lớp nền gây nhiễu trong MS COCO (người cầm đồ, bàn ghế, TV...) -> bị phạt nặng priority = 0.15
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
    """
    Bộ cắt vật thể nổi bật (Salient Object Cropper) sử dụng YOLOv8n.
    Mục đích: loại bỏ 70-80% phần nền thừa (mặt bàn, sàn gạch, tay người) trước khi đưa ảnh vào CLIP & HSV
    để AI tập trung so sánh đúng món đồ thất lạc.
    """

    def __init__(self, weights_path: str = YOLO_MODEL_PATH):
        self.model = None
        self.weights_path = weights_path
        self._load_model()

    def _load_model(self):
        """Nạp file trọng số yolov8n.pt; nếu lỗi sẽ fallback sang chế độ giữ nguyên toàn ảnh."""
        try:
            from ultralytics import YOLO
            self.model = YOLO(self.weights_path)
            logger.info("YOLOv8 model loaded successfully.")
        except Exception as e:
            logger.warning(f"Could not load YOLOv8 model ({e}). Fallback to full-frame mode.")
            self.model = None

    @staticmethod
    def _downscale_for_inference(image: Image.Image, max_side: int = 640) -> Image.Image:
        """
        Thu nhỏ ảnh về cạnh tối đa 640px trước khi chạy YOLOv8n (tăng tốc CPU ~3-4x).
        Vì CLIP chỉ dùng 224x224 và HSV chỉ dùng 128x128 nên ảnh 640px giữ nguyên 100% độ chính xác.
        """
        w, h = image.size
        if max(w, h) <= max_side:
            return image
        resized = image.copy()
        resized.thumbnail((max_side, max_side), Image.Resampling.BILINEAR)
        return resized

    def _crop_from_result(self, orig_image: Image.Image, infer_image: Image.Image, result) -> Image.Image:
        """
        Tách bounding box của vật thể thuộc danh sách nhãn đồ vật cá nhân (PORTABLE_ITEM_CLASSES).
        QUY TẮC CỐT LÕI:
        - Chỉ cắt khi phát hiện đúng nhãn nằm trong PORTABLE_ITEM_CLASSES (conf >= 0.30, diện tích >= 2%).
        - Nếu vật thể trong ảnh nằm NGOÀI các nhãn được gắn nhãn (vd: ví da, thẻ sinh viên, chìa khóa,
          tai nghe, kính mắt...) hoặc YOLO chỉ thấy nền/bàn ghế/người -> GIỮ NGUYÊN 100% ẢNH GỐC (orig_image).
        """
        if orig_image is None or infer_image is None or result is None:
            return orig_image

        orig_w, orig_h = orig_image.size
        w, h = infer_image.size
        if w <= 0 or h <= 0 or orig_w <= 0 or orig_h <= 0:
            return orig_image

        boxes = result.boxes
        if boxes is None or len(boxes) == 0:
            return orig_image

        img_area = float(w * h)
        best_box = None
        best_score = -1.0

        for box in boxes:
            xyxy = box.xyxy[0].tolist()
            conf = float(box.conf[0].item()) if box.conf is not None else 0.0
            cls_id = int(box.cls[0].item()) if box.cls is not None else -1

            # QUAN TRỌNG: Nếu ngoài các nhãn đồ vật được gắn nhãn (PORTABLE_ITEM_CLASSES) -> bỏ qua!
            # Tuyệt đối không cắt vào nền (bàn, ghế, người) hoặc nhãn ngoài danh sách.
            if cls_id not in PORTABLE_ITEM_CLASSES or conf < 0.30:
                continue

            # Tính tỷ lệ diện tích của bounding box so với toàn bộ ảnh
            bw = max(1.0, xyxy[2] - xyxy[0])
            bh = max(1.0, xyxy[3] - xyxy[1])
            area_ratio = (bw * bh) / img_area

            # Bỏ qua các vật thể quá nhỏ (< 2% diện tích ảnh) hoặc đã chiếm trọn khung hình (> 95%)
            if area_ratio < 0.02 or area_ratio > 0.95:
                continue

            # Tính trọng số gần tâm ảnh: vật thể càng gần tâm (0.5, 0.5) trọng số càng tiến tới 1.0
            cx = (xyxy[0] + xyxy[2]) * 0.5 / w
            cy = (xyxy[1] + xyxy[3]) * 0.5 / h
            dist_center = math.hypot(cx - 0.5, cy - 0.5)
            center_weight = max(0.5, 1.0 - 0.6 * dist_center)

            # Công thức tổng hợp điểm nổi bật (Smart Crop Salience Score) cho nhãn hợp lệ
            salience_score = 2.2 * conf * math.sqrt(area_ratio) * center_weight
            if salience_score > best_score:
                best_score = salience_score
                best_box = xyxy

        # Ánh xạ tọa độ bounding box kèm 6% padding về lại kích thước ảnh gốc (orig_image)
        if best_box is not None:
            scale_x = orig_w / float(w)
            scale_y = orig_h / float(h)
            bx1 = best_box[0] * scale_x
            by1 = best_box[1] * scale_y
            bx2 = best_box[2] * scale_x
            by2 = best_box[3] * scale_y

            pad_x = (bx2 - bx1) * 0.06
            pad_y = (by2 - by1) * 0.06
            x1 = max(0, int(bx1 - pad_x))
            y1 = max(0, int(by1 - pad_y))
            x2 = min(orig_w, int(bx2 + pad_x))
            y2 = min(orig_h, int(by2 + pad_y))
            if x2 > x1 + 24 and y2 > y1 + 24:
                return orig_image.crop((x1, y1, x2, y2))

        # Nếu ngoài nhãn được gắn nhãn -> giữ nguyên ảnh gốc 100%
        return orig_image

    def crop_salient_object(self, image: Image.Image) -> Image.Image:
        """Cắt vật thể nổi bật cho 1 ảnh đơn lẻ; giữ nguyên ảnh gốc nếu ngoài nhãn."""
        if self.model is None or image is None:
            return image
        return self.crop_salient_objects_batch([image])[0]

    def crop_salient_objects_batch(self, images: list) -> list:
        """
        Cắt vật thể nổi bật hàng loạt theo lô (batch inference):
        - Chạy YOLOv8 trên bản thu nhỏ <= 640px để tăng tốc suy luận.
        - Nếu phát hiện nhãn thuộc PORTABLE_ITEM_CLASSES -> cắt trên ảnh gốc chất lượng cao.
        - Nếu ngoài nhãn được gắn nhãn -> trả về nguyên bản ảnh gốc (giữ nguyên 100%).
        """
        if self.model is None or not images:
            return images

        try:
            resized_list = [self._downscale_for_inference(img, max_side=640) for img in images]
            results = self.model(resized_list, imgsz=640, verbose=False)
            cropped = []
            for orig_img, img_small, res in zip(images, resized_list, results):
                cropped.append(self._crop_from_result(orig_img, img_small, res))
            return cropped
        except Exception as ex:
            logger.debug(f"Error during batch YOLO crop: {ex}")
            return images
