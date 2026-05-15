package ch.exmachina.cosmo42.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusDTO {
    String jobUuid;
    String status;
    int progressPercent;
    String documentUuid;
    String errorMessage;
}
