BEGIN;
-- Preserve existing transitions and permit the pre-handover P2P reversal.
DO $$
DECLARE existing_rule TEXT;
BEGIN
 SELECT pg_get_expr(conbin,conrelid) INTO existing_rule FROM pg_constraint
 WHERE conrelid='claim_activity'::regclass AND conname='chk_claim_activity_transition';
 IF existing_rule IS NULL THEN
  RAISE EXCEPTION 'Missing claim activity transition constraint';
 END IF;
 IF position('REVOKE_APPROVAL' in existing_rule)=0 THEN
  ALTER TABLE claim_activity DROP CONSTRAINT chk_claim_activity_transition;
  EXECUTE 'ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((' || existing_rule || ') OR ((action=''REVOKE_APPROVAL'' AND from_status=''APPROVED'' AND to_status=''PENDING'' AND nullif(btrim(reason),'''') IS NOT NULL) IS TRUE))';
 END IF;
END $$;
COMMIT;
