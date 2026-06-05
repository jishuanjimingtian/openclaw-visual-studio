package com.openclaw.vs.service.market;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClawHubMarketClientTest {

    @Test
    void mapSort_mapsFrontendValues() {
        assertThat(ClawHubMarketClient.mapSort("hot")).isEqualTo("trending");
        assertThat(ClawHubMarketClient.mapSort("rating")).isEqualTo("stars");
        assertThat(ClawHubMarketClient.mapSort("newest")).isEqualTo("createdAt");
    }

    @Test
    void starsToRating_capsAtFive() {
        assertThat(ClawHubMarketClient.starsToRating(0)).isEqualTo(3.0);
        assertThat(ClawHubMarketClient.starsToRating(10000)).isLessThanOrEqualTo(5.0);
    }
}
