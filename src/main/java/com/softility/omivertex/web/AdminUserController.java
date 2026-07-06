package com.softility.omivertex.web;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
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
    public AccessRequestResponse approveRequest(@PathVariable Long id) {
        return AccessRequestResponse.from(setStatus(id, AccessStatus.APPROVED));
    }

    @PostMapping("/{id}/reject")
    public AccessRequestResponse rejectRequest(@PathVariable Long id) {
        return AccessRequestResponse.from(setStatus(id, AccessStatus.REJECTED));
    }

    private AppUser setStatus(Long id, AccessStatus status) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AccessRequest", id));
        user.setStatus(status);
        return userRepository.save(user);
    }
}
