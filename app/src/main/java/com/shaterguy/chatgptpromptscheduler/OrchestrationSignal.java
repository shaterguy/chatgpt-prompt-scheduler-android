package com.shaterguy.chatgptpromptscheduler;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict Protocol 3.0 control-signal parser. */
public final class OrchestrationSignal {
    public enum Type { SEND_WORK, SEND_CHAT, DONE, PAUSE, ABORTED }

    private static final String JOB = "([A-Za-z0-9][A-Za-z0-9._-]{0,63})";
    private static final Pattern ROUTE = Pattern.compile("^\\[AR_(SEND_WORK|SEND_CHAT) " + JOB + " (S\\d{3}) (R\\d{3})]$");
    private static final Pattern TERMINAL = Pattern.compile("^\\[AR_(DONE|PAUSE|ABORTED) " + JOB + "]$");

    public final Type type;
    public final String jobId;
    public final String step;
    public final String round;
    public final String raw;

    private OrchestrationSignal(Type type, String jobId, String step, String round, String raw) {
        this.type = type;
        this.jobId = jobId;
        this.step = step;
        this.round = round;
        this.raw = raw;
    }

    public static OrchestrationSignal parse(String response, String expectedJobId) {
        if (response == null || expectedJobId == null) return null;
        String[] lines = response.replace("\r\n", "\n").replace('\r', '\n').trim().split("\n");
        if (lines.length == 0) return null;
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().startsWith("[AR_")) return null;
        }
        String raw = lines[lines.length - 1].trim();
        Matcher route = ROUTE.matcher(raw);
        if (route.matches()) {
            String job = route.group(2);
            if (!job.equals(expectedJobId)) return null;
            Type type = "SEND_WORK".equals(route.group(1)) ? Type.SEND_WORK : Type.SEND_CHAT;
            return new OrchestrationSignal(type, job, route.group(3), route.group(4), raw);
        }
        Matcher terminal = TERMINAL.matcher(raw);
        if (!terminal.matches() || !terminal.group(2).equals(expectedJobId)) return null;
        return new OrchestrationSignal(Type.valueOf(terminal.group(1)), terminal.group(2), "", "", raw);
    }

    public boolean isOlderThan(String previousStep, String previousRound) {
        if (step.isEmpty() || previousStep == null || previousStep.isEmpty()) return false;
        int stepCompare = step.compareTo(previousStep);
        return stepCompare < 0 || (stepCompare == 0 && round.compareTo(Objects.requireNonNullElse(previousRound, "")) < 0);
    }

    public boolean routesFrom(String currentSide) {
        return (type == Type.SEND_WORK && OrchestrationStore.SIDE_CHAT.equals(currentSide))
                || (type == Type.SEND_CHAT && OrchestrationStore.SIDE_WORK.equals(currentSide));
    }

    public boolean isValidNextRoute(String currentSide, String previousStep, String previousRound) {
        if (!routesFrom(currentSide)) return false;
        boolean hasPrevious = previousStep != null && !previousStep.isEmpty();
        if (type == Type.SEND_WORK) {
            if (!hasPrevious) return "S001".equals(step) && "R001".equals(round);
            int stepNumber = sequenceNumber(step);
            int roundNumber = sequenceNumber(round);
            int previousStepNumber = sequenceNumber(previousStep);
            int previousRoundNumber = sequenceNumber(previousRound);
            return (stepNumber == previousStepNumber && roundNumber == previousRoundNumber + 1)
                    || (stepNumber == previousStepNumber + 1 && roundNumber == 1);
        }
        return hasPrevious && compareSequence(step, round, previousStep, previousRound) == 0;
    }

    private static int compareSequence(String leftStep, String leftRound, String rightStep, String rightRound) {
        int stepCompare = leftStep.compareTo(Objects.requireNonNullElse(rightStep, ""));
        if (stepCompare != 0) return stepCompare;
        return leftRound.compareTo(Objects.requireNonNullElse(rightRound, ""));
    }

    private static int sequenceNumber(String value) {
        return Integer.parseInt(value.substring(1));
    }
}
