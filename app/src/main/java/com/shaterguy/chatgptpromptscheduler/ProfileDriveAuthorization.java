package com.shaterguy.chatgptpromptscheduler;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import java.util.List;

/** Google-managed account selection/consent. Access tokens stay in process memory. */
final class ProfileDriveAuthorization {
    static final String METADATA_SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly";
    static final String DOCUMENTS_SCOPE = "https://www.googleapis.com/auth/documents.readonly";

    interface Callback {
        void authorized(String accessToken);
        void resolution(PendingIntent pendingIntent);
        void failed();
    }

    static void request(Context context, boolean userInitiated, Callback callback) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(List.of(new Scope(METADATA_SCOPE), new Scope(DOCUMENTS_SCOPE)))
                .setOptOutIncludingGrantedScopes(true);
        if (userInitiated) builder.setPrompt(AuthorizationRequest.Prompt.CONSENT);
        Identity.getAuthorizationClient(context).authorize(builder.build())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        if (result.getPendingIntent() == null) callback.failed();
                        else callback.resolution(result.getPendingIntent());
                    } else deliver(result, callback);
                }).addOnFailureListener(error -> callback.failed());
    }

    static void fromIntent(Context context, Intent data, Callback callback) {
        try { deliver(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data), callback); }
        catch (ApiException | RuntimeException error) { callback.failed(); }
    }

    private static void deliver(AuthorizationResult result, Callback callback) {
        String token = result == null ? null : result.getAccessToken();
        if (token == null || token.isEmpty()) callback.failed();
        else callback.authorized(token);
    }

    private ProfileDriveAuthorization() {}
}
