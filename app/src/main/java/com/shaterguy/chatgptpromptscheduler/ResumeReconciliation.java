package com.shaterguy.chatgptpromptscheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure protocol-position logic used when Resume rebuilds relay state from both conversation DOMs.
 * No Android state is read or written here so the ordering and ambiguity rules stay deterministic.
 */
public final class ResumeReconciliation {
    public static final String PROMPT_START = "AUTOMATION_START";
    public static final String PROMPT_WORK_STEP = "AUTOMATION_WORK_STEP";
    public static final String PROMPT_CHAT_REVIEW = "AUTOMATION_CHAT_REVIEW";
    public static final String PROMPT_CONTINUE_SAME = "AUTOMATION_CONTINUE_SAME";
    public static final String PROMPT_USER_RESOLVED = "AUTOMATION_USER_RESOLVED";

    public enum DecisionType { ROUTE, USER_ACTION, TERMINAL, WAIT_FOR_IDLE, AMBIGUOUS }

    public static final class Candidate {
        public final OrchestrationSignal signal;
        public final String sourceSide;
        public final String predecessorPrompt;
        public final String predecessorKind;
        public final String predecessorSignal;
        public final int predecessorIndex;
        public final int messageIndex;
        public final int phase;
        public final String positionStep;
        public final String positionRound;

        private Candidate(OrchestrationSignal signal, String sourceSide, String predecessorPrompt,
                          String predecessorKind, String predecessorSignal, int predecessorIndex,
                          int messageIndex, int phase, String positionStep, String positionRound) {
            this.signal = signal;
            this.sourceSide = sourceSide;
            this.predecessorPrompt = predecessorPrompt;
            this.predecessorKind = predecessorKind;
            this.predecessorSignal = predecessorSignal;
            this.predecessorIndex = predecessorIndex;
            this.messageIndex = messageIndex;
            this.phase = phase;
            this.positionStep = positionStep;
            this.positionRound = positionRound;
        }

        public String raw() { return signal.raw; }
    }

    public static final class RoomScan {
        public final String side;
        public final boolean mainPresent;
        public final boolean generating;
        public final boolean authRequired;
        public final List<Candidate> candidates;

        public RoomScan(String side, boolean mainPresent, boolean generating, boolean authRequired,
                        List<Candidate> candidates) {
            this.side = side;
            this.mainPresent = mainPresent;
            this.generating = generating;
            this.authRequired = authRequired;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        public static RoomScan idle(String side, Candidate... candidates) {
            List<Candidate> values = new ArrayList<>();
            Collections.addAll(values, candidates);
            return new RoomScan(side, true, false, false, values);
        }
    }

    public static final class Decision {
        public final DecisionType type;
        public final Candidate selected;
        public final String reason;

        private Decision(DecisionType type, Candidate selected, String reason) {
            this.type = type;
            this.selected = selected;
            this.reason = reason;
        }

        public String targetSide() {
            if (selected == null) return "";
            return switch (selected.signal.type) {
                case SEND_WORK -> OrchestrationStore.SIDE_WORK;
                case SEND_CHAT -> OrchestrationStore.SIDE_CHAT;
                case CONTINUE_SAME -> selected.sourceSide;
                default -> "";
            };
        }

        public String prompt() {
            return selected == null ? "" : OrchestrationStore.promptFor(selected.signal);
        }
    }

    private ResumeReconciliation() {}

    /**
     * Accepts a structurally extracted assistant signal only when its predecessor user prompt
     * proves the causal phase. The prompt and signal are never written to the execution log.
     */
    public static Candidate acceptCandidate(String expectedJobId, String sourceSide, String rawSignal,
                                            String predecessorPrompt, int predecessorIndex,
                                            int messageIndex) {
        return acceptCandidate(expectedJobId, sourceSide, rawSignal, predecessorPrompt, "",
                predecessorIndex, messageIndex);
    }

