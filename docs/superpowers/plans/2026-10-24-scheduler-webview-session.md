# Scheduler WebView session repair

Baseline: v0.6.1-dev6 / b062445b0eb8d00ac15a3705398932d2e0cc092a.

1. Keep the default Android WebView browser identity in both login and scheduled execution. Remove the scheduler-only desktop user-agent mutation and viewport overrides. Preserve cookie storage, navigation, deadlines and duplicate-submit handling.
2. Reproduce redundant preference GET and native selection comparisons after successful writes. Remove these readback gates as already requested, keeping actual cookie write results and API acknowledgement failures, thrown errors, and preference writes before entering the target composer.
3. Exercise Chat thinking, Instant/Pro effort removal, Work Ultra state, one-write polling and error propagation. Run existing unit tests and package the unchanged DEV install/signing lineage as 0.6.1-dev7 / 6001007.

No new challenge solver, wait conditions, error codes, permissions, cookie reset, login flow, or scheduler UI.
