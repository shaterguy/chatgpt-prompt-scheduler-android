package com.shaterguy.chatgptpromptscheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

public class CoreLogicTest {
    @Test
    public void timestampUsesKstFormat() {
        long epoch = LocalDateTime.of(2026, 7, 24, 17, 10, 36).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
        assertEquals("[2026.07.24 | 17:10:36]\n테스트", TimestampUtil.prefix(epoch, "테스트"));
    }

    @Test
    public void automationDeliveryStampKeepsRawSignalSeparate() {
        long epoch = LocalDateTime.of(2026, 8, 8, 20, 51, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
        String raw = "[AUTOMATION_WORK_STEP JOB S001 R001]";
        assertEquals(raw, raw.trim());
        assertEquals("[2026.08.08 | 20:51:30]\n" + raw,
                OrchestrationStore.stampPrompt(raw, epoch));
        assertEquals("", OrchestrationStore.stampPrompt("  ", epoch));
    }

    @Test
    public void initialStartPromptIsExactAndUnstamped() {
        assertEquals("[AUTOMATION_START JOB-18]", OrchestrationStore.startPrompt(" JOB-18 "));
    }

    @Test
    public void orchestrationStatusSummarySeparatesTerminalAndRecoveryStates() {
        assertEquals("완료", OrchestrationStore.statusSummary(false, false, true, false,
                OrchestrationStore.DELIVERY_WAITING_RESPONSE, OrchestrationSignal.Type.DONE));
        assertEquals("일시정지", OrchestrationStore.statusSummary(false, true, true, false,
                OrchestrationStore.DELIVERY_WAITING_RESPONSE, OrchestrationSignal.Type.PAUSE));
        assertEquals("중단됨", OrchestrationStore.statusSummary(false, false, true, false,
                OrchestrationStore.DELIVERY_WAITING_RESPONSE, OrchestrationSignal.Type.ABORTED));
        assertEquals("사용자 조치 필요", OrchestrationStore.statusSummary(false, true, false, true,
                OrchestrationStore.DELIVERY_WAITING_RESPONSE, null));
        assertEquals("오류", OrchestrationStore.statusSummary(false, true, false, false,
                OrchestrationStore.DELIVERY_FAILED, null));
        assertEquals("진행 중", OrchestrationStore.statusSummary(true, false, false, false,
                OrchestrationStore.DELIVERY_WAITING_RESPONSE, null));
    }

    @Test
    public void terminalNotificationsUseDistinctExplicitMessages() {
        assertEquals("오토런 작업 완료", NotificationHelper.terminalTitle(OrchestrationSignal.Type.DONE));
        assertEquals("Job JOB-7 작업이 완료되었습니다.",
                NotificationHelper.terminalMessage(OrchestrationSignal.Type.DONE, "JOB-7"));
        assertEquals("오토런 작업 일시정지", NotificationHelper.terminalTitle(OrchestrationSignal.Type.PAUSE));
        assertEquals("Job JOB-7 작업이 일시정지되었습니다.",
                NotificationHelper.terminalMessage(OrchestrationSignal.Type.PAUSE, "JOB-7"));
        assertEquals("오토런 작업 중단", NotificationHelper.terminalTitle(OrchestrationSignal.Type.ABORTED));
        assertEquals("Job JOB-7 작업이 중단되었습니다.",
                NotificationHelper.terminalMessage(OrchestrationSignal.Type.ABORTED, "JOB-7"));
    }

    @Test
    public void parsesSupportedTargets() {
        assertTrue(TargetParser.isSupported("https://chatgpt.com/"));
        assertEquals("abc", TargetParser.conversationId("https://chatgpt.com/g/proj/c/abc"));
        assertEquals("proj", TargetParser.projectId("https://chatgpt.com/g/proj/c/abc"));
        assertFalse(TargetParser.isSupported("http://chatgpt.com/"));
        assertFalse(TargetParser.isSupported("https://example.com/"));
        assertNull(TargetParser.conversationId("https://chatgpt.com/"));
    }

    @Test
    public void targetMatchingRejectsHomeForProjectConversation() {
        String expected = "https://chatgpt.com/g/proj/c/abc";
        assertTrue(TargetParser.matchesTarget("existing", expected, "https://chatgpt.com/g/proj/c/abc"));
        assertFalse(TargetParser.matchesTarget("existing", expected, "https://chatgpt.com/"));
        assertFalse(TargetParser.matchesTarget("existing", expected, "https://chatgpt.com/c/abc"));
        assertFalse(TargetParser.matchesTarget("existing", expected, "https://chatgpt.com/g/proj/c/other"));
        assertTrue(TargetParser.matchesTarget("project", "https://chatgpt.com/g/proj/project", "https://chatgpt.com/g/proj/project"));
        assertFalse(TargetParser.matchesTarget("project", "https://chatgpt.com/g/proj/project", "https://chatgpt.com/"));
        assertTrue(TargetParser.matchesTarget("general", "https://chatgpt.com/", "https://chatgpt.com/"));
        assertFalse(TargetParser.matchesTarget("general", "https://chatgpt.com/", "https://chatgpt.com/c/abc"));
    }

    @Test
    public void startupIdentityAllowsSpaNormalizationOnlyForSameConversation() {
        String configured = "https://chatgpt.com/g/project/c/conversation-7?view=work";
        assertTrue(TargetParser.matchesConversationIdentity(configured,
                "https://www.chatgpt.com/c/conversation-7"));
        assertTrue(TargetParser.matchesConversationIdentity(configured,
                "https://chatgpt.com/g/normalized-project/c/conversation-7?model=auto"));
        assertFalse(TargetParser.matchesConversationIdentity(configured,
                "https://chatgpt.com/c/other-conversation"));
        assertFalse(TargetParser.matchesConversationIdentity(configured, "https://chatgpt.com/"));
    }

    @Test
    public void dailyScheduleSelectsNextTime() {
        Schedule schedule = new Schedule();
        schedule.times.clear();
        schedule.times.addAll(Arrays.asList("08:00", "17:00"));
        schedule.recurrence = "daily";
        long after = LocalDateTime.of(2026, 7, 24, 16, 0).atZone(Recurrence.KST).toInstant().toEpochMilli();
        long next = Recurrence.nextRunAt(schedule, after);
        assertEquals(LocalDateTime.of(2026, 7, 24, 17, 0), java.time.Instant.ofEpochMilli(next).atZone(Recurrence.KST).toLocalDateTime());
    }

    @Test
    public void weeklyScheduleSkipsUnselectedDays() {
        Schedule schedule = new Schedule();
        schedule.times.clear();
        schedule.times.add("09:00");
        schedule.weekdays.clear();
        schedule.weekdays.add(1);
        schedule.recurrence = "weekly";
        long after = LocalDateTime.of(2026, 7, 24, 10, 0).atZone(Recurrence.KST).toInstant().toEpochMilli();
        long next = Recurrence.nextRunAt(schedule, after);
        assertEquals(java.time.DayOfWeek.MONDAY, Recurrence.dayOfWeek(next));
    }

    @Test
    public void intervalScheduleUsesCompletionRelativeDelay() {
        Schedule schedule = new Schedule();
        schedule.recurrence = "interval";
        schedule.intervalMinutes = 45;
        long after = LocalDateTime.of(2026, 7, 24, 10, 0).atZone(Recurrence.KST).toInstant().toEpochMilli();
        assertEquals(after + 45L * 60_000L, Recurrence.nextRunAt(schedule, after));
        assertEquals(15, Schedule.normalizedIntervalMinutes(1));
        assertEquals(10_080, Schedule.normalizedIntervalMinutes(20_000));
    }

    @Test
    public void intervalDescriptionUsesPersistedAlarmTarget() {
        Schedule schedule = new Schedule();
        schedule.recurrence = "interval";
        schedule.intervalMinutes = 40;
        schedule.nextRunAt = LocalDateTime.of(2026, 7, 25, 9, 40).atZone(Recurrence.KST).toInstant().toEpochMilli();
        String atNine = Recurrence.describeNext(schedule,
                LocalDateTime.of(2026, 7, 25, 9, 0).atZone(Recurrence.KST).toInstant().toEpochMilli());
        String atNineTen = Recurrence.describeNext(schedule,
                LocalDateTime.of(2026, 7, 25, 9, 10).atZone(Recurrence.KST).toInstant().toEpochMilli());
        assertEquals(atNine, atNineTen);
        assertTrue(atNine.contains("2026-07-25 09:40"));
    }

    @Test
    public void editorParsesAndDeduplicatesTimes() {
        List<String> parsed = ScheduleEditorActivity.parseTimes("17:00, 08:00 17:00 invalid");
        assertEquals(Arrays.asList("08:00", "17:00"), parsed);
        assertNotNull(ScheduleEditorActivity.parseWeekdays("1,2,7"));
    }

    @Test
    public void editorShowsOnlyRelevantOptions() {
        assertFalse(ScheduleEditorActivity.requiresTargetUrl("general"));
        assertTrue(ScheduleEditorActivity.requiresTargetUrl("project"));
        assertTrue(ScheduleEditorActivity.requiresTargetUrl("existing"));

        assertTrue(ScheduleEditorActivity.showsExperience("general"));
        assertTrue(ScheduleEditorActivity.showsExperience("project"));
        assertFalse(ScheduleEditorActivity.showsExperience("existing"));

        assertTrue(ScheduleEditorActivity.showsReasoningEffort("general", "work"));
        assertTrue(ScheduleEditorActivity.showsReasoningEffort("project", "work"));
        assertFalse(ScheduleEditorActivity.showsReasoningEffort("general", "chat"));
        assertFalse(ScheduleEditorActivity.showsReasoningEffort("existing", "work"));

        assertTrue(ScheduleEditorActivity.showsClockTimes("once"));
        assertTrue(ScheduleEditorActivity.showsClockTimes("daily"));
        assertTrue(ScheduleEditorActivity.showsClockTimes("weekly"));
        assertFalse(ScheduleEditorActivity.showsClockTimes("interval"));

        assertFalse(ScheduleEditorActivity.showsWeekdays("once"));
        assertFalse(ScheduleEditorActivity.showsWeekdays("daily"));
        assertTrue(ScheduleEditorActivity.showsWeekdays("weekly"));
        assertFalse(ScheduleEditorActivity.showsWeekdays("interval"));

        assertFalse(ScheduleEditorActivity.showsIntervalMinutes("once"));
        assertFalse(ScheduleEditorActivity.showsIntervalMinutes("daily"));
        assertFalse(ScheduleEditorActivity.showsIntervalMinutes("weekly"));
        assertTrue(ScheduleEditorActivity.showsIntervalMinutes("interval"));
    }

    @Test
    public void editorRejectsTargetTypeMismatches() {
        assertTrue(ScheduleEditorActivity.isTargetValidForType("general", ""));
        assertTrue(ScheduleEditorActivity.isTargetValidForType("project", "https://chatgpt.com/g/proj"));
        assertFalse(ScheduleEditorActivity.isTargetValidForType("project", "https://chatgpt.com/g/proj/c/abc"));
        assertTrue(ScheduleEditorActivity.isTargetValidForType("existing", "https://chatgpt.com/g/proj/c/abc"));
        assertFalse(ScheduleEditorActivity.isTargetValidForType("existing", "https://chatgpt.com/g/proj"));
        assertFalse(ScheduleEditorActivity.isTargetValidForType("existing", "https://example.com/c/abc"));
    }

    @Test
    public void automationAlwaysReplacesExistingComposerTextAndGuardsRoute() {
        Schedule schedule = new Schedule();
        schedule.targetType = "existing";
        schedule.targetUrl = "https://chatgpt.com/g/proj/c/abc";
        String script = AutomationScript.build(schedule, "[2026.07.24 | 22:31:02]\n새 프롬프트", "run-1", 0);
        assertTrue(script.contains("textarea#prompt-textarea"));
        assertTrue(script.contains("main form [contenteditable=\"true\"]"));
        assertTrue(script.contains("execCommand('delete'"));
        assertTrue(script.contains("TARGET_CONTEXT_MISMATCH"));
        assertTrue(script.contains("expectedConversation=\"abc\""));
        assertFalse(script.contains("DRAFT_PRESENT"));
        assertFalse(script.contains("다른 초안"));
        assertTrue(script.contains("beforeinput"));
        assertTrue(script.contains("composer-submit-button"));
        assertTrue(script.contains("diagnostics"));
        assertTrue(script.contains("execCommand"));
    }

    @Test
    public void submissionMarkerPrecedesClickAndShortCircuitsRetry() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";
        String script = AutomationScript.build(schedule, "prompt", "run-marker", 0);
        assertTrue(script.contains("priorMarker"));
        assertTrue(script.contains("localStorage.setItem(markerKey,marker)"));
        assertTrue(script.contains("sessionStorage.setItem(markerKey,marker)"));
        assertTrue(script.contains("recoveredAfterNavigation:true"));
        assertTrue(script.indexOf("localStorage.setItem(markerKey,marker)") < script.indexOf("send.click()"));
        assertTrue(script.contains("return result('SUBMITTED'"));
        assertFalse(script.contains("READY_TO_SUBMIT"));
    }

