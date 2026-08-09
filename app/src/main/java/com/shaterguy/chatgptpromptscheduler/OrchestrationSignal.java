package com.shaterguy.chatgptpromptscheduler;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, bounded Protocol 3.x control-signal parser with safe diagnostics. */
public final class OrchestrationSignal {
    public enum Type { SEND_WORK, SEND_CHAT, CONTINUE_SAME, USER_ACTION_REQUIRED, DONE, PAUSE, ABORTED }

    public enum ErrorCode {
        NONE,
        NO_SIGNAL,
        PARSE_FAILED,
        WRONG_JOB,
        WRONG_DIRECTION,
        WRONG_STEP_ROUND,
        DUPLICATE,
        STALE,
        WORK_TERMINAL
    }

    public static final class ParseResult {
        public final OrchestrationSignal signal;
        public final ErrorCode errorCode;

        private ParseResult(OrchestrationSignal signal, ErrorCode errorCode) {
            this.signal = signal;
            this.errorCode = errorCode;
        }

        public boolean isValid() { return signal != null && errorCode == ErrorCode.NONE; }
    }

    private static final String JOB = "([A-Za-z0-9][A-Za-z0-9._-]{0,63})";
    private static final String ACTION = "([A-Za-z0-9][A-Za-z0-9._-]{0,63})";
    private static final Pattern ROUTE = Pattern.compile("^\\[AR_(SEND_WORK|SEND_CHAT) " + JOB + " (S\\d{3}) (R\\d{3})]$");
    private static final Pattern CONTINUE_SAME = Pattern.compile("^\\[AR_CONTINUE_SAME " + JOB + " (S\\d{3}) (R\\d{3})]$");
    private static final Pattern USER_ACTION = Pattern.compile("^\\[AR_USER_ACTION_REQUIRED " + JOB + " (S\\d{3}) (R\\d{3}) " + ACTION + "]$");
    private static final Pattern TERMINAL = Pattern.compile("^\\[AR_(DONE|PAUSE|ABORTED) " + JOB + "]$");
    private static final Pattern ANY_CONTROL = Pattern.compile("^\\[AR_[^\\r\\n]{1,256}]$");

    public final Type type;
    public final String jobId;
    public final String step;
    public final String round;
    public final String actionId;
    public final String raw;

    private OrchestrationSignal(Type type, String jobId, String step, String round,
                                String actionId, String raw) {
        this.type = type;
        this.jobId = jobId;
        this.step = step;
        this.round = round;
        this.actionId = actionId;
        this.raw = raw;
    }

    public static OrchestrationSignal parse(String response, String expectedJobId) {
        ParseResult result = parseDetailed(response, expectedJobId);
        return result.isValid() ? result.signal : null;
    }

