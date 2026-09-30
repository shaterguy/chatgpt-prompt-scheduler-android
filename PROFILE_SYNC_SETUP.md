# Canonical model/effort updates (0.5.2-dev4)

Settings → **Google 로그인 · 목록 업데이트** opens Google-managed account selection and consent. The requested scopes are `drive.metadata.readonly` and `documents.readonly`. Although Google grants these scopes at account level, this app's read-only client accepts only the two canonical profile document IDs already used by SelfRun. It never creates, modifies, shares, or deletes Drive/Docs files, and never stores or logs an access token.

After enabling, the main screen, schedule editor, settings screen and scheduled/manual execution check for document changes. Each mode's validated document completely replaces that mode's list. Removed models/efforts do not return from bundled defaults. This cleanup does not rewrite stored schedules or prompts; an unavailable choice is not added to the picker. The disabled selection-required placeholder blocks saving until a valid choice is explicitly selected, and unavailable explicit execution stops before submission. “Keep current” and existing-conversation behavior remain unchanged.

Drive metadata versions are checked before and after reading a changed document. Schema, allowlisted operations, signal collisions and SHA-256 fingerprints are checked before one atomic preferences commit. A failed request or malformed update retains the last known good snapshot and displays a stale-status warning. The first opt-in without a valid snapshot exposes no explicit remote profiles. Turning automatic updates off retains the last good list and does not revoke Google account consent; users can manage that permission in Google account settings. Manual capture/import UI and entrypoints are removed. Legacy chat_profiles/work_profiles preferences are quarantined in place and never selected, seeded, merged into automatic snapshots, or rewritten. Canonical raw/version data is retained and revalidated, even when a document entry has builtIn=true. That field is payload metadata, not local provenance.

## Google configuration required for live login

The Google Cloud project used for authorization must enable Google Drive API and Google Docs API and have its consent screen/scopes configured. Its Android OAuth client must match the actual installed package and signing certificate (SHA-1):

- DEV: `com.shaterguy.chatgptpromptscheduler.dev`, existing fixed DEV signing lineage
- A future stable build: `com.shaterguy.chatgptpromptscheduler`, existing stable signing lineage

No client secret belongs in an Android application. No OAuth client, grant, Google account connection, document permission or sharing setting is created by this patch. Google Play Services must be available on the device. A compile/test pass does not prove the project's OAuth registration or live user consent works.

## Data compatibility and verification

Portable settings/schedule schema remains version 1. Canonical raw snapshots, source versions and sanitized sync status are stored separately from schedule/settings data. In-memory resolved request profiles are never exported. Existing login sessions, schedule IDs, alarms, queues and logs are not cleared.

Local verification command:

`android-local-verify "$HOME/work/scheduler-profile-sync-20260930" :app:testDebugUnitTest :app:assembleRelease :app:assembleDebugAndroidTest`

This creates an unsigned DEV release candidate. Delivery must use the existing DEV signing certificate; do not substitute a debug key, create a new DEV identity, install an APK, or delete app data as part of this build.


### Interrupted login and refresh deadlines

- Disabling updates invalidates pending authorization. If Settings is destroyed or recreated during consent, the returned result is ignored and the app asks for a fresh login tap. Existing sync preference and last-good profiles are retained.
- Scheduled execution holds a bounded preparation wake lock during refresh and transfers to the run wake lock before releasing it. Cleanup and service destruction release the preparation lease.
- Refresh waiters have a 60-second deadline independent of blocked HTTP work. A commit already admitted may finish before completion is delivered; cancelled work cannot start another publication. Timeout/auth failure status is updated in memory immediately, while canonical profiles and versions remain persisted last-good snapshots.


### Google authorization diagnostics (dev2)

The result returned by Google's authorization UI is always decoded by the Google SDK when result data is present. An Android Activity result code alone no longer discards the Google response. A valid SDK-returned access token is required before sync can be enabled, and stale/disabled Activity attempts are still rejected.

If authorization fails, Settings retains an in-memory, readable error summary using only fixed categories and numeric Google status codes. The app does not log or persist raw Intent data, provider messages, account identifiers, tokens, or exception causes. A generic cancellation/no-result message is not evidence that the user cancelled or that Cloud registration is absent. Google Cloud registration and actual consent remain separate prerequisites; this diagnostic improvement does not itself create or repair a Cloud client.


### Controlled authorization comparison (dev3)

The normal Settings button now omits the optional prompt parameter, matching SelfRun's runtime authorization request. Background refresh always uses this default. The Google SDK remains pinned to 21.6.0 and the two read-only scopes are unchanged. This is a controlled diagnostic comparison, not proof that optional CONSENT caused INTERNAL_ERROR (8).

Open Google connection diagnostics only when needed. The single comparison checkbox selects the previous CONSENT request for the next explicit login-button tap; opening the panel or changing the checkbox never starts authorization. Changing mode, disabling sync, or destroying Settings invalidates the pending attempt. Each launched result has a distinct request code; only the next unused numeric code survives recreation, never authorization state. No retries, grant revocation, token clearing, or extra scopes are added.

Up to four attempts remain only in the current Settings instance. The report contains fixed phases/modes/outcomes, numeric Google and Activity result codes, a result-data-present flag, monotonic elapsed times, and Google Play services version/availability snapshots. The copy button adds the public app identity/version/signing-certificate SHA-1, Android SDK level, and current Google Play services metadata. No accounts, access tokens, Intent contents, provider messages or exception causes are recorded, persisted or automatically copied. Direct SDK success is recorded as DIRECT_RESULT with grant_source=UNKNOWN; it does not prove a new grant or cache hit.

Try the default request once; if it succeeds, stop the comparison. If it fails, the previous-mode checkbox allows one explicit comparison without reinstalling. A successful grant or server-side change can affect later attempts, so these observations do not establish causality. Do not revoke SelfRun access or clear Google data to repeat the comparison. Live OAuth completion and document refresh still need user verification.


### Automatic-only selection (dev4)

A fresh install or a missing/invalid automatic snapshot shows update-needed and supplies no explicit model fallback. Google authorization SDK, read-only scopes and diagnostics are unchanged. Only the two fixed official document readers can publish canonical raw/version snapshots. Network or authorization failure retains the last valid automatic list; disabling updates does not restore old local lists. No Drive documents are deleted or edited.

The editor preserves intentional Chat keep, complete Work inherit/inherit, and existing-conversation native behavior. An explicit Work model requires an exact reasoning from the same automatic model; model/inherit and inherit/reasoning partial pairs are invalid rather than silently choosing a native model. Changing an explicit model requires choosing a valid reasoning. Obsolete stored choices remain in saved schedule JSON for recovery, but are not selectable or executable. Prompt/schedule/settings schema remains unchanged.

Bundled Java mappings and JavaScript convenience setters are removed from runtime. Tests use explicit historical fixtures to verify exact operation and data-plane behavior. Production configuration requires a matching registry attachment and complete validated operations; missing attachment or operations cannot fall back to a guessed model.
