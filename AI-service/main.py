import logging
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from src.matcher import SmartMatcher

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("ai_service")

app = FastAPI(
    title="Sagasu AI Multimodal Smart Match Service",
    description="Pipeline: YOLOv8 Smart Crop + ViT/CLIP (Vision & Vietnamese Text) + HSV + Spatio-Temporal + Visual Gating",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

matcher = SmartMatcher()

class ItemData(BaseModel):
    id: Optional[Any] = None
    title: Optional[str] = ""
    description: Optional[str] = ""
    categoryName: Optional[str] = ""
    locationText: Optional[str] = ""
    eventAt: Optional[str] = ""
    imageUrl: Optional[str] = None
    authorName: Optional[str] = None
    postType: Optional[str] = None

class MatchRequest(BaseModel):
    sourceItem: ItemData
    candidates: List[ItemData] = Field(default_factory=list)

class MatchResponse(BaseModel):
    code: int = 200
    message: str = "Success"
    totalCandidates: int = 0
    matches: List[Dict[str, Any]] = Field(default_factory=list)

@app.get("/health")
def health_check():
    return {
        "status": "UP",
        "service": "Sagasu AI Service",
        "yoloReady": matcher.cropper.model is not None,
        "clipReady": matcher.clip.model is not None
    }

@app.post("/api/match", response_model=MatchResponse)
def match_items(req: MatchRequest):
    try:
        source_dict = req.sourceItem.model_dump()
        candidates_dict = [c.model_dump() for c in req.candidates]

        ranked_results = matcher.match(source_dict, candidates_dict)

        return MatchResponse(
            code=200,
            message="Successfully processed multimodal matching",
            totalCandidates=len(ranked_results),
            matches=ranked_results
        )
    except Exception as ex:
        logger.error(f"Error during matching: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
