package com.softility.omivertex.web;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.repository.AppUserRepository;
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
    public List<AppUser> listRequests() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/{id}/approve")
    public AppUser approveRequest(@PathVariable Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AccessRequest", id));
        user.setStatus(AccessStatus.APPROVED);
        return userRepository.save(user);
    }

    @PostMapping("/{id}/reject")
    public AppUser rejectRequest(@PathVariable Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AccessRequest", id));
        user.setStatus(AccessStatus.REJECTED);
        return userRepository.save(user);
    }
}
