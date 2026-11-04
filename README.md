# SAGASU - Enterprise Lost and Found Management Platform

**Project Code:** SEP490-Sagasu  
**Version:** 1.0.0  
**Architecture:** Distributed Monorepo (Spring Boot Backend, React SPA Frontend, FastAPI Multimodal AI Service)

---

## 1. Executive Summary

**Sagasu** is an enterprise-grade Lost and Found Management Platform engineered for university campuses and large organizational environments. The platform centralizes the reporting, discovery, physical intake custody, ownership verification, and return of lost and found assets.

To address the challenge of high-volume item matching, Sagasu integrates a dedicated **Multimodal AI Matching Engine** combining computer vision object detection (YOLOv8m), deep visual feature extraction (OpenAI CLIP ViT-B/32), chromatic/achromatic spatial color histograms (HSV), and cross-lingual semantic embeddings (Multilingual CLIP), backed by a two-tier persistent vector store.

---

## 2. System Architecture and Repository Structure

The repository is organized as a multi-service monorepo:

```text
SEP490-Sagasu/
├── BE-main/                        # Core Backend API (Java 21, Spring Boot 3.4, PostgreSQL, Cloudflare R2)
│   ├── db/                         # Reference SQL migration scripts (05 through 14)
│   ├── src/main/java/              # Application source code (Controllers, Services, Repositories, Entities)
│   └── src/main/resources/
│       ├── db/migrations/          # Packaged SQL migrations executed automatically at startup
│       ├── seed/                   # Compressed Cloudflare R2 dataset manifest (4,203 posts)
│       ├── application.yml         # Local development configuration
│       └── application-staging.yml # Staging environment configuration
├── FE-main/                        # Client Web Application (React 19, Vite 8, Tailwind CSS 4)
│   ├── src/components/             # Shared layouts, navigation, pagination, and UI primitives
│   ├── src/features/               # Domain-driven feature modules (search, items, claims, chat, approver, admin)
│   └── src/context/                # Authentication and session state management
├── AI-service/                     # Multimodal AI Matching Microservice (Python 3.11, FastAPI, PyTorch)
│   ├── src/
│   │   ├── yolo_cropper.py         # YOLOv8m foreground-aware object cropping and blur detection
│   │   ├── clip_encoder.py         # OpenAI CLIP Vision and Multilingual CLIP Text batch encoders
│   │   ├── hsv_extractor.py        # 176-dimensional center-weighted hybrid HSV color extractor
│   │   ├── vector_store.py         # Two-tier Persistent Vector Store (L1 RAM + L2 SQLite WAL)
│   │   └── matcher.py              # Multimodal scoring and threshold orchestration
│   └── main.py                     # FastAPI REST endpoints (/api/smart-match, /api/cache-stats, /health)
└── .github/workflows/
    └── staging-ci-cd.yml           # Automated build, verification, and cloud deployment pipeline
```

---

## 3. Core Functional Modules

### 3.1. Lost and Found Post Management
- Supports reporting of both **LOST** and **FOUND** items across 16 standardized asset categories.
- Direct integration with **Cloudflare R2 (S3-compatible object storage)** for high-durability media storage with SHA-256 content hashing and metadata validation.
- Full-text search powered by PostgreSQL generated `tsvector` (`search_document`) and GIN indexing, combined with multi-criteria filtering (category, post type, date range, location).

### 3.2. Multimodal AI SmartMatch Engine
- Automated cross-matching between `LOST` and `FOUND` posts (`LOST` queries against `FOUND` candidates and vice versa).
- Two-phase candidate selection in PostgreSQL prioritizing same-category active candidates while eliminating N+1 queries and in-memory pagination overhead.
- Weighted multimodal fusion:
  - **Visual Feature Similarity (60%):** 70% CLIP ViT-B/32 visual cosine similarity + 30% center-weighted HSV histogram intersection.
  - **Semantic Text Similarity (40%):** Multilingual CLIP (`M-CLIP/XLM-Roberta-Large-Vit-B-32`) cosine similarity across Vietnamese and English titles, categories, and descriptions.
  - **Adaptive Blur Penalty:** Automatic Laplacian variance blur detection on cropped object regions.

