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
        if (!parsed.isValid() || predecessorIndex < 0 || messageIndex <= predecessorIndex)
            return null;
        OrchestrationSignal signal = parsed.signal;
        String promptKind = promptKind(predecessorPrompt, expectedJobId);
        if (promptKind.isEmpty() || !validSource(signal, sourceSide)) return null;
        String predecessorStep = promptStep(predecessorPrompt, expectedJobId);
        String predecessorRound = promptRound(predecessorPrompt, expectedJobId);
        if (PROMPT_USER_RESOLVED.equals(promptKind)) {
            OrchestrationSignal context = OrchestrationSignal.parse(predecessorSignal, expectedJobId);
            if (context == null || context.type != OrchestrationSignal.Type.USER_ACTION_REQUIRED
                    || !OrchestrationStore.SIDE_CHAT.equals(sourceSide)) return null;
            predecessorStep = context.step;
            predecessorRound = context.round;
        }
        if (!validPredecessor(signal, sourceSide, promptKind, predecessorStep, predecessorRound)) return null;
        int phase = causalPhase(signal, sourceSide, promptKind);
        if (phase < 0) return null;
        String positionStep = signal.step.isEmpty()
                ? (predecessorStep.isEmpty() && PROMPT_START.equals(promptKind) ? "S001" : predecessorStep)
                : signal.step;
        String positionRound = signal.round.isEmpty()
                ? (predecessorRound.isEmpty() && PROMPT_START.equals(promptKind) ? "R001" : predecessorRound)
                : signal.round;
        if (positionStep.isEmpty() || positionRound.isEmpty()) return null;
        return new Candidate(signal, sourceSide, predecessorPrompt, promptKind, predecessorSignal,
                predecessorIndex, messageIndex, phase, positionStep, positionRound);
    }

    public static Decision select(RoomScan chat, RoomScan work) {
        if (chat == null || work == null) return ambiguous("ROOM_SCAN_MISSING");
        if (!chat.mainPresent || !work.mainPresent) return ambiguous("ROOM_DOM_MISSING");
        if (chat.authRequired || work.authRequired) return ambiguous("AUTH_REQUIRED");
        if (chat.generating || work.generating) return new Decision(DecisionType.WAIT_FOR_IDLE, null,
                "ROOM_GENERATING");

        List<Candidate> all = new ArrayList<>();
        all.addAll(chat.candidates);
        all.addAll(work.candidates);
        if (all.isEmpty()) return ambiguous("NO_VALID_SIGNAL");

        all.sort(Comparator.comparingInt((Candidate value) -> sequenceNumber(value.positionStep))
                .thenComparingInt(value -> sequenceNumber(value.positionRound))
                .thenComparingInt(value -> value.phase)
                .thenComparingInt(value -> value.messageIndex).reversed());

        Candidate best = all.get(0);
        for (int index = 1; index < all.size(); index++) {
            Candidate candidate = all.get(index);
            if (rankCompare(candidate, best) != 0) break;
            if (candidate.sourceSide.equals(best.sourceSide) && candidate.raw().equals(best.raw())) {
                if (candidate.messageIndex > best.messageIndex) best = candidate;
                continue;
            }
            return ambiguous("PROTOCOL_POSITION_CONFLICT");
        }
        if (best.signal.type == OrchestrationSignal.Type.USER_ACTION_REQUIRED)
            return new Decision(DecisionType.USER_ACTION, best, "USER_ACTION_REQUIRED");
        if (OrchestrationStore.isTerminalSignal(best.signal.type))
            return new Decision(DecisionType.TERMINAL, best, best.signal.type.name());
        return new Decision(DecisionType.ROUTE, best, "SIGNAL_SELECTED");
    }

    public static int comparePosition(Candidate left, Candidate right) {
        return rankCompare(left, right);
    }

    /**
     * Returns the highest candidate from one room, or null when the room has no usable candidate
     * or contains a same-rank protocol conflict. This is used by the pre-submit source-freshness
     * guard and intentionally fails closed instead of guessing between equal positions.
     */
    public static Candidate highestCandidate(RoomScan room) {
        if (room == null || !room.mainPresent || room.authRequired || room.generating
                || room.candidates.isEmpty()) return null;
        List<Candidate> values = new ArrayList<>(room.candidates);
        values.sort(Comparator.comparingInt((Candidate value) -> sequenceNumber(value.positionStep))
                .thenComparingInt(value -> sequenceNumber(value.positionRound))
                .thenComparingInt(value -> value.phase)
                .thenComparingInt(value -> value.messageIndex).reversed());
        Candidate best = values.get(0);
        for (int index = 1; index < values.size(); index++) {
            Candidate candidate = values.get(index);
            if (rankCompare(candidate, best) != 0) break;
            if (!sameCandidate(candidate, best)) return null;
            if (candidate.messageIndex > best.messageIndex) best = candidate;
        }
        return best;
    }

    /** Compares the protocol identity, not the transient DOM index, of two candidates. */
    public static boolean sameCandidate(Candidate left, Candidate right) {
        if (left == null || right == null) return false;
        return left.sourceSide.equals(right.sourceSide)
                && left.signal.type == right.signal.type
                && left.signal.step.equals(right.signal.step)
                && left.signal.round.equals(right.signal.round)
                && left.signal.actionId.equals(right.signal.actionId)
                && left.raw().equals(right.raw())
                && left.positionStep.equals(right.positionStep)
                && left.positionRound.equals(right.positionRound)
                && left.phase == right.phase
                && left.predecessorKind.equals(right.predecessorKind)
                && left.predecessorPrompt.equals(right.predecessorPrompt)
                && left.predecessorSignal.equals(right.predecessorSignal);
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
