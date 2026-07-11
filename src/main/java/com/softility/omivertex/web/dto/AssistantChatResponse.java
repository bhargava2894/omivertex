package com.softility.omivertex.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/** proposedAction is omitted (not null) when the turn is a plain answer. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantChatResponse(String reply, ProposedAction proposedAction) {

    public enum ActionType { CREATE_ALLOCATION, FILL_POSITION }

    /** A draft the user must confirm in the UI; confirming calls the existing endpoints. */
    public record ProposedAction(ActionType type,
                                 Long associateId, String associateName,
                                 Long projectId, String projectName,
                                 Long positionId, String positionTitle,
                                 Integer percent, Boolean billable,
                                 LocalDate startDate, LocalDate endDate,
                                 String summary, List<String> warnings) {
    }
}
