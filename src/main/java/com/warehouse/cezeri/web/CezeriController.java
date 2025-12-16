package com.warehouse.cezeri.web;

import com.warehouse.cezeri.context.CezeriContext;
import com.warehouse.cezeri.context.CezeriContextHolder;
import com.warehouse.cezeri.service.CezeriChatService;
import com.warehouse.util.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController
@RequestMapping("/api/cezeri")
@CrossOrigin(origins = "*")
@Profile("!test")
public class CezeriController {

    private final CezeriChatService chatService;

    public CezeriController(CezeriChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<CezeriChatResponse> chat(@RequestBody(required = false) CezeriChatRequest request) {
        String username = CurrentUser.usernameOrSystem();
        String role = CurrentUser.getRole();
        boolean allowMutations = request != null && request.allowMutations;

        CezeriContextHolder.set(new CezeriContext(username, role, allowMutations));
        try {
            return ResponseEntity.ok(chatService.chat(username, role, request));
        } finally {
            CezeriContextHolder.clear();
        }
    }
}


