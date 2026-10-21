package com.fleet.storefront.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.storefront.application.port.in.ManageStaffUseCase;
import com.fleet.storefront.application.port.in.ManageStaffUseCase.CreateStaffCommand;
import com.fleet.storefront.domain.staff.StaffRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Production mode: UI devices need a signed-in person for everything but signing in. */
@TestPropertySource(properties = "storefront.security.require-staff-session=true")
class StaffSessionRequiredIT extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    ManageStaffUseCase staff;

    @Autowired
    ObjectMapper json;

    @Test
    void uiDevicesNeedASignedInPerson_machineClientsDoNot() throws Exception {
        staff.create(new CreateStaffCommand("carl", "Carl", StaffRole.CASHIER, "1111"));

        mvc.perform(get("/api/v1/catalog").with(as(POS_KEY)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STAFF_SESSION_REQUIRED"));
        mvc.perform(get("/api/v1/me").with(as(POS_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffSignInRequired").value(true));
        mvc.perform(get("/api/v1/staff/roster").with(as(POS_KEY))).andExpect(status().isOk());

        String body = mvc.perform(post("/api/v1/staff-sessions").with(as(POS_KEY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"carl\",\"pin\":\"1111\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();

        mvc.perform(get("/api/v1/catalog").with(as(POS_KEY)).header("X-Staff-Session", token))
                .andExpect(status().isOk());
        // The HQ sync client is a machine: its key alone is enough.
        mvc.perform(put("/api/v1/sync/catalog").with(as(HQ_KEY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"products\":[]}"))
                .andExpect(status().isOk());
        // Health stays public for the orchestrator.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
