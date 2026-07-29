package com.godlei.onlinesafe.vaultimport.web;

import tools.jackson.databind.JsonNode;

public record ImportCandidatePatchRequest(
        JsonNode payload,
        String duplicateAction,
        Double confidence,
        Boolean forceReady
) {
}