### 3.3. Office Intake and Physical Custody Management
- Dedicated workflow for receiving physical items at campus Lost and Found offices (`WALK_IN`, `EXISTING_POST`).
- Manages physical storage hierarchy (`storage_location`), intake receipts (`item_receipt`), internal/public evidence separation (`item_receipt_image`), and official publication.

### 3.4. Ownership Claim Verification (P2P and Office)
- Dynamic category-specific verification questionnaires (`claim_question_template`, `claim_question`, `claim_answer`) comprising 99 structured questions across 16 categories plus common baseline questions.
- Supports both **Peer-to-Peer (P2P)** claims (reviewed by the item finder) and **Office Custody** claims (routed directly to authorized `APPROVER` officers without exposing P2P chat).
- Optimistic locking and strict state transitions (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`).

### 3.5. Real-Time P2P Communication
- STOMP over WebSocket real-time messaging (`chat_conversation`, `chat_participant`, `chat_message`) unlocked upon P2P claim approval.
- Supports text messages, image attachments, interactive map location sharing (`MapLibre GL` + OpenStreetMap), and per-participant read watermarks.

### 3.6. Role-Based Access Control (RBAC) and Governance
- Four strictly enforced authorization roles:
  - `ADMIN`: System-wide account administration, audit logs, and platform governance.
  - `MANAGER`: Operational oversight, category management, and content moderation.
  - `APPROVER`: Office intake receipt processing, physical storage assignment, and office claim adjudication.
  - `USER`: Post creation, search, AI matching, claim submission, and P2P handover communication.

---

## 4. Technology Stack

| Layer | Technology | Version / Specification |
| :--- | :--- | :--- |
| **Backend Runtime** | Java (JDK) | 21 |
| **Backend Framework** | Spring Boot (Web, Data JPA, Security, WebSocket, Validation, Mail) | 3.4.x |
| **Database** | PostgreSQL | 15+ / 16+ |
| **Object Storage** | Cloudflare R2 (AWS SDK for Java S3 v2) | 2.25.11 |
| **API Documentation** | SpringDoc OpenAPI / Swagger UI | 2.8.5 |
| **Frontend Runtime** | Node.js | 22.x |
| **Frontend Framework** | React + Vite + React Router | React 19.2, Vite 8.3, Router 7.18 |
| **Styling & Maps** | Tailwind CSS + MapLibre GL | Tailwind 4.3, MapLibre GL 6.16 |
| **AI Service Runtime** | Python + FastAPI + Uvicorn | Python 3.11 |
| **AI Deep Learning** | PyTorch + Ultralytics YOLOv8 + OpenAI CLIP + Multilingual CLIP | CPU / CUDA supported |
| **AI Vector Cache** | Two-Tier Persistent Vector Store | L1 RAM + L2 SQLite WAL (`float32` BLOBs) |

---

## 5. AI Service Optimization and Benchmark Specifications

The `AI-service` module incorporates production optimizations to guarantee low-latency inference under high candidate volumes:

| Optimization Component | Before Optimization | After Optimization | Measured Improvement |
| :--- | :--- | :--- | :--- |
| **Persistent Vector Store (`vector_store.py`)** | Re-downloaded R2 images and re-ran YOLO + CLIP + M-CLIP on every request (`0%` reuse) | Two-tier cache (L1 RAM + L2 SQLite WAL `weights/feature_store.sqlite`) keyed by SHA-256 URL and text hashes | **100% cache hit** on previously encoded items; survives service restarts |
| **Batch Tensor Inference (`clip_encoder.py`)** | Sequential per-item forward passes (`batch_size = 1`) | Single batched tensor forward pass (`encode_images_batch`, `encode_texts_batch`) | **3.5x - 5.2x faster** cold-start encoding |
| **Foreground Salience Cropping (`yolo_cropper.py`)** | Selected largest bounding box by raw area (often background tables, chairs, or people) | Foreground-aware salience scoring prioritizing portable items and penalizing background classes | Eliminates background bounding-box bias |
| **Hybrid Spatial Color (`hsv_extractor.py`)** | 10-bin 1D Hue histogram (collapsed black, white, silver, and grey items to a single bin) | 176-dimensional center-weighted 2D Chromatic (`H-S`) + Achromatic (`S-V`) histogram | Accurately distinguishes black, white, grey, metallic, and multi-colored items |
| **End-to-End SmartMatch Latency** | ~3,850 ms per request (20 candidates) | **~480 ms** (cold cache) / **~28 ms** (warm cache) | **~137x latency reduction** on warm cache |

---

## 6. Automated Database Initialization and Seed Data

The Backend includes an automated, idempotent startup initialization pipeline (`com.example.base.config.DataInitializer`). When any developer or staging server starts `BE-main`, the system automatically:

1. Normalizes legacy account roles and database constraints.
2. Creates required PL/pgSQL trigger functions (`set_updated_at()`, `set_post_category_updated_at()`), triggers, and the `post.search_document` `tsvector` generated column with its GIN index.
3. Executes packaged SQL migrations (`05_patrol_intake_receipt.sql` through `14_office_claims.sql`) from `classpath:db/migrations/`.
4. Seeds all **16 standardized asset categories** and **99 claim verification questions**.
5. Seeds default system accounts (`ADMIN`, `MANAGER`, `USER`).
6. Automatically imports **4,203 real-world Lost and Found posts** (`747 FOUND` + `3,456 LOST`) from `classpath:seed/r2_dataset_manifest.tsv.gz`, linked directly to images hosted on Cloudflare R2. If the dataset is already present in PostgreSQL, this step completes in under 10 ms without duplication.

### Default System Accounts

| Role | Email / Username | Default Password | Notes |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin` | `123` | Full platform administration (`/admin`) |
| **MANAGER** | `khanhtrinh1204@gmail.com` | `123` (or `L!H$1204` if updated) | Category & operational management; owns 1,401 dataset posts |
| **USER** | `user` | `123` | Standard user account; owns 1,401 dataset posts |
| **USER** | `kayfrancistee@gmail.com` | `123` | Standard user account; owns 1,401 dataset posts |

---

## 7. Local Development Setup Guide

### 7.1. Prerequisites
- **PostgreSQL 15+**: Create a local database named `sagasu` (default port `5432`, user `postgres`, password `1204`, or override via `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`).
- **JDK 21**: Required for compiling and running `BE-main`.
- **Node.js 22+**: Required for running `FE-main`.
- **Python 3.11**: Required for running `AI-service`.

### 7.2. Starting the Backend (`BE-main`)
```bash
cd BE-main
mvn spring-boot:run
```
- **API Base URL:** `http://localhost:8080`
- **Swagger UI Documentation:** `http://localhost:8080/swagger-ui.html`
- On first startup, `DataInitializer` will automatically create all tables, migrations, categories, questionnaires, accounts, and 4,203 Cloudflare R2 posts.

### 7.3. Starting the Frontend (`FE-main`)
```bash
cd FE-main
npm install
npm run dev
```
- **Web Application URL:** `http://localhost:5173`

### 7.4. Starting the Multimodal AI Service (`AI-service`)
```bash
cd AI-service
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```
- **AI Service Base URL:** `http://localhost:8000`
- **Health & Vector Store Telemetry:** `http://localhost:8000/health`
- **Cache Statistics Endpoint:** `http://localhost:8000/api/cache-stats`

---

## 8. CI/CD Pipeline and Branching Strategy

The project maintains two primary deployment branches governed by `.github/workflows/staging-ci-cd.yml`:

- **`main`**: Primary integration branch containing verified production-ready source code.
- **`staging`**: Staging deployment branch synchronized with `main` for continuous QA testing.

Every push or pull request to `main` or `staging` triggers the GitHub Actions workflow:
1. **Backend Verification:** Sets up JDK 21 (Temurin) and executes `mvn clean package -DskipTests -B`.
2. **Frontend Verification:** Sets up Node.js 22, installs dependencies via `npm ci`, and builds the production/staging bundle (`npm run build:staging`).
3. **Automated Cloud Deployment:** Triggers deployment hooks for the Staging Backend and Staging Frontend services upon successful build verification.
