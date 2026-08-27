package com.warehouse.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The driver and vehicle directories are read by warehouse staff, not admins — they are the ones
 * filling in transfers. The controllers say so with {@code @PreAuthorize}, but the HTTP rules in
 * {@link SecurityConfig} are what actually decide, and when these endpoints were added they fell
 * through to the admin-only catch-all. The failure was invisible: the type-ahead saw a 403, showed
 * an empty list, and looked exactly like a directory with nothing in it.
 *
 * <p>The same reasoning covers the waybill type-ahead on the stock entry screens: warehouse staff
 * are the ones entering deliveries, so it has to answer for them too.</p>
 *
 * <p>These tests pin the intended split so it cannot drift back. The URL rule covers the whole
 * {@code /vehicles/**} subtree, so exercising {@code /suggest} also covers {@code /by-driver} —
 * which cannot be called here anyway, since its join table is created by Flyway and the test
 * schema comes from Hibernate.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DirectoryAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "STOCK_OUT")
    void warehouseRoleCanSearchTheDriverDirectory() throws Exception {
        mockMvc.perform(get("/api/admin/drivers/suggest").param("q", "ali")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_IN")
    void warehouseRoleCanSearchTheVehicleDirectory() throws Exception {
        mockMvc.perform(get("/api/admin/vehicles/suggest").param("q", "34")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STOCK_IN")
    void warehouseRoleCanLookUpPreviousWaybills() throws Exception {
        mockMvc.perform(get("/api/admin/stocks/irsaliye/suggest").param("q", "ABC")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /** Reading is opened up; removing a driver is not. */
    @Test
    @WithMockUser(roles = "STOCK_OUT")
    void warehouseRoleStillCannotDeleteADriver() throws Exception {
        mockMvc.perform(delete("/api/admin/drivers/1"))
                .andExpect(status().isForbidden());
    }

    /** The duplicate-merge screen is a cleanup tool and stays with admins. */
    @Test
    @WithMockUser(roles = "STOCK_IN")
    void warehouseRoleStillCannotSeeDuplicateGroups() throws Exception {
        mockMvc.perform(get("/api/admin/drivers/duplicates").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