    public static ParseResult parseDetailed(String response, String expectedJobId) {
        if (response == null || expectedJobId == null) return error(ErrorCode.NO_SIGNAL);
        String normalized = response.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return error(ErrorCode.NO_SIGNAL);
        String[] lines = normalized.split("\n");
        boolean foundControl = false;
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().startsWith("[AR_")) {
                foundControl = true;
                break;
            }
        }
        if (foundControl) return error(ErrorCode.PARSE_FAILED);

        String raw = lines[lines.length - 1].trim();
        Matcher route = ROUTE.matcher(raw);
        if (route.matches()) {
            String job = route.group(2);
            if (!job.equals(expectedJobId)) return error(ErrorCode.WRONG_JOB);
            Type type = "SEND_WORK".equals(route.group(1)) ? Type.SEND_WORK : Type.SEND_CHAT;
            return ok(new OrchestrationSignal(type, job, route.group(3), route.group(4), "", raw));
        }
        Matcher same = CONTINUE_SAME.matcher(raw);
        if (same.matches()) {
            String job = same.group(1);
            if (!job.equals(expectedJobId)) return error(ErrorCode.WRONG_JOB);
            return ok(new OrchestrationSignal(Type.CONTINUE_SAME, job, same.group(2), same.group(3), "", raw));
        }
        Matcher userAction = USER_ACTION.matcher(raw);
        if (userAction.matches()) {
            String job = userAction.group(1);
            if (!job.equals(expectedJobId)) return error(ErrorCode.WRONG_JOB);
            return ok(new OrchestrationSignal(Type.USER_ACTION_REQUIRED, job, userAction.group(2),
                    userAction.group(3), userAction.group(4), raw));
        }
        Matcher terminal = TERMINAL.matcher(raw);
        if (terminal.matches()) {
            if (!terminal.group(2).equals(expectedJobId)) return error(ErrorCode.WRONG_JOB);
            return ok(new OrchestrationSignal(Type.valueOf(terminal.group(1)), terminal.group(2),
                    "", "", "", raw));
        }
        if (raw.startsWith("[AR_") || ANY_CONTROL.matcher(raw).matches()) return error(ErrorCode.PARSE_FAILED);
        return error(ErrorCode.NO_SIGNAL);
    }

    public static ParseResult validate(String response, String expectedJobId, String sourceSide,
                                       String previousStep, String previousRound, String lastSignal) {
        return validate(response, expectedJobId, sourceSide, previousStep, previousRound, lastSignal,
                Long.MIN_VALUE, Long.MIN_VALUE);
    }

    /**
     * Validates a response against the durable response epoch. A repeated SAME-SIDE signal is
     * still rejected within one assistant response, but the same raw signal is valid again after
     * a later response epoch because it is a continuation trigger rather than a global nonce.
     */
    public static ParseResult validate(String response, String expectedJobId, String sourceSide,
                                       String previousStep, String previousRound, String lastSignal,
                                       long responseEpoch, long lastSignalResponseEpoch) {
        ParseResult parsed = parseDetailed(response, expectedJobId);
        if (!parsed.isValid()) return parsed;
        OrchestrationSignal signal = parsed.signal;
        // Re-validation may legitimately return the same unresolved ACTION_ID after USER_RESOLVED.
        if (signal.raw.equals(lastSignal) && signal.type != Type.USER_ACTION_REQUIRED) {
            boolean laterSameSideEpoch = signal.type == Type.CONTINUE_SAME
                    && responseEpoch != Long.MIN_VALUE
                    && lastSignalResponseEpoch != Long.MIN_VALUE
                    && responseEpoch != lastSignalResponseEpoch;
            if (!laterSameSideEpoch) return error(ErrorCode.DUPLICATE);
        }
        if (signal.isOlderThan(previousStep, previousRound)) return error(ErrorCode.STALE);
        if ((signal.type == Type.DONE || signal.type == Type.PAUSE || signal.type == Type.ABORTED)
                && OrchestrationStore.SIDE_WORK.equals(sourceSide)) return error(ErrorCode.WORK_TERMINAL);
        if (signal.type == Type.DONE || signal.type == Type.PAUSE || signal.type == Type.ABORTED) return parsed;
        if (!signal.routesFrom(sourceSide)) return error(ErrorCode.WRONG_DIRECTION);
        if (!signal.isValidNextRoute(sourceSide, previousStep, previousRound))
            return error(ErrorCode.WRONG_STEP_ROUND);
        return parsed;
    }

    public boolean isOlderThan(String previousStep, String previousRound) {
        if (step.isEmpty() || previousStep == null || previousStep.isEmpty()) return false;
        int stepCompare = step.compareTo(previousStep);
        return stepCompare < 0 || (stepCompare == 0
                && round.compareTo(Objects.requireNonNullElse(previousRound, "")) < 0);
    }

    public boolean routesFrom(String currentSide) {
        return (type == Type.SEND_WORK && OrchestrationStore.SIDE_CHAT.equals(currentSide))
                || (type == Type.SEND_CHAT && OrchestrationStore.SIDE_WORK.equals(currentSide))
                || (type == Type.CONTINUE_SAME && (OrchestrationStore.SIDE_CHAT.equals(currentSide)
                || OrchestrationStore.SIDE_WORK.equals(currentSide)))
                || (type == Type.USER_ACTION_REQUIRED && OrchestrationStore.SIDE_CHAT.equals(currentSide));
    }

    public boolean isValidNextRoute(String currentSide, String previousStep, String previousRound) {
        if (!routesFrom(currentSide)) return false;
        if (type == Type.CONTINUE_SAME) {
            return previousStep != null && !previousStep.isEmpty()
                    && step.equals(previousStep) && round.equals(Objects.requireNonNullElse(previousRound, ""));
        }
        boolean hasPrevious = previousStep != null && !previousStep.isEmpty();
        if (type == Type.SEND_CHAT) {
            return hasPrevious && compareSequence(step, round, previousStep, previousRound) == 0;
        }
        if (type == Type.USER_ACTION_REQUIRED && hasPrevious
                && compareSequence(step, round, previousStep, previousRound) == 0) return true;
        if (!hasPrevious) return "S001".equals(step) && "R001".equals(round);
        int stepNumber = sequenceNumber(step);
        int roundNumber = sequenceNumber(round);
        int previousStepNumber = sequenceNumber(previousStep);
        int previousRoundNumber = sequenceNumber(previousRound);
        return (stepNumber == previousStepNumber && roundNumber == previousRoundNumber + 1)
                || (stepNumber == previousStepNumber + 1 && roundNumber == 1);
    }

    private static int compareSequence(String leftStep, String leftRound, String rightStep, String rightRound) {
        int stepCompare = leftStep.compareTo(Objects.requireNonNullElse(rightStep, ""));
        return stepCompare != 0 ? stepCompare
                : leftRound.compareTo(Objects.requireNonNullElse(rightRound, ""));
    }

    private static int sequenceNumber(String value) { return Integer.parseInt(value.substring(1)); }
    private static ParseResult ok(OrchestrationSignal signal) { return new ParseResult(signal, ErrorCode.NONE); }
    private static ParseResult error(ErrorCode errorCode) { return new ParseResult(null, errorCode); }
}
