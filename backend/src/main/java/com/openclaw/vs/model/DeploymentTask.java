package com.openclaw.vs.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "deployment_tasks")
@NoArgsConstructor @AllArgsConstructor @Builder
public class DeploymentTask {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "状态不能为空")
    @Size(max = 20, message = "状态长度不能超过 20")
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @PositiveOrZero(message = "进度不能为负数")
    @Builder.Default
    private int progress = 0;

    @Column(name = "start_time", nullable = false)
    @Builder.Default
    private LocalDateTime startTime = LocalDateTime.now();

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Lob
    private String log;

    @Lob
    @Column(name = "config_json")
    private String configJson;
}