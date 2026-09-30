package com.shaterguy.chatgptpromptscheduler;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ProfilePreparationLeaseTest {
    @Test public void completionFailureAndDestroyReleaseOnlyOnce() {
        for (String terminal : new String[]{"completion", "failure", "destroy"}) {
            int[] releases = {0}; ProfilePreparationLease lease = new ProfilePreparationLease();
            lease.hold(() -> releases[0]++); lease.close(); lease.close();
            assertEquals(terminal, 1, releases[0]);
        }
    }
    @Test public void replacingPreparationReleasesPreviousAndHasBoundedTimeout() {
        int[] releases = {0}; ProfilePreparationLease lease = new ProfilePreparationLease();
        lease.hold(() -> releases[0]++); lease.hold(() -> releases[0]++);
        assertEquals(1, releases[0]); lease.close(); assertEquals(2, releases[0]);
        assertTrue(ProfilePreparationLease.TIMEOUT_MS > 60_000L);
        assertTrue(ProfilePreparationLease.TIMEOUT_MS <= 90_000L);
    }
}