    @Test
    public void automationUsesOneIdempotentComposerStrategy() {
        Schedule schedule = new Schedule();
        schedule.targetType = "existing";
        schedule.targetUrl = "https://chatgpt.com/g/proj/c/abc";
        String first = AutomationScript.build(schedule, "prompt", "run-1", 0);
        String paste = AutomationScript.build(schedule, "prompt", "run-1", 2);
        String dom = AutomationScript.build(schedule, "prompt", "run-1", 3);
        assertTrue(first.contains("attempt:0"));
        assertTrue(paste.contains("attempt:2"));
        assertTrue(dom.contains("attempt:3"));
        assertTrue(first.contains("single-execCommand"));
        assertTrue(paste.contains("single-execCommand"));
        assertTrue(dom.contains("single-execCommand"));
        assertFalse(first.contains("paste+execCommand"));
        assertFalse(first.contains("dom+input"));
        assertFalse(first.contains("fire('beforeinput','insertText',expected)"));
        assertTrue(dom.contains("actualPreview"));
    }

    @Test
    public void verificationKeepsProjectContextAfterNewConversationCreation() {
        Schedule schedule = new Schedule();
        schedule.targetType = "project";
        schedule.targetUrl = "https://chatgpt.com/g/proj/project";
        String script = AutomationScript.verify(schedule, "[2026.07.24 | 22:31:02]\n새 프롬프트");
        assertTrue(script.contains("afterSubmit=true"));
        assertTrue(script.contains("actualProject===expectedProject"));
        assertTrue(script.contains("VERIFIED"));
    }