    /**
     * Variant that carries the immediately preceding assistant signal. It is required for
     * AUTOMATION_USER_RESOLVED, whose transport prompt intentionally omits Step/Round.
     */
    public static Candidate acceptCandidate(String expectedJobId, String sourceSide, String rawSignal,
                                            String predecessorPrompt, String predecessorSignal,
                                            int predecessorIndex, int messageIndex) {
        OrchestrationSignal.ParseResult parsed = OrchestrationSignal.parseDetailed(rawSignal, expectedJobId);
        if (!parsed.isValid() || messageIndex < 0) return null;
        OrchestrationSignal signal = parsed.signal;
        if (!validSource(signal, sourceSide)) return null;
        String predecessorKind = promptKind(predecessorPrompt, expectedJobId);
        String positionStep = signal.step;
        String positionRound = signal.round;
        if (positionStep.isEmpty() || positionRound.isEmpty()) {
            positionStep = promptStep(predecessorPrompt, expectedJobId);
            positionRound = promptRound(predecessorPrompt, expectedJobId);
        }
        if (positionStep.isEmpty() || positionRound.isEmpty()) {
            if (!OrchestrationStore.isTerminalSignal(signal.type)) return null;
            positionStep = "S999";
            positionRound = "R999";
        }
        if (!validSequence(positionStep, positionRound)) return null;
        return new Candidate(signal, sourceSide, predecessorPrompt, predecessorKind, predecessorSignal,
                predecessorIndex, messageIndex, 0, positionStep, positionRound);
    }

    public static Decision select(RoomScan chat, RoomScan work) {
        if (chat == null || work == null) return ambiguous("ROOM_SCAN_MISSING");
        if (!chat.mainPresent || !work.mainPresent) return waitForIdle("ROOM_DOM_NOT_READY");
        if (chat.authRequired || work.authRequired) return ambiguous("AUTH_REQUIRED");
        if (chat.generating || work.generating) return waitForIdle("ROOM_GENERATING");

        RoomChoice chatChoice = chooseRoom(chat);
        RoomChoice workChoice = chooseRoom(work);
        if (chatChoice.conflict || workChoice.conflict) return ambiguous("SAME_ROOM_CONFLICT");
        Candidate c = chatChoice.candidate;
        Candidate w = workChoice.candidate;
        if (c == null && w == null) return waitForIdle("NO_VALID_SIGNAL");
        if (c == null) return classify(w, "ONE_SIDED_WORK");
        if (w == null) return classify(c, "ONE_SIDED_CHAT");

        int position = comparePosition(c, w);
        if (position > 0) return classify(c, "CHAT_NEWER_POSITION");
        if (position < 0) return classify(w, "WORK_NEWER_POSITION");
        if (c.signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED)
            return classify(c, "CHAT_USER_ACTION_TIE");
        return classify(w, "WORK_TIE");
    }

    public static int comparePosition(Candidate left, Candidate right) {
        int step = Integer.compare(sequenceNumber(left.positionStep), sequenceNumber(right.positionStep));
        if (step != 0) return step;
        return Integer.compare(sequenceNumber(left.positionRound), sequenceNumber(right.positionRound));
    }

    /** Returns the highest numeric Step/Round candidate from one room, or null on same-position conflict. */
    public static Candidate highestCandidate(RoomScan room) {
        RoomChoice choice = chooseRoom(room);
        return choice.conflict ? null : choice.candidate;
    }

    /** Protocol identity deliberately ignores DOM index and predecessor/causal metadata. */
    public static boolean sameCandidate(Candidate left, Candidate right) {
        if (left == null || right == null) return false;
        return left.sourceSide.equals(right.sourceSide)
                && left.signal.type == right.signal.type
                && left.signal.step.equals(right.signal.step)
                && left.signal.round.equals(right.signal.round)
                && left.signal.actionId.equals(right.signal.actionId)
                && left.raw().equals(right.raw())
                && left.positionStep.equals(right.positionStep)
                && left.positionRound.equals(right.positionRound);
    }

    public static String promptKind(String prompt, String expectedJobId) {
        String[] tokens = promptTokens(prompt);
        if (tokens.length < 2 || !expectedJobId.equals(tokens[1])) return "";
        return switch (tokens[0]) {
            case PROMPT_START -> tokens.length == 2 ? PROMPT_START : "";
            case PROMPT_WORK_STEP -> tokens.length == 4 && validSequence(tokens[2], tokens[3])
                    ? PROMPT_WORK_STEP : "";
            case PROMPT_CHAT_REVIEW -> tokens.length == 4 && validSequence(tokens[2], tokens[3])
                    ? PROMPT_CHAT_REVIEW : "";
            case PROMPT_CONTINUE_SAME -> tokens.length == 4 && validSequence(tokens[2], tokens[3])
                    ? PROMPT_CONTINUE_SAME : "";
            case PROMPT_USER_RESOLVED -> tokens.length == 3 && validToken(tokens[2])
                    ? PROMPT_USER_RESOLVED : "";
            default -> "";
        };
    }

