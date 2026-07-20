package com.godlei.onlinesafe.auth.web;

public record CsrfResponse(String cookieName, String headerName, String parameterName) {
}