    @Test
    public void existingConversationInheritsCurrentMode() {
        assertEquals("inherit", Schedule.normalizedExperience("existing", "chat"));
        assertEquals("inherit", Schedule.normalizedExperience("existing", "work"));
        assertEquals("chat", Schedule.normalizedExperience("general", "inherit"));
        assertEquals("work", Schedule.normalizedExperience("project", "work"));
    }

    @Test
    public void reasoningEffortIsWorkOnlyAndBackwardCompatible() {
        assertEquals("inherit", Schedule.normalizedReasoningEffort("chat", "max"));
        assertEquals("inherit", Schedule.normalizedReasoningEffort("inherit", "high"));
        assertEquals("light", Schedule.normalizedReasoningEffort("work", "light"));
        assertEquals("medium", Schedule.normalizedReasoningEffort("work", "medium"));
        assertEquals("high", Schedule.normalizedReasoningEffort("work", "high"));
        assertEquals("xhigh", Schedule.normalizedReasoningEffort("work", "xhigh"));
        assertEquals("max", Schedule.normalizedReasoningEffort("work", "max"));
        assertEquals("ultra", Schedule.normalizedReasoningEffort("work", "ultra"));
        assertEquals("inherit", Schedule.normalizedReasoningEffort("work", "unknown"));
    }

