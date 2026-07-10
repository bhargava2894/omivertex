package com.softility.omivertex.web;

import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.service.AssociateService;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The ASSOCIATE-role self-service surface: own profile and change proposals. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AppUserRepository appUsers;
    private final AssociateService associateService;

    public MeController(AppUserRepository appUsers, AssociateService associateService) {
        this.appUsers = appUsers;
        this.associateService = associateService;
    }

    @GetMapping("/profile")
    public AssociateResponse myProfile(Authentication auth) {
        return associateService.get(linkedAssociateId(auth));
    }

    Long linkedAssociateId(Authentication auth) {
        AppUser user = appUsers.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new NotFoundException("User " + auth.getName(), 0L));
        if (user.getAssociateId() == null) {
            throw new NotFoundException("Linked associate for " + auth.getName(), 0L);
        }
        return user.getAssociateId();
    }
}
