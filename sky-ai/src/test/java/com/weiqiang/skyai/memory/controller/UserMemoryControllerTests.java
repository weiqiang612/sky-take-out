package com.weiqiang.skyai.memory.controller;

import com.weiqiang.skyai.memory.model.MemoryFactKey;
import com.weiqiang.skyai.memory.model.MemoryFactSourceType;
import com.weiqiang.skyai.memory.model.UserMemoryFact;
import com.weiqiang.skyai.memory.model.UserMemoryFactHistory;
import com.weiqiang.skyai.memory.service.UserMemoryFactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserMemoryController.class)
@Import(MemoryApiExceptionHandler.class)
class UserMemoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMemoryFactService userMemoryFactService;

    @Test
    void listFactsReturnsSortedDtos() throws Exception {
        UserMemoryFact manual = new UserMemoryFact();
        manual.setFactKey(MemoryFactKey.DEFAULT_ADDRESS.value());
        manual.setFactValue("{\"raw\":\"Manual address\"}");
        manual.setSourceType(MemoryFactSourceType.USER_MANUAL);
        manual.setConfidence(1.0);
        manual.setCreatedAt(Instant.parse("2026-05-10T00:00:00Z"));
        manual.setUpdatedAt(Instant.parse("2026-05-10T00:00:00Z"));

        UserMemoryFact inferred = new UserMemoryFact();
        inferred.setFactKey(MemoryFactKey.FAVORITE_FLAVORS.value());
        inferred.setFactValue("清淡");
        inferred.setSourceType(MemoryFactSourceType.INFERRED);
        inferred.setConfidence(0.9);
        inferred.setCreatedAt(Instant.parse("2026-05-10T00:00:01Z"));
        inferred.setUpdatedAt(Instant.parse("2026-05-10T00:00:01Z"));

        when(userMemoryFactService.findFactsSorted("u1")).thenReturn(List.of(manual, inferred));

        mockMvc.perform(get("/api/v1/users/u1/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].factKey").value("default_address"))
                .andExpect(jsonPath("$[0].factValue.raw").value("Manual address"))
                .andExpect(jsonPath("$[0].sourceType").value("USER_MANUAL"))
                .andExpect(jsonPath("$[1].factKey").value("favorite_flavors"))
                .andExpect(jsonPath("$[1].factValue").value("清淡"));
    }

    @Test
    void putFactAcceptsJsonValueAndReturnsSavedFact() throws Exception {
        UserMemoryFact saved = new UserMemoryFact();
        saved.setUserId("u1");
        saved.setFactKey(MemoryFactKey.DEFAULT_ADDRESS.value());
        saved.setFactValue("{\"raw\":\"No. 1 Road\"}");
        saved.setSourceType(MemoryFactSourceType.USER_MANUAL);
        saved.setConfidence(1.0);
        saved.setCreatedAt(Instant.parse("2026-05-10T00:00:00Z"));
        saved.setUpdatedAt(Instant.parse("2026-05-10T00:00:01Z"));

        doNothing().when(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.DEFAULT_ADDRESS), org.mockito.ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), eq(MemoryFactSourceType.USER_MANUAL), eq(1.0), eq(true));
        when(userMemoryFactService.findFact("u1", MemoryFactKey.DEFAULT_ADDRESS)).thenReturn(Optional.of(saved));

        mockMvc.perform(put("/api/v1/users/u1/memory/default_address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":{\"raw\":\"No. 1 Road\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factKey").value("default_address"))
                .andExpect(jsonPath("$.factValue.raw").value("No. 1 Road"))
                .andExpect(jsonPath("$.sourceType").value("USER_MANUAL"))
                .andExpect(jsonPath("$.confidence").value(1.0));

        verify(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.DEFAULT_ADDRESS), org.mockito.ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), eq(MemoryFactSourceType.USER_MANUAL), eq(1.0), eq(true));
    }

    @Test
    void putFactRejectsInvalidFactKey() throws Exception {
        mockMvc.perform(put("/api/v1/users/u1/memory/not_a_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("factKey must be one of")));
    }

    @Test
    void putFactRejectsWrongArrayShape() throws Exception {
        mockMvc.perform(put("/api/v1/users/u1/memory/favorite_dishes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":\"dumplings\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("favorite_dishes must be a JSON array"));
    }

    @Test
    void putFactRejectsDefaultAddressWithoutRawField() throws Exception {
        mockMvc.perform(put("/api/v1/users/u1/memory/default_address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":{\"city\":\"Shanghai\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("default_address must be a JSON object with raw"));
    }

    @Test
    void putFactAcceptsUserProfileNotesAsJsonString() throws Exception {
        UserMemoryFact saved = new UserMemoryFact();
        saved.setUserId("u1");
        saved.setFactKey(MemoryFactKey.USER_PROFILE_NOTES.value());
        saved.setFactValue("我在减脂");
        saved.setSourceType(MemoryFactSourceType.USER_MANUAL);
        saved.setConfidence(1.0);
        saved.setCreatedAt(Instant.parse("2026-05-10T00:00:00Z"));
        saved.setUpdatedAt(Instant.parse("2026-05-10T00:00:01Z"));

        doNothing().when(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.USER_PROFILE_NOTES), org.mockito.ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), eq(MemoryFactSourceType.USER_MANUAL), eq(1.0), eq(true));
        when(userMemoryFactService.findFact("u1", MemoryFactKey.USER_PROFILE_NOTES)).thenReturn(Optional.of(saved));

        mockMvc.perform(put("/api/v1/users/u1/memory/user_profile_notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":\"我在减脂\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factKey").value("user_profile_notes"))
                .andExpect(jsonPath("$.factValue").value("我在减脂"))
                .andExpect(jsonPath("$.sourceType").value("USER_MANUAL"));

        verify(userMemoryFactService).upsertFact(eq("u1"), eq(MemoryFactKey.USER_PROFILE_NOTES), org.mockito.ArgumentMatchers.<com.fasterxml.jackson.databind.JsonNode>any(), eq(MemoryFactSourceType.USER_MANUAL), eq(1.0), eq(true));
    }

    @Test
    void putFactRejectsUserProfileNotesObjectValue() throws Exception {
        mockMvc.perform(put("/api/v1/users/u1/memory/user_profile_notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":{\"value\":\"我在减脂\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("user_profile_notes must be a JSON string"));
    }

    @Test
    void putFactRejectsUserProfileNotesArrayValue() throws Exception {
        mockMvc.perform(put("/api/v1/users/u1/memory/user_profile_notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"factValue\":[\"我在减脂\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("user_profile_notes must be a JSON string"));
    }

    @Test
    void deleteFactReturnsNoContent() throws Exception {
        doNothing().when(userMemoryFactService).deleteFact("u1", MemoryFactKey.OPERATIONAL_NOTES);

        mockMvc.perform(delete("/api/v1/users/u1/memory/operational_notes"))
                .andExpect(status().isNoContent());

        verify(userMemoryFactService).deleteFact("u1", MemoryFactKey.OPERATIONAL_NOTES);
    }

    @Test
    void historyReturnsOrderedRows() throws Exception {
        UserMemoryFactHistory newer = new UserMemoryFactHistory();
        newer.setOldValue("A");
        newer.setNewValue("B");
        newer.setSourceType(MemoryFactSourceType.USER_MANUAL);
        newer.setChangedAt(Instant.parse("2026-05-11T00:00:00Z"));

        UserMemoryFactHistory older = new UserMemoryFactHistory();
        older.setOldValue("B");
        older.setNewValue("C");
        older.setSourceType(MemoryFactSourceType.USER);
        older.setChangedAt(Instant.parse("2026-05-10T00:00:00Z"));

        when(userMemoryFactService.findHistory("u1", MemoryFactKey.DEFAULT_ADDRESS)).thenReturn(List.of(newer, older));

        mockMvc.perform(get("/api/v1/users/u1/memory/default_address/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldValue").value("A"))
                .andExpect(jsonPath("$[0].newValue").value("B"))
                .andExpect(jsonPath("$[0].sourceType").value("USER_MANUAL"))
                .andExpect(jsonPath("$[1].oldValue").value("B"))
                .andExpect(jsonPath("$[1].newValue").value("C"));
    }
}
