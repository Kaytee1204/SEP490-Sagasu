BEGIN;

-- Resume office requests that are waiting for the retired supplement step.
-- Historical requests for information, replies and attachments remain available.
UPDATE claim SET status='PENDING', version=version+1, updated_at=now()
WHERE claim_type='OFFICE' AND status='NEEDS_INFO';

-- A withdrawal ends only the dispute belonging to this claim.
CREATE OR REPLACE FUNCTION withdraw_p2p_appeal(withdrawn_claim bigint)
RETURNS SETOF bigint LANGUAGE plpgsql AS $$
DECLARE entry record;
    note text := 'Người gửi đã rút yêu cầu nhận đồ và kết thúc tranh chấp của mình.';
BEGIN
    FOR entry IN
        SELECT r.report_id, r.reporter_id, r.status
        FROM report r JOIN p2p_claim_appeal a USING(report_id)
        JOIN claim c ON c.claim_id=a.appellant_claim_id
        WHERE c.claim_id=withdrawn_claim AND c.claim_type IN ('P2P','OFFICE') AND c.status='CANCELLED'
          AND r.status IN ('OPEN','IN_REVIEW') FOR UPDATE OF r
    LOOP
        UPDATE report SET status='RESOLVED', resolution_note=note, resolved_at=now(), updated_at=now(),
            version=version+1 WHERE report_id=entry.report_id;
        INSERT INTO report_activity(report_id,actor_id,action,from_status,to_status,public_note)
            VALUES(entry.report_id,entry.reporter_id,'APPEAL_WITHDRAWN',entry.status,'RESOLVED',note);
        INSERT INTO report_decision(report_id,actor_id,action,conclusion,basis)
            VALUES(entry.report_id,entry.reporter_id,'APPEAL_WITHDRAWN',note,'Người yêu cầu đã hủy hồ sơ nhận đồ.');
        UPDATE report_inquiry SET closed_at=now() WHERE report_id=entry.report_id AND responded_at IS NULL AND closed_at IS NULL;
        RETURN NEXT entry.report_id;
    END LOOP;
END $$;

COMMIT;