    public static String promptStep(String prompt, String expectedJobId) {
        String[] tokens = promptTokens(prompt);
        return tokens.length >= 4 && expectedJobId.equals(tokens[1]) && validSequence(tokens[2], tokens[3])
                ? tokens[2] : "";
    }

    public static String promptRound(String prompt, String expectedJobId) {
        String[] tokens = promptTokens(prompt);
        return tokens.length >= 4 && expectedJobId.equals(tokens[1]) && validSequence(tokens[2], tokens[3])
                ? tokens[3] : "";
    }

    private static final class RoomChoice {
        final Candidate candidate;
        final boolean conflict;
        RoomChoice(Candidate candidate, boolean conflict) {
            this.candidate = candidate;
            this.conflict = conflict;
        }
    }

    private static RoomChoice chooseRoom(RoomScan room) {
        if (room == null || !room.mainPresent || room.authRequired || room.generating || room.candidates.isEmpty())
            return new RoomChoice(null, false);
        List<Candidate> values = new ArrayList<>(room.candidates);
        values.sort(Comparator.comparingInt((Candidate value) -> sequenceNumber(value.positionStep))
                .thenComparingInt(value -> sequenceNumber(value.positionRound)).reversed());
        Candidate best = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            Candidate candidate = values.get(i);
            if (comparePosition(candidate, best) != 0) break;
            if (!sameCandidate(candidate, best)) return new RoomChoice(null, true);
            if (candidate.messageIndex > best.messageIndex) best = candidate;
        }
        return new RoomChoice(best, false);
    }

    private static Decision classify(Candidate candidate, String reason) {
        if (candidate == null) return waitForIdle("NO_VALID_SIGNAL");
        if (candidate.signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED)
            return new Decision(DecisionType.USER_ACTION, candidate, reason);
        if (OrchestrationStore.isTerminalSignal(candidate.signal.type))
            return new Decision(DecisionType.TERMINAL, candidate, reason);
        return new Decision(DecisionType.ROUTE, candidate, reason);
    }

    private static Decision waitForIdle(String reason) {
        return new Decision(DecisionType.WAIT_FOR_IDLE, null, reason);
    }

    private static boolean validSource(OrchestrationSignal signal, String sourceSide) {
        return signal.routesFrom(sourceSide)
                || (OrchestrationStore.SIDE_CHAT.equals(sourceSide)
                && OrchestrationStore.isTerminalSignal(signal.type));
    }

    private static boolean validPredecessor(OrchestrationSignal signal, String sourceSide, String promptKind,
                                            String predecessorStep, String predecessorRound) {
        if (OrchestrationStore.isTerminalSignal(signal.type))
            return OrchestrationStore.SIDE_CHAT.equals(sourceSide);
        if (signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED) {
            if (!OrchestrationStore.SIDE_CHAT.equals(sourceSide)) return false;
            return sameOrInitial(signal, promptKind, predecessorStep, predecessorRound, true);
        }
        if (signal.type == OrchestrationSignal.Type.CONTINUE_SAME) {
            if (PROMPT_START.equals(promptKind)) return "S001".equals(signal.step) && "R001".equals(signal.round);
            return sameSequence(signal.step, signal.round, predecessorStep, predecessorRound)
                    && ((OrchestrationStore.SIDE_CHAT.equals(sourceSide)
                    && (PROMPT_CHAT_REVIEW.equals(promptKind) || PROMPT_CONTINUE_SAME.equals(promptKind)
                    || PROMPT_USER_RESOLVED.equals(promptKind)))
                    || (OrchestrationStore.SIDE_WORK.equals(sourceSide)
                    && (PROMPT_WORK_STEP.equals(promptKind) || PROMPT_CONTINUE_SAME.equals(promptKind))));
        }
        if (signal.type == OrchestrationSignal.Type.SEND_CHAT) {
            return OrchestrationStore.SIDE_WORK.equals(sourceSide)
                    && (PROMPT_WORK_STEP.equals(promptKind) || PROMPT_CONTINUE_SAME.equals(promptKind))
                    && sameSequence(signal.step, signal.round, predecessorStep, predecessorRound);
        }
        if (signal.type == OrchestrationSignal.Type.SEND_WORK) {
            if (!OrchestrationStore.SIDE_CHAT.equals(sourceSide)) return false;
            if (PROMPT_START.equals(promptKind)) return "S001".equals(signal.step) && "R001".equals(signal.round);
            if (!(PROMPT_CHAT_REVIEW.equals(promptKind) || PROMPT_CONTINUE_SAME.equals(promptKind)
                    || PROMPT_USER_RESOLVED.equals(promptKind))) return false;
            return nextSequence(signal.step, signal.round, predecessorStep, predecessorRound);
        }
        return false;
    }

