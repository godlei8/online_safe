package com.godlei.onlinesafe.admin.web;

public record CreateInvitationResponse(
        InvitationResponse invitation,
        String plainCode
) {
    public static CreateInvitationResponse from(InvitationResponse invitation, String plainCode) {
        return new CreateInvitationResponse(invitation, plainCode);
    }
}