    @Test
    public void ultraDisplayAndJsonRoundTripRemainBackwardCompatible() throws Exception {
        assertEquals("울트라", Schedule.displayReasoningEffort("work", "ultra"));
        assertEquals("inherit", Schedule.displayReasoningEffort("chat", "ultra"));
        assertEquals("ultra", ScheduleEditorActivity.reasoningEffortValue("울트라"));
        assertEquals("xhigh", ScheduleEditorActivity.reasoningEffortValue("xhigh"));

        Schedule schedule = new Schedule();
        schedule.experience = "work";
        schedule.reasoningEffort = "ultra";
        JSONObject json = schedule.toJson();
        assertEquals("ultra", json.getString("reasoningEffort"));
        assertEquals("ultra", Schedule.fromJson(json).reasoningEffort);

        JSONObject legacy = new JSONObject(json.toString());
        legacy.remove("reasoningEffort");
        assertEquals("inherit", Schedule.fromJson(legacy).reasoningEffort);

        JSONObject unknown = new JSONObject(json.toString());
        unknown.put("reasoningEffort", "not-a-real-effort");
        assertEquals("inherit", Schedule.fromJson(unknown).reasoningEffort);
    }

    @Test
    public void workModelIsWorkOnlyAndBackwardCompatible() {
        assertEquals("inherit", Schedule.normalizedWorkModel("chat", "sol"));
        assertEquals("inherit", Schedule.normalizedWorkModel("inherit", "terra"));
        assertEquals("sol", Schedule.normalizedWorkModel("work", "sol"));
        assertEquals("terra", Schedule.normalizedWorkModel("work", "terra"));
        assertEquals("luna", Schedule.normalizedWorkModel("work", "luna"));
        assertEquals("inherit", Schedule.normalizedWorkModel("work", "unknown"));
    }}
