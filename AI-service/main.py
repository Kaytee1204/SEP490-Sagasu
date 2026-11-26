import logging
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from src.matcher import SmartMatcher

# Cấu hình log cho AI service để theo dõi thời gian xử lý và tỷ lệ cache hit/miss
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("ai_service")

# Khởi tạo ứng dụng FastAPI đóng vai trò microservice đối sánh đa phương thức (Kiến trúc 3 lớp)
app = FastAPI(
    title="Sagasu AI Multimodal Smart Match Service",
    description=(
        "Kiến trúc 3 lớp (hỗ trợ 50.000+ bài): "
        "Lớp 1 Async Indexing -> Lớp 2 Multi-Channel Vector Recall (Ảnh-Ảnh, Mô tả-Mô tả, Ảnh-Mô tả) "
        "-> Lớp 3 Fine Re-ranking (YOLOv8 + CLIP ViT-B/16 + Multilingual CLIP + HSV 176-dim + Spatio-Temporal + Visual Gating)"
    ),
    version="3.0.0"
)

# Cho phép Frontend / Backend gọi API từ các domain khác nhau
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Khởi tạo Singleton SmartMatcher 1 lần duy nhất khi server bật
# (Giữ sẵn model YOLOv8, CLIP Vision, CLIP Text, PersistentVectorStore và MultimodalVectorIndex trong RAM)
matcher = SmartMatcher()


class ItemData(BaseModel):
    """Schema đại diện cho một bài đăng LOST (Mất đồ) hoặc FOUND (Nhặt được)."""
    id: Optional[Any] = None
    title: Optional[str] = ""
    description: Optional[str] = ""
    categoryName: Optional[str] = ""
    locationText: Optional[str] = ""
    eventAt: Optional[str] = ""
    imageUrl: Optional[str] = None
    # Danh sách toàn bộ URL ảnh của bài đăng (hỗ trợ so sánh TẤT CẢ ảnh với nhau)
    imageUrls: List[str] = Field(default_factory=list)
    # ID tác giả dùng để chặn tuyệt đối việc so sánh đồ do cùng 1 người đăng
    authorId: Optional[Any] = None
    authorName: Optional[str] = None
    # Loại bài đăng: "LOST" hoặc "FOUND" (chỉ so sánh chéo LOST <-> FOUND)
    postType: Optional[str] = None
    status: Optional[str] = "OPEN"


class BulkIndexRequest(BaseModel):
    """Payload đồng bộ hàng loạt nhiều bài đăng vào Vector Index (Lớp 1)."""
    items: List[ItemData] = Field(default_factory=list)


class PruneIndexRequest(BaseModel):
    """Payload xóa hàng loạt các post_id không còn OPEN/VISIBLE khỏi Vector Index."""
    ids: List[Any] = Field(default_factory=list)


class SearchRequest(BaseModel):
    """Payload tìm kiếm 2 tầng trên toàn bộ kho chỉ mục (Lớp 2 Recall -> Lớp 3 Re-rank, tối đa Top 10)."""
    sourceItem: ItemData
    recentCandidates: List[ItemData] = Field(default_factory=list)
    topKPerChannel: int = 60
    topN: int = 10
    includeRejected: bool = False


class MatchRequest(BaseModel):
    """Payload đối sánh trực tiếp gồm 1 bài gốc (sourceItem) và danh sách bài ứng viên (candidates)."""
    sourceItem: ItemData
    candidates: List[ItemData] = Field(default_factory=list)
    topN: int = 10
    includeRejected: bool = False


class MatchResponse(BaseModel):
    """Payload đầu ra trả về danh sách ứng viên đã chấm điểm % trùng khớp và sắp xếp giảm dần."""
    code: int = 200
    message: str = "Success"
    totalCandidates: int = 0
    matches: List[Dict[str, Any]] = Field(default_factory=list)
    telemetry: Optional[Dict[str, Any]] = None


@app.get("/health")
def health_check():
    """Kiểm tra trạng thái hoạt động của server, model AI, cache vector và kho chỉ mục."""
    return {
        "status": "UP",
        "service": "Sagasu AI Service (3-Layer Architecture)",
        "yoloReady": matcher.cropper.model is not None,
        "clipReady": matcher.clip.vision_model is not None and matcher.clip.text_model is not None,
        "vectorStore": matcher.store.stats(),
        "vectorIndex": matcher.index.stats(),
    }


@app.get("/api/cache-stats")
def cache_stats():
    """Trả về thống kê chi tiết số vector đã cache và tổng số bài trong kho chỉ mục."""
    return {
        "code": 200,
        "message": "Persistent Vector Store & Index statistics",
        "data": {
            "vectorStore": matcher.store.stats(),
            "vectorIndex": matcher.index.stats(),
        },
    }


