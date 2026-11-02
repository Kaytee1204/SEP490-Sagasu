from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

DATASET_DIR = Path(__file__).resolve().parent
GALLERY_DIR = DATASET_DIR / "gallery"
QUERY_DIR = DATASET_DIR / "query"

GALLERY_DIR.mkdir(parents=True, exist_ok=True)
QUERY_DIR.mkdir(parents=True, exist_ok=True)

def create_item_image(path: Path, bg_color: tuple, shape_type: str, item_color: tuple, label: str):
    img = Image.new("RGB", (320, 320), bg_color)
    draw = ImageDraw.Draw(img)

    if shape_type == "wallet":
        # Draw wallet rectangle
        draw.rounded_rectangle([60, 90, 260, 230], radius=16, fill=item_color, outline=(40, 40, 40), width=4)
        draw.line([60, 160, 260, 160], fill=(70, 70, 70), width=2)
        draw.rectangle([210, 140, 245, 180], fill=(210, 180, 140), outline=(50, 50, 50), width=2)
    elif shape_type == "backpack":
        # Draw backpack shape
        draw.rounded_rectangle([70, 60, 250, 280], radius=30, fill=item_color, outline=(30, 30, 30), width=4)
        draw.rounded_rectangle([90, 160, 230, 260], radius=14, fill=(50, 50, 60), outline=(20, 20, 20), width=3)
        draw.ellipse([140, 195, 180, 225], fill=(240, 100, 30))  # Orange FPT logo circle
    elif shape_type == "water_bottle":
        # Draw water bottle
        draw.rounded_rectangle([110, 80, 210, 280], radius=20, fill=item_color, outline=(20, 50, 80), width=4)
        draw.rectangle([130, 40, 190, 80], fill=(200, 200, 200), outline=(50, 50, 50), width=3)
    elif shape_type == "keys":
        # Draw key ring & keys
        draw.ellipse([90, 80, 170, 160], outline=(180, 180, 190), width=8)
        draw.rectangle([140, 140, 160, 270], fill=item_color, outline=(50, 50, 50), width=3)
        draw.rectangle([160, 200, 185, 220], fill=item_color)
        draw.rectangle([160, 240, 180, 260], fill=item_color)
    elif shape_type == "id_card":
        # Draw student card
        draw.rounded_rectangle([50, 90, 270, 230], radius=10, fill=item_color, outline=(200, 200, 200), width=3)
        draw.rectangle([70, 115, 125, 185], fill=(70, 130, 180))  # Avatar placeholder
        draw.line([145, 130, 250, 130], fill=(50, 50, 50), width=4)
        draw.line([145, 155, 230, 155], fill=(100, 100, 100), width=3)
        draw.line([145, 175, 210, 175], fill=(230, 100, 30), width=3)  # FPT orange code
    else:
        draw.rectangle([80, 80, 240, 240], fill=item_color, outline=(50, 50, 50), width=3)

    # Text label watermark
    draw.text((20, 290), label, fill=(120, 120, 120))
    img.save(path, quality=90)

if __name__ == "__main__":
    # 1. Gallery Items (Kho đồ nhặt được)
    create_item_image(GALLERY_DIR / "vi_da_pedro_den_1.jpg", (245, 245, 245), "wallet", (25, 25, 25), "Ví da Pedro đen #1")
    create_item_image(GALLERY_DIR / "vi_da_bo_nau_1.jpg", (240, 240, 240), "wallet", (110, 60, 30), "Ví da bò nâu #1")
    create_item_image(GALLERY_DIR / "balo_fpt_den_1.jpg", (250, 250, 250), "backpack", (35, 35, 45), "Balo laptop FPT đen #1")
    create_item_image(GALLERY_DIR / "binh_nuoc_xanh_1.jpg", (245, 245, 250), "water_bottle", (30, 140, 200), "Bình Lock&Lock xanh #1")
    create_item_image(GALLERY_DIR / "chia_khoa_honda_1.jpg", (240, 245, 240), "keys", (160, 165, 170), "Chìa khóa Honda #1")
    create_item_image(GALLERY_DIR / "the_sinh_vien_fpt_1.jpg", (250, 250, 250), "id_card", (255, 255, 255), "Thẻ sinh viên FPT #1")

    # 2. Query Items (Đồ làm mất để tìm kiếm)
    create_item_image(QUERY_DIR / "vi_da_pedro_den_query.jpg", (230, 230, 230), "wallet", (30, 30, 30), "Query: Ví da Pedro đen")
    create_item_image(QUERY_DIR / "balo_fpt_den_query.jpg", (235, 235, 240), "backpack", (40, 40, 50), "Query: Balo FPT đen")
    create_item_image(QUERY_DIR / "binh_nuoc_xanh_query.jpg", (240, 240, 245), "water_bottle", (35, 145, 210), "Query: Bình Lock&Lock xanh")

    print("Sample dataset images initialized successfully!")
