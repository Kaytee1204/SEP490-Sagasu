# Migration compatibility

Existing team SQL files under db/ and src/main/resources/db/migrations/ are unchanged.

DataInitializer runs compatibility_existing_tables.sql after category migration 09
and before 10. It restores missing unique indexes/defaults on Hibernate-created
tables and links legacy office claims to receipts using their existing post link.
New office submissions also persist this receipt reference.

MigrationCompatibility.prepare reconciles SQL in memory before execution:
- Scripts 15 and 16 use the union of office/email and P2P activity transitions.
- Script 17 retains NEEDS_INFO in the claim status constraint.
The original scripts are not edited. Running those files directly in a SQL client
bypasses this adapter and can still fail; use the application startup sequence.

Verification: executed the exact Java adapter and all 15 registered scripts twice
against a copy of the current local DB. Then backed up the local DB in the Docker
container at /tmp/sagasu_before_compat_apply.dump and successfully applied all 15
to the configured local database. Java compiler goal passed.

This verifies the existing provisioned database, not a fresh installation. The
historical manual 09_office_claim_and_appointment.sql prerequisite remains outside
the startup list; do not add this one-time script directly to repeated startup.
