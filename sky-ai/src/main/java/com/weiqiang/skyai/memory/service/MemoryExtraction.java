package com.weiqiang.skyai.memory.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MemoryExtraction(
        @JsonProperty("favorite_dishes") FactEntry favoriteDishes,
        @JsonProperty("favorite_flavors") FactEntry favoriteFlavors,
        @JsonProperty("dietary_restrictions") FactEntry dietaryRestrictions,
        @JsonProperty("default_address") FactEntry defaultAddress,
        @JsonProperty("operational_notes") FactEntry operationalNotes,
        @JsonProperty("corrections") List<String> corrections) {
}
