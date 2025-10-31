package com.warehouse.controller;

import com.warehouse.entity.Notification;
import com.warehouse.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
    }

    @Test
    void shouldListUnreadAndMarkAsRead() throws Exception {
        Notification n1 = new Notification();
        n1.setTitle("Test 1");
        n1.setMessage("Message 1");
        notificationRepository.save(n1);

        Notification n2 = new Notification();
        n2.setTitle("Test 2");
        n2.setMessage("Message 2");
        notificationRepository.save(n2);

        mockMvc.perform(get("/api/notifications/unread")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // mark first as read
        mockMvc.perform(post("/api/notifications/" + n1.getId() + "/read"))
                .andExpect(status().isNoContent());

        Notification reloaded = notificationRepository.findById(n1.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isTrue();

        mockMvc.perform(get("/api/notifications/recent")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}


