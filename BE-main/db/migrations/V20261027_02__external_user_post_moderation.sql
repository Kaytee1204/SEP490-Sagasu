-- Apply manually before starting the updated backend. Safe to re-run.
BEGIN;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_role_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_role_check CHECK
    (role IN ('ADMIN', 'USER', 'EXTERNAL_USER', 'CHECKER', 'APPROVER', 'MANAGER'));

ALTER TABLE post DROP CONSTRAINT IF EXISTS chk_post_moderation_actor;
ALTER TABLE post ADD CONSTRAINT chk_post_moderation_actor CHECK (
    moderation_status = 'PENDING'
    OR (moderated_by IS NOT NULL AND moderated_at IS NOT NULL)
    OR (moderation_status = 'VISIBLE' AND moderated_by IS NULL AND moderated_at IS NULL)
);

-- Only retract direct publications that have never been approved.
UPDATE post p SET moderation_status = 'PENDING'
FROM accounts a WHERE a.id = p.created_by
  AND a.role IN ('CHECKER', 'EXTERNAL_USER')
  AND p.moderation_status = 'VISIBLE'
  AND p.moderated_by IS NULL AND p.moderated_at IS NULL
  AND p.withdrawn_at IS NULL AND p.deleted_at IS NULL;
COMMIT;
