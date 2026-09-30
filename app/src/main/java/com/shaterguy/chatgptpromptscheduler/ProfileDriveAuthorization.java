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

    interface Callback extends ProfileAuthorizationResultPolicy.Listener {
        void resolution(PendingIntent pendingIntent);
    }

    static void request(Context context, ProfileAuthorizationSession.Mode mode, Callback callback) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(List.of(new Scope(METADATA_SCOPE), new Scope(DOCUMENTS_SCOPE)))
                .setOptOutIncludingGrantedScopes(true);
        if (mode == ProfileAuthorizationSession.Mode.PREVIOUS_CONSENT) builder.setPrompt(AuthorizationRequest.Prompt.CONSENT);
        Identity.getAuthorizationClient(context).authorize(builder.build())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        if (result.getPendingIntent() == null) callback.failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.SDK_ERROR,null,null));
                        else callback.resolution(result.getPendingIntent());
                    } else deliver(result, callback);
                }).addOnFailureListener(error -> callback.failed(issue(error)));
    }

    static void fromIntent(Context context, Intent data, int resultCode, Callback callback) {
        ProfileAuthorizationResultPolicy.handle(data, resultCode, value -> {
            try {
                AuthorizationResult result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(value);
                return result == null ? null : result.getAccessToken();
            } catch (ApiException error) {
                throw new ProfileAuthorizationResultPolicy.GoogleStatusException(error.getStatusCode());
            }
        }, callback);
    }

    private static ProfileAuthorizationIssue issue(Exception error) {
        return error instanceof ApiException
                ? new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.GOOGLE_API,((ApiException)error).getStatusCode(),null)
                : new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.SDK_ERROR,null,null);
    }

    private static void deliver(AuthorizationResult result, Callback callback) {
        String token = result == null ? null : result.getAccessToken();
        if (token == null || token.isEmpty()) callback.failed(new ProfileAuthorizationIssue(ProfileAuthorizationIssue.Kind.NO_TOKEN,null,null));
        else callback.authorized(token);
    }

    private ProfileDriveAuthorization() {}
}
