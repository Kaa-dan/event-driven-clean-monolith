package com.fleet.storefront.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.storefront.application.port.in.ManageStaffUseCase;
import com.fleet.storefront.application.port.in.ManageStaffUseCase.CreateStaffCommand;
import com.fleet.storefront.domain.staff.Staff;
import com.fleet.storefront.domain.staff.StaffRole;
import com.fleet.storefront.domain.terminal.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR 0017: staff PIN sign-in on paired devices, role narrowing and attribution of documents. */
class StaffAuthIT extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ManageStaffUseCase staff;

    @Autowired
    ObjectMapper json;

    Staff boss;

    @BeforeEach
    void givenStaff() {
        boss = staff.create(new CreateStaffCommand("boss", "Boss", StaffRole.MANAGER, "1234"));
        staff.create(new CreateStaffCommand("carl", "Carl", StaffRole.CASHIER, "1111"));
        staff.create(new CreateStaffCommand("kofi", "Kofi", StaffRole.KITCHEN, "3333"));
    }

    String signIn(String apiKey, String username, String pin) throws Exception {
        String body = mvc.perform(post("/api/v1/staff-sessions").with(as(apiKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    @Test
    void theRosterShowsWhoMaySignInOnTheDevice() throws Exception {
        mvc.perform(get("/api/v1/staff/roster").with(as(POS_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", containsInAnyOrder("boss", "carl")))
                .andExpect(jsonPath("$[0].pin").doesNotExist());
        mvc.perform(get("/api/v1/staff/roster").with(as(KITCHEN_KEY)))
                .andExpect(jsonPath("$[*].username", containsInAnyOrder("boss", "kofi")));
        mvc.perform(get("/api/v1/staff/roster").with(as(HQ_KEY))).andExpect(status().isForbidden());
    }

    @Test
    void signingInNarrowsTheDeviceRoleToThePersonsJob() throws Exception {
        String carl = signIn(MANAGER_KEY, "carl", "1111");

        mvc.perform(get("/api/v1/me").with(as(MANAGER_KEY)).header("X-Staff-Session", carl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.staff.username").value("carl"))
                .andExpect(jsonPath("$.staff.effectiveRole").value("POS"));
        // A cashier on the back-office device still cannot use manager endpoints.
        mvc.perform(get("/api/v1/ops/outbox").with(as(MANAGER_KEY)).header("X-Staff-Session", carl))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ops/outbox").with(as(MANAGER_KEY))).andExpect(status().isOk());

        String bossOnPos = signIn(POS_KEY, "boss", "1234");
        mvc.perform(get("/api/v1/ops/outbox").with(as(POS_KEY)).header("X-Staff-Session", bossOnPos))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPinsAreRejected_andLockTheAccountAfterFiveAttempts() throws Exception {
        for (int i = 1; i < Staff.MAX_FAILED_ATTEMPTS; i++) {
            mvc.perform(post("/api/v1/staff-sessions").with(as(POS_KEY)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"carl\",\"pin\":\"0000\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/v1/staff-sessions").with(as(POS_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carl\",\"pin\":\"0000\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("STAFF_LOCKED"));
        mvc.perform(post("/api/v1/staff-sessions").with(as(POS_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carl\",\"pin\":\"1111\"}"))
                .andExpect(status().isLocked());
        mvc.perform(post("/api/v1/staff-sessions").with(as(KITCHEN_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"boss\",\"pin\":\"1234\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void sessionsAreBoundToTheirDevice_andEndOnSignOut() throws Exception {
        String carl = signIn(POS_KEY, "carl", "1111");
        givenDevice("kiosk-2", Role.POS, "sf_it_pos_2");

        mvc.perform(get("/api/v1/me").with(as("sf_it_pos_2")).header("X-Staff-Session", carl))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STAFF_SESSION_INVALID"));

        mvc.perform(delete("/api/v1/staff-sessions/current").with(as(POS_KEY)).header("X-Staff-Session", carl))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").with(as(POS_KEY)).header("X-Staff-Session", carl))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordersAndPaymentsRecordWhoTookThem() throws Exception {
        givenProduct("WATER", "2.50", false, 10);
        String carl = signIn(POS_KEY, "carl", "1111");

        String order = mvc.perform(post("/api/v1/orders").with(as(POS_KEY)).header("X-Staff-Session", carl)
                        .header("Idempotency-Key", "k-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"WATER\",\"quantity\":1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode placed = json.readTree(order);
        UUID orderId = UUID.fromString(placed.get("id").asText());
        mvc.perform(post("/api/v1/orders/" + orderId + "/payments").with(as(POS_KEY)).header("X-Staff-Session", carl)
                        .header("Idempotency-Key", "p-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CASH\",\"amount\":" + placed.get("total").asText()
                                + ",\"tendered\":10}"))
                .andExpect(status().isCreated());

        UUID carlId = jdbc.sql("SELECT id FROM staff WHERE username = 'carl'").query(UUID.class).single();
        assertThat(jdbc.sql("SELECT staff_id FROM customer_order WHERE id = :id").param("id", orderId)
                .query(UUID.class).single()).isEqualTo(carlId);
        assertThat(jdbc.sql("SELECT staff_id FROM payment WHERE order_id = :id").param("id", orderId)
                .query(UUID.class).single()).isEqualTo(carlId);
    }

    @Test
    void managersManageStaff_butNotTheLastManager() throws Exception {
        mvc.perform(post("/api/v1/staff").with(as(MANAGER_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wendy\",\"displayName\":\"Wendy\",\"role\":\"WAITER\",\"pin\":\"2222\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("wendy"))
                .andExpect(jsonPath("$.pin").doesNotExist());
        mvc.perform(post("/api/v1/staff").with(as(MANAGER_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wendy\",\"displayName\":\"W2\",\"role\":\"WAITER\",\"pin\":\"2222\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_EXISTS"));
        mvc.perform(post("/api/v1/staff").with(as(MANAGER_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x1\",\"displayName\":\"X\",\"role\":\"WAITER\",\"pin\":\"12\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/staff/" + boss.id()).with(as(MANAGER_KEY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Boss\",\"role\":\"MANAGER\",\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_STAFF_MANAGER"));
        mvc.perform(get("/api/v1/staff").with(as(POS_KEY))).andExpect(status().isForbidden());
    }
}
