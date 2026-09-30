package com.shaterguy.chatgptpromptscheduler;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class AutoOnlySelectionTest {
    @Test public void absentOldChoiceStaysUnselectedUntilExplicitReselection() {
        List<String> choices = ScheduleEditorActivity.profileChoices("keep", List.of("automatic"));
        assertEquals(List.of("", "keep", "automatic"), choices);
        assertEquals(0, ScheduleEditorActivity.profileChoiceIndex(choices, "old-manual"));
        assertEquals(0, ScheduleEditorActivity.profileChoiceIndex(choices, ""));
        assertEquals(1, ScheduleEditorActivity.profileChoiceIndex(choices, "keep"));
        assertEquals(2, ScheduleEditorActivity.profileChoiceIndex(choices, "automatic"));
    }
    @Test public void emptyCanonicalListContainsNoExplicitModelFallback() {
        assertEquals(List.of("", "inherit"), ScheduleEditorActivity.profileChoices("inherit", List.of()));
        assertEquals(List.of(""), ScheduleEditorActivity.profileChoices(null, List.of()));
    }
    @Test public void explicitModelAllowsOnlyItsCanonicalReasonings() {
        assertEquals(List.of("", "high", "low"), ScheduleEditorActivity.profileChoices(null, List.of("high", "low", "high")));
    }
    @Test public void validationRejectsMissingOrRemovedValuesBeforeScheduleMutation() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK,
                ProfileRegistrySyncTest.document("automatic", "high"), "v4");
        assertFalse(registry.isSelectionAvailable("general", "work", "keep", "", ""));
        assertFalse(registry.isSelectionAvailable("general", "work", "keep", "old-manual", "high"));
        assertFalse(registry.isSelectionAvailable("general", "work", "keep", "automatic", "inherit"));
        assertFalse(registry.isSelectionAvailable("general", "work", "keep", "inherit", "high"));
        assertFalse(registry.isSelectionAvailable("general", "work", "keep", "automatic", "low"));
        assertTrue(registry.isSelectionAvailable("general", "work", "keep", "automatic", "high"));
        assertTrue(registry.isSelectionAvailable("general", "work", "keep", "inherit", "inherit"));
        assertFalse(registry.isSelectionAvailable("general", "chat", "", "inherit", "inherit"));
        assertFalse(registry.isSelectionAvailable("general", "chat", "old-manual", "inherit", "inherit"));
        assertTrue(registry.isSelectionAvailable("general", "chat", "keep", "inherit", "inherit"));
        assertTrue(registry.isSelectionAvailable("existing", "inherit", "", "", ""));
    }
    @Test public void registryAttachmentNeverChangesStoredScheduleEvenWhenUnavailable() throws Exception {
        Schedule schedule = new Schedule(); schedule.experience="work"; schedule.workModel="old-manual";
        schedule.reasoningEffort="high"; schedule.prompt="preserve my prompt"; schedule.name="preserve my schedule";
        schedule.lastRunAt=123; schedule.nextRunAt=456;
        String before = schedule.toJson().toString();
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences());
        registry.attach(schedule);
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        assertEquals(before, schedule.toJson().toString());
    }
    @Test public void staleAttachmentCannotApplyAnotherSelectionOrUnverifiedState() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, ProfileRegistrySyncTest.document("automatic", "high"), "v1");
        Schedule schedule = new Schedule();schedule.experience="work";schedule.workModel="automatic";schedule.reasoningEffort="high";
        registry.attach(schedule);assertNotNull(RequestProfileEngine.forSchedule(schedule));
        schedule.workModel="removed";
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        schedule.workModel="automatic";schedule.requestProfileRegistryResolved=false;
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
    }
    @Test public void removingCanonicalPairInvalidatesAnAlreadyAttachedSchedule() throws Exception {
        RequestProfileRegistry registry = new RequestProfileRegistry(ProfileRegistrySyncTest.memoryPreferences());
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, ProfileRegistrySyncTest.document("old", "high"), "v1");
        Schedule schedule = new Schedule(); schedule.experience="work";schedule.workModel="old";schedule.reasoningEffort="high";
        registry.attach(schedule);assertNotNull(RequestProfileEngine.forSchedule(schedule));
        registry.replaceCanonicalSnapshot(RequestProfileEngine.Mode.WORK, ProfileRegistrySyncTest.document("new", "low"), "v2");
        registry.attach(schedule);
        assertThrows(IllegalArgumentException.class, () -> RequestProfileEngine.forSchedule(schedule));
        assertEquals("old", schedule.workModel);assertEquals("high", schedule.reasoningEffort);
    }
}
