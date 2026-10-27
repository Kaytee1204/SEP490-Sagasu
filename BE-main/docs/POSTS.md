# Posts: UC13–17 and publication policy

Apply `db/migrations/V20261027_02__external_user_post_moderation.sql` to the existing
PostgreSQL schema before starting this backend. This script can be re-run.

| Account role | New Lost/Found post |
| --- | --- |
| USER (internal student/lecturer) | VISIBLE |
| EXTERNAL_USER (provisioned external account) | PENDING |
| CHECKER | PENDING |

Admins and Managers can provision EXTERNAL_USER accounts. Existing USER accounts
remain internal; the system does not infer affiliation from email or expiry dates.
Assign the correct role in account management. Actual approval decisions on existing
posts are preserved. Review/approval actions belong to the moderation use cases.

Authenticated endpoints:

- GET `/api/posts?page=1&size=12`: only visible, non-withdrawn/non-deleted posts.
- GET `/api/posts/{id}`: visible posts; owners may also view their own pending posts.
- GET `/api/posts/categories`: active categories from the shared category repository.
- POST `/api/posts/lost` or `/api/posts/found`: multipart `data` JSON plus optional
  `images` (0–5 JPG/PNG files, up to 5 MiB each; actual image content validated).
  Common fields: title, description, categoryId, locationText, eventAt,
  optional privateIdentifiers (up to 5000 characters). Found additionally requires
  custodyMode `P2P` or `OFFICE`. OFFICE records the choice to hand to the office,
  pending Checker confirmation; it does not create an intake record.
- GET/PUT `/api/posts/{id}/private-identifiers`: active owner only. PUT accepts
  `{ "privateIdentifiers": "..." }`; blank text clears it. Deleted/withdrawn and
  non-owned posts return 404. Responses use `Cache-Control: no-store`.

Private identifiers never appear in public post DTOs or database full-text search.
Image storage uploads are compensated if the database save fails.

Set `R2_ENABLED=true`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID` and
`R2_SECRET_ACCESS_KEY` in your runtime configuration. Keep credentials local.
With R2 disabled, posting without images is supported. Bucket objects remain private;
the backend generates time-limited read URLs.

Frontend: `/dang-tin` (Lost), `/dang-tin-nhat-duoc` (Found), `/tim-kiem` (list),
`/chi-tiet/{id}` (detail and owner's private-identifiers editor).

Run backend tests with Java 21 and `SAGASU_DB_TESTS=true` for PostgreSQL integration
tests. Database test fixtures roll back. Frontend checks: `npm run build`, `npm run lint`.
