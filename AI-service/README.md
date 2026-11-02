# Hướng Dẫn Sử Dụng Sagasu AI Service & Bộ Test Query CLI

Dành cho các thành viên trong nhóm đồ án tốt nghiệp **Sagasu (SEP490)** để kiểm thử mô hình đối sánh đa phương thức (Multimodal Matching: ViT-B/16 + CLIP Multilingual + HSV Histogram + Spatio-Temporal Campus Gating).

---

## 1. Cài đặt môi trường (Dành cho thành viên mới)

Mở terminal tại thư mục `AI-service`:

```bash
cd AI-service
python -m venv venv
# Kích hoạt môi trường ảo:
# Trên Windows (PowerShell):
.\venv\Scripts\Activate.ps1
# Hoặc trên Windows (CMD):
.\venv\Scripts\activate.bat

# Cài đặt các thư viện cần thiết:
pip install -r requirements.txt
```

---

## 2. Các câu lệnh Query Test nhanh (CLI)

### 2.1 Xem danh sách các bài test mẫu có sẵn
```bash
python query_test.py --query-id list
```

### 2.2 Chạy test theo ID bài mẫu (Predefined Query)
```bash
# Test tìm ví Pedro màu đen (có ảnh query + text + địa điểm Căn tin Alpha)
python query_test.py --query-id Q1

# Test tìm balo laptop FPT (ảnh balo + text + Thư viện Alpha)
python query_test.py --query-id Q2

# Test tìm bình giữ nhiệt Lock&Lock xanh thể thao (ảnh bình nước + Sân bóng)
python query_test.py --query-id Q3

# Test thuần Text (Không có ảnh, tìm bằng ngữ nghĩa "ví da tối màu" vs "ví da màu đen")
python query_test.py --query-id Q4_TEXT_ONLY

# Test thuần Text tìm chìa khóa smartkey ở hầm xe Gamma
python query_test.py --query-id Q5_TEXT_ONLY
```

### 2.3 Xem cả các ứng viên bị loại bởi Hard Cutoff (Ảnh dưới 55%)
Thêm cờ `--show-rejected`:
```bash
python query_test.py --query-id Q1 --show-rejected
```

### 2.4 Tự gõ một câu lệnh Query bất kỳ với dữ liệu tùy chọn
Bất kỳ thành viên nào cũng có thể truyền ảnh hoặc thông tin mình muốn:
```bash
# Query bằng ảnh bất kỳ (đường dẫn file ảnh trên máy hoặc trong thư mục dataset):
python query_test.py --image "dataset/query/vi_da_pedro_den_query.jpg" --title "Ví da Pedro đen" --location "Tòa Alpha canteen" --category "Ví / Bóp"

# Query thuần mô tả chữ và địa điểm trong khuôn viên trường FPT:
python query_test.py --title "Balo laptop" --desc "Rơi balo màu đen ở bàn tự học thư viện tầng 2" --location "Thư viện Alpha" --category "Balo / Cặp sách"
```

---

## 3. Chạy Đánh giá Toàn diện (Benchmark & Accuracy Report)

Chạy script tự động đánh giá độ chính xác (Top-1, Top-3, MRR, thời gian xử lý trung bình) trên toàn bộ dataset:

```bash
python benchmark.py
```

Kết quả sẽ xuất ra bảng đánh giá chi tiết phục vụ báo cáo tiến độ và slide bảo vệ đồ án tốt nghiệp.

---

## 4. Cách thêm dữ liệu ảnh / bài đăng mới vào Dataset

1. **Thêm ảnh vào kho (Gallery - đồ nhặt được):**
   - Copy file ảnh vào `dataset/gallery/` (ví dụ: `tai_nghe_airpods_1.jpg`).
   - Mở `dataset/metadata.json`, thêm 1 object tương ứng:
     ```json
     {
       "id": 7,
       "imageFile": "tai_nghe_airpods_1.jpg",
       "title": "Tai nghe AirPods Pro màu trắng",
       "categoryName": "Thiết bị điện tử",
       "description": "Hộp sạc tai nghe không dây AirPods Pro, có ốp bảo vệ silicon màu đỏ",
       "locationText": "Phòng 302 Tòa Delta",
       "eventAt": "2026-11-02T14:00:00+07:00",
       "postType": "FOUND"
     }
     ```

2. **Thêm bài query mẫu để test:**
   - Copy ảnh query vào `dataset/query/` (nếu có ảnh).
   - Thêm vào file `dataset/queries.json` kèm `expectedMatchId` (ID của bài trong kho mà bạn muốn AI tìm ra).
