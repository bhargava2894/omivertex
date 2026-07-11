package com.softility.omivertex.web;

import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.service.AssociateService;
import com.softility.omivertex.service.ProfileChangeService;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.dto.ProfileChangeResponse;
import com.softility.omivertex.web.dto.SkillAssignmentRequest;
import com.softility.omivertex.web.error.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** The ASSOCIATE-role self-service surface: own profile and change proposals. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AppUserRepository appUsers;
    private final AssociateService associateService;
    private final ProfileChangeService profileChangeService;

    public MeController(AppUserRepository appUsers, AssociateService associateService,
                        ProfileChangeService profileChangeService) {
        this.appUsers = appUsers;
        this.associateService = associateService;
        this.profileChangeService = profileChangeService;
    }

    @GetMapping("/profile")
    public AssociateResponse myProfile(Authentication auth) {
        return associateService.get(linkedAssociateId(auth));
    }

    @PostMapping("/profile-changes/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileChangeResponse proposeSkills(Authentication auth,
                                               @Valid @RequestBody SkillAssignmentRequest request) {
        return profileChangeService.submitSkills(linkedAssociateId(auth), request);
    }

    @PostMapping("/profile-changes/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileChangeResponse proposeResume(Authentication auth,
                                               @RequestParam("file") MultipartFile file) {
        return profileChangeService.submitResume(linkedAssociateId(auth), file);
    }

    @GetMapping("/profile-changes")
    public List<ProfileChangeResponse> myChanges(Authentication auth) {
        return profileChangeService.listForAssociate(linkedAssociateId(auth));
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
