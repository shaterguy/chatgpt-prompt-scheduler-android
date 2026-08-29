package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ProjectCatalogClearAndroidTest {
    @Test public void clearAllRemovesOnlyCatalogAndAllowsFreshRegistration() {
        Context context = ApplicationProvider.getApplicationContext();
        ProjectCatalog catalog = new ProjectCatalog(context);
        catalog.clearAll();
        assertTrue(catalog.addVisitedProject("https://chatgpt.com/g/g-p-alpha/project", "Alpha"));
        assertTrue(catalog.addVisitedProject("https://chatgpt.com/g/g-p-beta/project", "Beta"));
        assertEquals(2, catalog.entries().size());
        assertEquals(2, catalog.clearAll());
        assertTrue(catalog.entries().isEmpty());
        assertTrue(catalog.addVisitedProject("https://chatgpt.com/g/g-p-alpha/project", "Alpha 다시 등록"));
        assertEquals(1, catalog.entries().size());
        assertEquals("Alpha 다시 등록", catalog.displayName(catalog.entries().get(0)));
        catalog.clearAll();
    }
}
