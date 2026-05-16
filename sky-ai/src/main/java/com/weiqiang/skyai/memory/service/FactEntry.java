package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.databind.JsonNode;

public record FactEntry(JsonNode value, double confidence) {
}
