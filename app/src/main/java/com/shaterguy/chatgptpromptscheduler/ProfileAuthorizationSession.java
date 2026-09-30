package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayDeque;

/** Settings-only attempt state. No tokens, accounts, provider text or disk writes. */
final class ProfileAuthorizationSession {
    enum Mode { DEFAULT, PREVIOUS_CONSENT }
    enum Phase { REQUEST_TASK, CONSENT_LAUNCH, RESOLUTION_RESULT, DIRECT_RESULT }
    enum Outcome { PENDING, SUCCESS, GOOGLE_API, NO_RESULT, NO_TOKEN, SDK_ERROR, LAUNCH_ERROR, DISABLED, DESTROYED, MODE_CHANGED }
    static final int FIRST_REQUEST_CODE = 3000;
    private final ProfileAuthorizationAttempt gate = new ProfileAuthorizationAttempt();
    private final ArrayDeque<Record> history = new ArrayDeque<>();
    private Mode mode = Mode.DEFAULT;
    private int nextRequestCode;
    private int launchedRequestCode;
    private Record active;

    static final class Record {
        final long attempt, started;
        final int requestCode;
        final Mode mode;
        long lastEvent, ended;
        Phase phase = Phase.REQUEST_TASK;
        Outcome outcome = Outcome.PENDING;
        Integer googleStatus, activityResult;
        Boolean dataPresent;
        long gmsStart = -1, gmsEnd = -1;
        int availabilityStart = -1, availabilityEnd = -1;
        boolean environmentObserved;
        Record(long attempt, Mode mode, long now, int requestCode) { this.attempt=attempt; this.mode=mode; started=lastEvent=now; this.requestCode=requestCode; }
    }

    ProfileAuthorizationSession(int nextCode) {
        nextRequestCode = Math.max(FIRST_REQUEST_CODE, Math.min(65536, nextCode));
    }
    Mode mode() { return mode; }
    int size() { return history.size(); }
    int nextRequestCode() { return nextRequestCode; }
    boolean current(long attempt) { return gate.current(attempt); }
    long begin(long now) {
        long attempt = gate.begin();
        if (attempt == 0L) return 0L;
        // Reserve before authorize(): a saved-state snapshot must never reuse a delayed launch code.
        int requestCode = nextRequestCode <= 65535 ? nextRequestCode++ : 0;
        active = new Record(attempt, mode, now, requestCode);
        if (history.size() == 4) history.removeFirst();
        history.addLast(active);
        return attempt;
    }
    void selectMode(Mode next, long now) {
        if (next == null || next == mode) return;
        cancel(Outcome.MODE_CHANGED, now);
        mode = next;
    }
    void observeGoogle(long attempt, long version, int availability) {
        if (!current(attempt)) return;
        if (!active.environmentObserved) {
            active.gmsStart=version; active.availabilityStart=availability; active.environmentObserved=true;
        }
        active.gmsEnd=version; active.availabilityEnd=availability;
    }
    int launchConsent(long attempt, long now) {
        if (!current(attempt) || active.requestCode == 0 || !gate.consentLaunched(attempt)) return 0;
        launchedRequestCode = active.requestCode;
        active.phase=Phase.CONSENT_LAUNCH; active.lastEvent=now;
        return launchedRequestCode;
    }
    long takeConsentResult(int requestCode, int resultCode, boolean dataPresent, long now) {
        if (requestCode != launchedRequestCode || launchedRequestCode == 0) return 0L;
        long attempt=gate.takeConsentResult();
        if (attempt == 0L) return 0L;
        launchedRequestCode=0;
        active.phase=Phase.RESOLUTION_RESULT; active.lastEvent=now;
        active.activityResult=resultCode; active.dataPresent=dataPresent;
        return attempt;
    }
    boolean succeed(long attempt, long now) {
        if (!gate.finish(attempt)) return false;
        if (active.phase == Phase.REQUEST_TASK) active.phase=Phase.DIRECT_RESULT;
        end(Outcome.SUCCESS, now);
        return true;
    }
    boolean fail(long attempt, ProfileAuthorizationIssue issue, long now) {
        if (!gate.finish(attempt)) return false;
        active.googleStatus=issue.googleStatus;
        if (issue.activityResult != null) active.activityResult=issue.activityResult;
        end(Outcome.valueOf(issue.kind.name()), now);
        return true;
    }
    void cancel(Outcome reason, long now) {
        if (reason != Outcome.DISABLED && reason != Outcome.DESTROYED && reason != Outcome.MODE_CHANGED)
            throw new IllegalArgumentException("Invalid local cancellation reason");
        if (active != null && active.outcome == Outcome.PENDING) end(reason, now);
        gate.cancel();
        launchedRequestCode=0;
    }
    private void end(Outcome outcome, long now) {
        active.outcome=outcome; active.ended=now; launchedRequestCode=0;
    }
    String report(long now) {
        StringBuilder out=new StringBuilder("auth_diagnostic_v1\ngrant_source=UNKNOWN\n");
        for (Record record:history) {
            long stop=record.outcome == Outcome.PENDING ? now : record.ended;
            out.append("attempt=").append(record.attempt).append(" mode=").append(record.mode)
                    .append(" phase=").append(record.phase).append(" outcome=").append(record.outcome)
                    .append(" google=").append(record.googleStatus).append(" activity=").append(record.activityResult)
                    .append(" data=").append(record.dataPresent).append(" phase_ms=").append(Math.max(0L,record.lastEvent-record.started))
                    .append(" duration_ms=").append(Math.max(0L,stop-record.started))
                    .append(" gms_start=").append(record.gmsStart).append(" gms_end=").append(record.gmsEnd)
                    .append(" availability_start=").append(record.availabilityStart).append(" availability_end=").append(record.availabilityEnd).append('\n');
        }
        return out.toString();
    }
}
