#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
SAGASU AI BENCHMARK & EVALUATION TOOL
Evaluates Top-1 & Top-3 matching accuracy across the benchmark dataset.
Useful for team testing, validation, and graduation thesis (SEP490) reporting.
"""

import json
import os
import sys
import time
from pathlib import Path
from typing import Dict, Any, List

# Ensure UTF-8 output on Windows terminal
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from src.matcher import SmartMatcher

DATASET_DIR = CURRENT_DIR / "dataset"
GALLERY_DIR = DATASET_DIR / "gallery"
QUERY_DIR = DATASET_DIR / "query"
METADATA_FILE = DATASET_DIR / "metadata.json"
QUERIES_FILE = DATASET_DIR / "queries.json"


def load_gallery() -> List[Dict[str, Any]]:
    with open(METADATA_FILE, "r", encoding="utf-8") as f:
        items = json.load(f)
    for item in items:
        img_name = item.get("imageFile")
        if img_name:
            local_path = GALLERY_DIR / img_name
            if local_path.exists():
                item["imageUrl"] = str(local_path)
    return items


def load_queries() -> List[Dict[str, Any]]:
    with open(QUERIES_FILE, "r", encoding="utf-8") as f:
        queries = json.load(f)
    for q in queries:
        img_name = q.get("imageFile")
        if img_name:
            local_path = QUERY_DIR / img_name
            if local_path.exists():
                q["imageUrl"] = str(local_path)
    return queries


def run_benchmark():
    print("=" * 95)
    print("              SAGASU AI MULTIMODAL MATCHING BENCHMARK & ACCURACY EVALUATION              ")
    print("      Testing YOLOv8 Crop + ViT-B/16 + CLIP-Multilingual + HSV + Spatio-Temporal Gating  ")
    print("=" * 95)

    gallery = load_gallery()
    queries = load_queries()

    print(f"\n[INFO] Đã nạp {len(gallery)} vật phẩm trong Gallery và {len(queries)} bài test trong Queries.")
    print("[INFO] Đang khởi động mô hình AI...")

    matcher = SmartMatcher()

    top1_correct = 0
    top3_correct = 0
    total_mrr = 0.0
    latencies = []

    report_rows = []

    print("\n[INFO] Bắt đầu đánh giá từng truy vấn...")
    for q in queries:
        expected_id = q.get("expectedMatchId")
        has_img = q.get("imageUrl") is not None
        modality = "Image + Text + SpaceTime" if has_img else "Text + SpaceTime"

        t_start = time.perf_counter()
        results = matcher.match(q, gallery, include_rejected=False)
        t_elapsed = (time.perf_counter() - t_start) * 1000.0  # ms
        latencies.append(t_elapsed)

        # Check ranking of expected_id
        rank = -1
        top1_id = None
        top1_score = 0.0

        if results:
            top1_id = results[0]["id"]
            top1_score = results[0]["matchScore"]
            for idx, r in enumerate(results, 1):
                if r["id"] == expected_id:
                    rank = idx
                    break

        is_top1 = (rank == 1)
        is_top3 = (1 <= rank <= 3)

        if is_top1:
            top1_correct += 1
            total_mrr += 1.0
        elif rank > 1:
            total_mrr += 1.0 / rank

        if is_top3:
            top3_correct += 1

        status_str = "✅ PASS (Top-1)" if is_top1 else ("⚠️ PASS (Top-3)" if is_top3 else "❌ MISS")

        report_rows.append({
            "id": q.get("id"),
            "title": q.get("title")[:28],
            "modality": modality,
            "expected": expected_id,
            "pred_top1": top1_id if top1_id else "None",
            "top1_score": f"{top1_score:.1f}%",
            "rank": f"#{rank}" if rank > 0 else "N/A",
            "latency": f"{t_elapsed:.0f}ms",
            "status": status_str
        })

    # Print Evaluation Table
    print("\n" + "-" * 95)
    header = f"{'Query ID':<14} | {'Query Title':<28} | {'Modality':<18} | {'Exp':<4} | {'Pred':<4} | {'Score':<7} | {'Rank':<5} | {'Latency':<8} | {'Status'}"
    print(header)
    print("-" * 95)

    for row in report_rows:
        print(f"{row['id']:<14} | {row['title']:<28} | {row['modality']:<18} | {row['expected']:<4} | {row['pred_top1']:<4} | {row['top1_score']:<7} | {row['rank']:<5} | {row['latency']:<8} | {row['status']}")

    print("-" * 95)

    # Summary Metrics
    total = len(queries)
    top1_acc = (top1_correct / total) * 100.0 if total > 0 else 0.0
    top3_acc = (top3_correct / total) * 100.0 if total > 0 else 0.0
    mrr = (total_mrr / total) if total > 0 else 0.0
    avg_latency = sum(latencies) / len(latencies) if latencies else 0.0

    print("\n📈 KẾT QUẢ ĐÁNH GIÁ TỔNG THỂ (OVERALL BENCHMARK METRICS):")
    print(f"  • Tổng số truy vấn kiểm thử (Total Queries)   : {total}")
    print(f"  • Độ chính xác Top-1 (Top-1 Accuracy)        : {top1_acc:.1f}% ({top1_correct}/{total})")
    print(f"  • Độ chính xác Top-3 (Top-3 Accuracy)        : {top3_acc:.1f}% ({top3_correct}/{total})")
    print(f"  • Điểm xếp hạng tương hỗ (MRR - Mean RR)      : {mrr:.3f}")
    print(f"  • Thời gian xử lý trung bình / truy vấn      : {avg_latency:.1f} ms")
    print("=" * 95)


if __name__ == "__main__":
    run_benchmark()
