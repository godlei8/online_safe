package com.godlei.onlinesafe.datarecovery.web;

import java.time.Instant;

public record ReauthResponse(Instant verifiedUntil) {
}
