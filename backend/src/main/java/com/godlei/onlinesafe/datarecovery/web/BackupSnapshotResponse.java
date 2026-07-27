package com.godlei.onlinesafe.datarecovery.web;

import tools.jackson.databind.JsonNode;

public record BackupSnapshotResponse(JsonNode snapshot) {
}
