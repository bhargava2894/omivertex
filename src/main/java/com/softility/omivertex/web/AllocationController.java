package com.softility.omivertex.web;

import com.softility.omivertex.service.AllocationService;
import com.softility.omivertex.web.dto.AllocationRequest;
import com.softility.omivertex.web.dto.AllocationResponse;
import com.softility.omivertex.web.dto.AllocationUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping
    public List<AllocationResponse> list(@RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) Long associateId,
                                         @RequestParam(required = false) Boolean active) {
        return allocationService.list(projectId, associateId, active);
    }

    @GetMapping("/{id}")
    public AllocationResponse get(@PathVariable Long id) {
        return allocationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationResponse create(@Valid @RequestBody AllocationRequest request) {
        return allocationService.create(request);
    }

    @PutMapping("/{id}")
    public AllocationResponse update(@PathVariable Long id, @Valid @RequestBody AllocationUpdateRequest request) {
        return allocationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        allocationService.delete(id);
    }
}
