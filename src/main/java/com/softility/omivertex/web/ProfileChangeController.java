package com.softility.omivertex.web;

import com.softility.omivertex.domain.ProfileChangeStatus;
import com.softility.omivertex.service.ProfileChangeService;
import com.softility.omivertex.web.dto.ProfileChangeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin approval queue for associate-proposed profile changes. */
@RestController
@RequestMapping("/api/v1/profile-changes")
public class ProfileChangeController {

    private final ProfileChangeService service;

    public ProfileChangeController(ProfileChangeService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProfileChangeResponse> list(@RequestParam(required = false) ProfileChangeStatus status) {
        return service.list(status);
    }

    @PostMapping("/{id}/approve")
    public ProfileChangeResponse approve(@PathVariable Long id, Authentication auth) {
        return service.approve(id, auth.getName());
    }

    @PostMapping("/{id}/reject")
    public ProfileChangeResponse reject(@PathVariable Long id, Authentication auth,
                                        @RequestBody(required = false) RejectBody body) {
        return service.reject(id, body == null ? null : body.note(), auth.getName());
    }

    /** Optional rejection payload: a note shown back to the associate. */
    public record RejectBody(String note) {
    }
}
