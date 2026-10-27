package com.civicbridge.analytics;

import com.civicbridge.entity.Role;
import com.civicbridge.entity.User;
import com.civicbridge.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end authorization + session lifecycle. Redis is intentionally unreachable (DB fallback path). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsSecurityIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("email", email, "password", "Passw0rd!123", "fullName", "Test User"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private String loginAdmin(String email) throws Exception {
        users.save(User.builder().email(email).passwordHash(encoder.encode("Passw0rd!123"))
                .fullName("Admin").role(Role.ADMIN).enabled(true).build());
        String body = json.writeValueAsString(java.util.Map.of("email", email, "password", "Passw0rd!123"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    @Test
    void analyticsRequiresAdmin() throws Exception {
        String citizen = register("c-" + UUID.randomUUID() + "@example.com");
        String admin = loginAdmin("a-" + UUID.randomUUID() + "@example.com");

        mvc.perform(get("/api/admin/analytics/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/analytics/overview").header("Authorization", "Bearer " + citizen))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics/trends").header("Authorization", "Bearer " + citizen))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics/overview").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredUsers").isNumber())
                .andExpect(jsonPath("$.activeSessions").isNumber());
        mvc.perform(get("/api/admin/analytics/trends?granularity=weekly").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTokenImmediately() throws Exception {
        String token = register("l-" + UUID.randomUUID() + "@example.com");
        mvc.perform(post("/api/session/heartbeat").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(post("/api/session/heartbeat").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()); // idempotent
    }

    @Test
    void forgedOrGarbageTokenIsRejected() throws Exception {
        mvc.perform(post("/api/session/heartbeat").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void collectRequiresConsentHeaderAndValidInput() throws Exception {
        String vid = UUID.randomUUID().toString();
        String ok = json.writeValueAsString(java.util.Map.of("path", "/dashboard", "visitorId", vid));
        mvc.perform(post("/api/analytics/collect").contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isBadRequest()); // no consent header -> nothing stored
        mvc.perform(post("/api/analytics/collect").header("X-Analytics-Consent", "granted")
                .contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isNoContent());
        String bad = json.writeValueAsString(java.util.Map.of("path", "/x?y=1", "visitorId", vid));
        mvc.perform(post("/api/analytics/collect").header("X-Analytics-Consent", "granted")
                .contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
    }

    @Test
    void consentEndpointRejectsMarketingAndWrongVersion() throws Exception {
        String ver = "2026-10-1";
        mvc.perform(post("/api/consent").contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyVersion\":\"" + ver + "\",\"preferences\":false,\"analytics\":true,\"marketing\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/consent").contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyVersion\":\"" + ver + "\",\"preferences\":false,\"analytics\":true,\"marketing\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/consent").contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyVersion\":\"old\",\"preferences\":false,\"analytics\":true,\"marketing\":false}"))
                .andExpect(status().isBadRequest());
    }
}