    private static boolean sameOrInitial(OrchestrationSignal signal, String promptKind,
                                         String predecessorStep, String predecessorRound,
                                         boolean allowInitial) {
        if (PROMPT_START.equals(promptKind))
            return allowInitial && "S001".equals(signal.step) && "R001".equals(signal.round);
        return sameSequence(signal.step, signal.round, predecessorStep, predecessorRound);
    }

    private static int causalPhase(OrchestrationSignal signal, String sourceSide, String promptKind) {
        if (OrchestrationStore.isTerminalSignal(signal.type)
                || signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED) return 6;
        if (OrchestrationStore.SIDE_CHAT.equals(sourceSide)) {
            if (signal.type == OrchestrationSignal.Type.CONTINUE_SAME && PROMPT_START.equals(promptKind)) return 1;
            if (signal.type == OrchestrationSignal.Type.SEND_WORK) return 2;
            if (signal.type == OrchestrationSignal.Type.CONTINUE_SAME) return 5;
        }
        if (OrchestrationStore.SIDE_WORK.equals(sourceSide)) {
            if (signal.type == OrchestrationSignal.Type.CONTINUE_SAME) return 3;
            if (signal.type == OrchestrationSignal.Type.SEND_CHAT) return 4;
        }
        return -1;
    }

    private static int rankCompare(Candidate left, Candidate right) {
        int step = Integer.compare(sequenceNumber(left.positionStep), sequenceNumber(right.positionStep));
        if (step != 0) return step;
        int round = Integer.compare(sequenceNumber(left.positionRound), sequenceNumber(right.positionRound));
        if (round != 0) return round;
        return Integer.compare(left.phase, right.phase);
    }

    private static Decision ambiguous(String reason) {
        return new Decision(DecisionType.AMBIGUOUS, null, reason);
    }

    private static String[] promptTokens(String prompt) {
        if (prompt == null) return new String[0];
        String[] lines = prompt.replace('\r', '\n').split("\\n");
        String line = "";
        for (String value : lines) if (!value.trim().isEmpty()) line = value.trim();
        if (!line.startsWith("[") || !line.endsWith("]")) return new String[0];
        return line.substring(1, line.length() - 1).trim().split("\\s+");
    }

    private static boolean validSequence(String step, String round) {
        return step != null && round != null && step.matches("S\\d{3}") && round.matches("R\\d{3}");
    }

    private static boolean validToken(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private static boolean sameSequence(String step, String round, String otherStep, String otherRound) {
        return validSequence(step, round) && validSequence(otherStep, otherRound)
                && sequenceNumber(step) == sequenceNumber(otherStep)
                && sequenceNumber(round) == sequenceNumber(otherRound);
    }

    private static boolean nextSequence(String step, String round, String previousStep, String previousRound) {
        if (!validSequence(step, round) || !validSequence(previousStep, previousRound)) return false;
        int currentStep = sequenceNumber(step);
        int currentRound = sequenceNumber(round);
        int oldStep = sequenceNumber(previousStep);
        int oldRound = sequenceNumber(previousRound);
        return (currentStep == oldStep && currentRound == oldRound + 1)
                || (currentStep == oldStep + 1 && currentRound == 1);
    }

    private static int sequenceNumber(String value) {
        if (value == null || value.length() < 2
                || (value.charAt(0) != 'S' && value.charAt(0) != 'R')) return -1;
        try { return Integer.parseInt(value.substring(1)); } catch (NumberFormatException ignored) { return -1; }
    }
}
