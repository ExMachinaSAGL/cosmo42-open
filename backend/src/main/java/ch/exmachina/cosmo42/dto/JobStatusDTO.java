package ch.exmachina.cosmo42.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusDTO {
    String jobUuid;
    String status;
    int progressPercent;
    String documentUuid;
    String errorMessage;
}
