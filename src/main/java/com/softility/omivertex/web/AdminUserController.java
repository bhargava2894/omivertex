package com.softility.omivertex.web;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.domain.Role;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.web.dto.AccessRequestResponse;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/access-requests")
public class AdminUserController {

    private final AppUserRepository userRepository;

    public AdminUserController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<AccessRequestResponse> listRequests() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AccessRequestResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    public AccessRequestResponse approveRequest(@PathVariable Long id,
                                                @RequestBody(required = false) ApproveRequest body) {
        AppUser user = find(id);
        user.setStatus(AccessStatus.APPROVED);
        // The role is what the approving admin grants; default to read-only Viewer
        // when the caller doesn't specify one (backward-compatible with a bare POST).
        user.setRole(body != null && body.role() != null ? body.role() : Role.VIEWER);
        return AccessRequestResponse.from(userRepository.save(user));
    }

    @PostMapping("/{id}/reject")
    public AccessRequestResponse rejectRequest(@PathVariable Long id) {
        AppUser user = find(id);
        user.setStatus(AccessStatus.REJECTED);
        return AccessRequestResponse.from(userRepository.save(user));
    }

    private AppUser find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AccessRequest", id));
    }

    /** Optional approval payload: the role to grant the approved user. */
    public record ApproveRequest(Role role) {
    }
}