# ══════════════════════════════════════════════════════════════════════════════
# LỚP 1: ASYNC INDEXING ENDPOINTS (Lập chỉ mục bài đăng ngay khi tạo / sửa / xóa)
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/api/index/ids")
def list_indexed_ids():
    """
    LỚP 1: Trả về toàn bộ danh sách post_id hiện có trong Vector Index cùng số lượng LOST / FOUND
    để Backend đồng bộ chính xác phần chênh lệch (không bao giờ bị thiếu bài khi restart giữa chừng).
    """
    return {"code": 200, "message": "Indexed post IDs summary", "data": matcher.index.get_indexed_ids_summary()}


@app.post("/api/index/prune")
def prune_indexed_posts(req: PruneIndexRequest):
    """LỚP 1: Xóa hàng loạt bài đăng đã đóng / trả đồ / ẩn khỏi Vector Index."""
    removed = matcher.index.delete_posts_batch(req.ids)
    return {"code": 200, "message": "Pruned stale posts", "data": {"removed": removed, "totalIndexedPosts": len(matcher.index.posts)}}


@app.post("/api/index/upsert")
def index_single_post(item: ItemData):
    """
    LỚP 1: Backend gọi bất đồng bộ (@Async) ngay khi có bài đăng mới hoặc cập nhật bài.
    AI-service chạy YOLO + CLIP + HSV đúng 1 lần duy nhất và lưu vào MultimodalVectorIndex.
    """
    try:
        info = matcher.index_posts([item.model_dump()])
        return {"code": 200, "message": "Indexed post successfully", "data": info}
    except Exception as ex:
        logger.error(f"Error indexing post {item.id}: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))


@app.post("/api/index/bulk")
def index_bulk_posts(req: BulkIndexRequest):
    """
    LỚP 1: Đồng bộ hàng loạt nhiều bài đang OPEN từ Database sang Vector Index
    (dùng khi khởi động hệ thống lần đầu hoặc đồng bộ lại dữ liệu quy mô lớn).
    """
    try:
        items_dict = [it.model_dump() for it in req.items]
        info = matcher.index_posts(items_dict)
        return {"code": 200, "message": "Bulk indexed posts successfully", "data": info}
    except Exception as ex:
        logger.error(f"Error bulk indexing posts: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))


@app.delete("/api/index/{post_id}")
def delete_indexed_post(post_id: str):
    """
    LỚP 1: Backend gọi khi bài bị xóa, ẩn hoặc đã trả đồ (RETURNED/CLOSED)
    để loại khỏi kho tìm kiếm active ngay lập tức.
    """
    try:
        info = matcher.remove_post(post_id)
        return {"code": 200, "message": "Removed post from index", "data": info}
    except Exception as ex:
        logger.error(f"Error removing post {post_id} from index: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))


# ══════════════════════════════════════════════════════════════════════════════
# LỚP 2 + LỚP 3: SEARCH TRÊN TOÀN BỘ KHO BÀI (RECALL 3 KÊNH -> FINE RE-RANKING)
# ══════════════════════════════════════════════════════════════════════════════

@app.post("/api/search", response_model=MatchResponse)
def search_indexed_items(req: SearchRequest):
    """
    Endpoint chính của kiến trúc 3 lớp:
    - Backend CHỈ gửi duy nhất 1 bài gốc (`sourceItem`), KHÔNG cần gửi hàng chục nghìn bài ứng viên.
    - Lớp 2 (Recall): Quét toàn bộ kho chỉ mục (50.000+ bài, không giới hạn category, khác tác giả,
      ngược loại LOST <-> FOUND) qua 3 kênh (Ảnh-Ảnh, Mô tả-Mô tả, Ảnh-Mô tả) -> ~100-150 ứng viên.
    - Lớp 3 (Re-rank): Chấm điểm tinh bằng HSV + Spatio-Temporal + Visual Gating -> trả Top N bài khớp nhất.
    """
    try:
        source_dict = req.sourceItem.model_dump()
        recent_list = [c.model_dump() for c in req.recentCandidates] if req.recentCandidates else []
        ranked_results, telemetry = matcher.search_indexed(
            source_item=source_dict,
            top_k_per_channel=req.topKPerChannel,
            top_n=req.topN,
            include_rejected=req.includeRejected,
            recent_candidates=recent_list,
        )
        return MatchResponse(
            code=200,
            message="Successfully searched indexed corpus (3-layer pipeline)",
            totalCandidates=len(ranked_results),
            matches=ranked_results,
            telemetry=telemetry,
        )
    except Exception as ex:
        logger.error(f"Error during indexed search: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))


@app.post("/api/match", response_model=MatchResponse)
def match_items(req: MatchRequest):
    """
    Endpoint đối sánh trực tiếp danh sách ứng viên gửi kèm (giữ lại để tương thích ngược / fallback).
    """
    try:
        source_dict = req.sourceItem.model_dump()
        candidates_dict = [c.model_dump() for c in req.candidates]

        ranked_results = matcher.match(
            source_dict,
            candidates_dict,
            include_rejected=req.includeRejected,
            top_n=req.topN,
        )

        return MatchResponse(
            code=200,
            message="Successfully processed multimodal matching",
            totalCandidates=len(ranked_results),
            matches=ranked_results,
        )
    except Exception as ex:
        logger.error(f"Error during matching: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=False)
