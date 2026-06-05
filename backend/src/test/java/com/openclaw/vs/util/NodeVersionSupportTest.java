package com.openclaw.vs.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeVersionSupportTest {

    @Test
    void isAcceptable_rejectsNode14() {
        assertThat(NodeVersionSupport.isAcceptable("v14.18.0")).isFalse();
    }

    @Test
    void isAcceptable_acceptsNode22_19() {
        assertThat(NodeVersionSupport.isAcceptable("v22.19.0")).isTrue();
    }

    @Test
    void isAcceptable_acceptsNode24() {
        assertThat(NodeVersionSupport.isAcceptable("v24.11.0")).isTrue();
    }

    @Test
    void parseNvmListVersions_extractsAndSorts() {
        String output = """
              * 14.18.0 (Currently using 64-bit executable)
                22.19.0
                24.11.0
            """;
        List<String> versions = NodeVersionSupport.parseNvmListVersions(output);
        assertThat(versions).containsExactly("14.18.0", "22.19.0", "24.11.0");
        assertThat(NodeVersionSupport.findBestAcceptable(versions)).isEqualTo("24.11.0");
    }
}
