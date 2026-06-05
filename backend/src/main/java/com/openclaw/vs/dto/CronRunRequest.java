package com.openclaw.vs.dto;

import lombok.Data;

@Data
public class CronRunRequest {
    /** force | due */
    private String mode;
}
