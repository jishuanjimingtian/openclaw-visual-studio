package com.openclaw.vs.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ModelMarketOverviewDto {
    private List<ModelMarketCategoryDto> categories;
    private List<OpenClawCatalogModelDto> models;
}
