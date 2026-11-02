#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
SAGASU AI MATCHING SERVICE - CLI QUERY TEST TOOL
Allows team members to run multimodal matching queries directly from terminal.
"""

import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, Any, List

# Ensure UTF-8 output on Windows terminal
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Ensure AI-service root is on python path
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
    if not METADATA_FILE.exists():
        print(f"[ERROR] Gallery metadata not found: {METADATA_FILE}")
        sys.exit(1)
    with open(METADATA_FILE, "r", encoding="utf-8") as f:
        items = json.load(f)

    # Attach absolute path to imageUrl
    for item in items:
        img_name = item.get("imageFile")
        if img_name:
            local_path = GALLERY_DIR / img_name
            if local_path.exists():
                item["imageUrl"] = str(local_path)
    return items


def load_queries() -> List[Dict[str, Any]]:
    if not QUERIES_FILE.exists():
        return []
    with open(QUERIES_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def print_banner():
    print("=" * 82)
    print("      SAGASU AI MULTIMODAL MATCHING SERVICE - COMMAND LINE TEST RUNNER    ")
    print("  ViT-B/16 + Multilingual CLIP + HSV Color + Spatio-Temporal Campus Gating")
    print("=" * 82)


def print_results_table(query_item: Dict[str, Any], results: List[Dict[str, Any]], top_k: int = 5):
    print("\n🔍 THÔNG TIN TRUY VẤN (QUERY ITEM):")
    print(f"  • Tiêu đề    : {query_item.get('title', 'N/A')}")
    print(f"  • Danh mục   : {query_item.get('categoryName', 'N/A')}")
    print(f"  • Mô tả      : {query_item.get('description', 'N/A')}")
    print(f"  • Địa điểm   : {query_item.get('locationText', 'N/A')}")
    print(f"  • Thời gian  : {query_item.get('eventAt', 'N/A')}")
    img_disp = query_item.get("imageUrl") or "(Không có ảnh - Query bằng Text & Spatio-Temporal)"
    print(f"  • Hình ảnh   : {img_disp}")
    print("-" * 82)

    if not results:
        print("  ⚠️ Không có ứng viên nào vượt qua ngưỡng đối sánh!")
        print("=" * 82)
        return

    print(f"\n📊 KẾT QUẢ ĐỐI SÁNH (TOP {min(top_k, len(results))} CANDIDATES):")
    header = f"{'Hạng':<5} | {'ID':<4} | {'Tổng điểm':<10} | {'Ảnh (55%)':<10} | {'Màu (15%)':<10} | {'Chữ (15%)':<10} | {'Vị trí (15%)':<12} | {'Trạng thái'}"
    print("-" * 92)
    print(header)
    print("-" * 92)

    for idx, item in enumerate(results[:top_k], 1):
        score = item.get("matchScore", 0.0)
        bd = item.get("matchBreakdown", {})
        v_sc = f"{bd.get('visual', 0.0):.1f}%"
        c_sc = f"{bd.get('color', 0.0):.1f}%"
        t_sc = f"{bd.get('text', 0.0):.1f}%"
        st_sc = f"{bd.get('spatioTemporal', 0.0):.1f}%"

        is_rej = item.get("isRejected", False)
        status = "❌ BỊ LOẠI" if is_rej else "✅ ĐẠT"

        rank_str = f"#{idx}" if not is_rej else "-"
        print(f"{rank_str:<5} | {item.get('id', 0):<4} | {score:>7.1f}%   | {v_sc:<10} | {c_sc:<10} | {t_sc:<10} | {st_sc:<12} | {status}")
        print(f"      └─ Tiêu đề: {item.get('title', '')} ({item.get('locationText', '')})")
        print(f"      └─ Lý do: {item.get('matchReason', '')}")
        print("." * 92)

    print("=" * 92)


def main():
    parser = argparse.ArgumentParser(description="Test Sagasu AI matching service via CLI")
    parser.add_argument("--query-id", type=str, help="Predefined query ID (e.g. Q1, Q2, Q3, Q4_TEXT_ONLY, Q5_TEXT_ONLY, or 'list')")
    parser.add_argument("--image", type=str, help="Path to query image file or URL")
    parser.add_argument("--title", type=str, default="", help="Query title")
    parser.add_argument("--desc", type=str, default="", help="Query description")
    parser.add_argument("--category", type=str, default="", help="Category name (e.g. 'Ví / Bóp')")
    parser.add_argument("--location", type=str, default="", help="Location string (e.g. 'Tòa Alpha Canteen')")
    parser.add_argument("--date", type=str, default="", help="Event timestamp (ISO format, e.g. '2026-11-02T10:00:00+07:00')")
    parser.add_argument("--top", type=int, default=5, help="Top K results to display (default: 5)")
    parser.add_argument("--show-rejected", action="store_true", help="Include rejected candidates (<55% visual cutoff) in table")

    args = parser.parse_args()

    print_banner()

    # If user wants to see available predefined queries
    if args.query_id == "list":
        queries = load_queries()
        print("\n📋 DANH SÁCH BỘ TRUY VẤN MẪU (PREDEFINED QUERIES):")
        for q in queries:
            print(f"  • ID: {q['id']:<15} | {q.get('title'):<32} | Kỳ vọng match ID: #{q.get('expectedMatchId')}")
        return

    # 1. Determine Source Query
    query_item = {}
    if args.query_id:
        queries = load_queries()
        match_q = next((q for q in queries if q["id"].upper() == args.query_id.upper()), None)
        if not match_q:
            print(f"[ERROR] Query ID '{args.query_id}' not found. Use '--query-id list' to see available IDs.")
            sys.exit(1)
        query_item = dict(match_q)
        # Resolve image file
        img_name = query_item.get("imageFile")
        if img_name:
            local_img = QUERY_DIR / img_name
            if local_img.exists():
                query_item["imageUrl"] = str(local_img)
    else:
        # Custom input from flags
        query_item = {
            "title": args.title or "Vật phẩm thất lạc",
            "description": args.desc or "",
            "categoryName": args.category or "",
            "locationText": args.location or "",
            "eventAt": args.date or datetime.now().isoformat(),
            "imageUrl": args.image
        }
        if args.image:
            # Check if image is relative to dataset/query or gallery
            p = Path(args.image)
            if not p.is_file():
                if (QUERY_DIR / args.image).is_file():
                    query_item["imageUrl"] = str(QUERY_DIR / args.image)
                elif (GALLERY_DIR / args.image).is_file():
                    query_item["imageUrl"] = str(GALLERY_DIR / args.image)

    # 2. Load Gallery Candidates
    candidates = load_gallery()
    print(f"\n[INFO] Đã nạp {len(candidates)} vật phẩm trong kho lưu trữ (Gallery).")
    print("[INFO] Đang khởi động mô hình AI & tiến hành đối sánh đa phương thức...")

    matcher = SmartMatcher()
    results = matcher.match(query_item, candidates, include_rejected=args.show_rejected)

    # 3. Print Results
    print_results_table(query_item, results, top_k=args.top)


if __name__ == "__main__":
    main()
