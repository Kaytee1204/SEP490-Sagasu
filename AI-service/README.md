# Sagasu AI Multimodal Smart Match Service

Dịch vụ AI so khớp bài đăng thất lạc thông minh đa phương thức (Multimodal Smart Match) dành cho hệ thống **Sagasu**.

---

### Kiến trúc Pipeline
1. **YOLOv8 Smart Crop (`yolov8n.pt`):** Tự động phát hiện và crop bounding box của vật thể chính trong ảnh, loại bỏ nhiễu nền (mặt bàn, sàn nhà, người cầm).
2. **Multilingual CLIP ViT (`clip-ViT-B-32-multilingual-v1`):** Trích xuất đặc trưng thị giác (Vision Transformer) và biểu diễn ngữ nghĩa văn bản Tiếng Việt trong cùng không gian vector 512 chiều.
3. **HSV Color Space:** Trích xuất lược đồ màu 2D Hue-Saturation, tính hệ số tương đồng Bhattacharyya để loại bỏ hiện tượng sai lệch màu sắc.
4. **Spatio-Temporal Scorer:** Hàm suy giảm theo khoảng cách thời gian và độ trùng khớp địa danh khuôn viên trường học (tòa nhà, canteen, thư viện).
5. **Adaptive Visual Gating Layer:** Cân bằng động trọng số giữa 4 phương thức (Hình ảnh, Màu sắc, Chữ viết, Thời gian & Địa điểm).

---

### Cài đặt và Khởi chạy

```bash
# 1. Di chuyển vào thư mục AI-service
cd AI-service

# 2. Tạo môi trường ảo (khuyên dùng)
python -m venv venv
venv\Scripts\activate   # Trên Windows

# 3. Cài đặt các thư viện cần thiết
pip install -r requirements.txt

# 4. Khởi chạy FastAPI Server (Port 8000)
python main.py
```

* Swagger UI tài liệu API: [http://localhost:8000/docs](http://localhost:8000/docs)
* Endpoint kiểm tra sức khỏe: [http://localhost:8000/health](http://localhost:8000/health)
* Endpoint so khớp: `POST http://localhost:8000/api/match`
#5 Run-cop dòng này để chạy
.\venv\Scripts\python.exe main.py