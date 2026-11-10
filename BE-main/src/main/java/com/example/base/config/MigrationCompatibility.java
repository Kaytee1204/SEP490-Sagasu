package com.example.base.config;

/** Reconciles shared constraints at execution time without editing team SQL files. */
final class MigrationCompatibility {
    private MigrationCompatibility() {}

    static String prepare(String path, String sql) {
        // DaiHV: startup replays old scripts; preserve report-resolution history without editing team SQL.
        if (path.endsWith("15_office_email_invitation.sql") || path.endsWith("16_p2p_handover.sql")
                || path.endsWith("19_office_transfer_and_return.sql")) {
            sql = sql.replaceAll("(?s)ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK.*?IS TRUE\\);", TRANSITIONS);
        }
        if (path.endsWith("17_confirm_p2p_receipt.sql")) {
            sql = sql.replace("status IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED','CLOSED')",
                    "status IN ('PENDING','NEEDS_INFO','APPROVED','REJECTED','CANCELLED','COMPLETED','CLOSED')");
        }
        return sql;
    }

    private static final String TRANSITIONS = """
        ALTER TABLE claim_activity ADD CONSTRAINT chk_claim_activity_transition CHECK ((
          (action='SUBMIT' AND from_status IS NULL AND to_status='PENDING') OR
          (action='APPROVE' AND from_status='PENDING' AND to_status='APPROVED') OR
          (action='REJECT' AND from_status IN ('PENDING','NEEDS_INFO') AND to_status='REJECTED') OR
          (action='REQUEST_INFO' AND from_status='PENDING' AND to_status='NEEDS_INFO') OR
          (action='RESUBMIT' AND from_status='NEEDS_INFO' AND to_status='PENDING') OR
          (action='CANCEL' AND from_status IN ('PENDING','NEEDS_INFO','APPROVED') AND to_status='CANCELLED') OR
          (action IN ('HANDOVER','INVITE_CONFIRM','RESCHEDULE') AND from_status='APPROVED' AND to_status='APPROVED') OR
          (action='CONFIRM_RECEIPT' AND from_status='APPROVED' AND to_status='COMPLETED') OR
          (action='AUTO_CLOSE' AND from_status IN ('PENDING','NEEDS_INFO') AND to_status='CLOSED') OR
          (action='REPORT_ISSUE' AND from_status='APPROVED' AND to_status='APPROVED' AND nullif(btrim(reason),'') IS NOT NULL) OR
          (action='DISPUTE_RESOLVE' AND from_status='APPROVED' AND to_status IN ('APPROVED','REJECTED') AND nullif(btrim(reason),'') IS NOT NULL) OR
          (action IN ('INVITE_DECLINE','INVITE_EXPIRE','OFFICE_CANCEL') AND from_status='APPROVED' AND to_status='REJECTED')
         OR (action='TRANSFER_OFFICE' AND ((from_status='APPROVED' AND to_status='PENDING') OR (from_status=to_status AND from_status IN ('PENDING','NEEDS_INFO','REJECTED','CANCELLED','CLOSED'))))
        ) IS TRUE);
        """;
}
