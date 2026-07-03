package com.softility.omivertex.web;

import com.softility.omivertex.domain.PositionStatus;
import com.softility.omivertex.service.PositionService;
import com.softility.omivertex.web.dto.FillPositionRequest;
import com.softility.omivertex.web.dto.MatchCandidateResponse;
import com.softility.omivertex.web.dto.PositionRequest;
import com.softility.omivertex.web.dto.PositionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    public List<PositionResponse> list(@RequestParam(required = false) PositionStatus status,
                                       @RequestParam(required = false) Long projectId) {
        return positionService.list(status, projectId);
    }

    @GetMapping("/{id}")
    public PositionResponse get(@PathVariable Long id) {
        return positionService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse create(@Valid @RequestBody PositionRequest request) {
        return positionService.create(request);
    }

    @PutMapping("/{id}")
    public PositionResponse update(@PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        return positionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        positionService.delete(id);
    }

    @GetMapping("/{id}/matches")
    public List<MatchCandidateResponse> matches(@PathVariable Long id) {
        return positionService.matches(id);
    }

    @PostMapping("/{id}/fill")
    public PositionResponse fill(@PathVariable Long id, @Valid @RequestBody FillPositionRequest request) {
        return positionService.fill(id, request);
    }
}
