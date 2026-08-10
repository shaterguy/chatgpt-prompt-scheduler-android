from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected pattern not found: {path}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/com/shaterguy/chatgptpromptscheduler/OrchestrationActivity.java",
    """    static ResumePath resumePath(boolean waitingForUser, boolean fullRelay) {\n        if (fullRelay) return ResumePath.RECONCILE;\n        if (waitingForUser) return ResumePath.USER_ACTION_RESOLVED;\n        return ResumePath.BOOTSTRAP;\n    }\n""",
    """    static ResumePath resumePath(boolean waitingForUser, boolean fullRelay) {\n        // WAITING_USER is an explicit user-confirmation transition, not an observational recovery.\n        // It must bypass generic reconciliation even after both relay rooms are provisioned.\n        if (waitingForUser) return ResumePath.USER_ACTION_RESOLVED;\n        if (fullRelay) return ResumePath.RECONCILE;\n        return ResumePath.BOOTSTRAP;\n    }\n""",
)

Path("app/src/test/java/com/shaterguy/chatgptpromptscheduler/UnifiedResumeTest.java").write_text(
    """package com.shaterguy.chatgptpromptscheduler;\n\nimport static org.junit.Assert.assertEquals;\n\nimport org.junit.Test;\n\npublic class UnifiedResumeTest {\n    @Test\n    public void waitingUserAlwaysUsesDirectResolvedPath() {\n        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,\n                OrchestrationActivity.resumePath(true, true));\n        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,\n                OrchestrationActivity.resumePath(true, false));\n    }\n\n    @Test\n    public void fullRelayWithoutWaitingUserReconciles() {\n        assertEquals(OrchestrationActivity.ResumePath.RECONCILE,\n                OrchestrationActivity.resumePath(false, true));\n    }\n\n    @Test\n    public void incompleteBootstrapWithoutWaitingUserKeepsBootstrapPath() {\n        assertEquals(OrchestrationActivity.ResumePath.BOOTSTRAP,\n                OrchestrationActivity.resumePath(false, false));\n    }\n}\n""",
    encoding="utf-8",
)

Path("app/src/test/java/com/shaterguy/chatgptpromptscheduler/Rc6WaitingUserResumeRegressionTest.java").write_text(
    """package com.shaterguy.chatgptpromptscheduler;\n\nimport static org.junit.Assert.assertEquals;\n\nimport org.junit.Test;\n\npublic class Rc6WaitingUserResumeRegressionTest {\n    @Test\n    public void fullyProvisionedWaitingUserJobStillSendsUserResolvedPrompt() {\n        assertEquals(OrchestrationActivity.ResumePath.USER_ACTION_RESOLVED,\n                OrchestrationActivity.resumePath(true, true));\n        assertEquals(\"[AUTOMATION_USER_RESOLVED AR-20260810-101754-6XZR4U REFRESH-DOORAY-DBINS-ACTIONS]\",\n                OrchestrationStore.userResolvedPrompt(\n                        \"AR-20260810-101754-6XZR4U\",\n                        \"REFRESH-DOORAY-DBINS-ACTIONS\"));\n    }\n\n    @Test\n    public void ordinaryFullRelayResumeStillUsesReconciliation() {\n        assertEquals(OrchestrationActivity.ResumePath.RECONCILE,\n                OrchestrationActivity.resumePath(false, true));\n    }\n}\n""",
    encoding="utf-8",
)

replace_once(
    "app/build.gradle",
    """        versionCode 27\n        versionName '0.1.22-rc5'\n""",
    """        versionCode 28\n        versionName '0.1.22-rc6'\n""",
)

print("RC6 WAITING_USER resume patch applied")
