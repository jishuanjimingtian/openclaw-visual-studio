package com.openclaw.vs.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OpenClaw 运行时 Node 版本要求（与 npm openclaw engines 对齐）。 */
public final class NodeVersionSupport {

    public static final String MIN_VERSION = "22.19.0";
    /** nvm / fnm 安装时使用的目标主版本（OpenClaw 推荐 Node 24）。 */
    public static final String NVM_INSTALL_TARGET = "24";

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private NodeVersionSupport() {}

    public static int[] parseVersionParts(String versionOutput) {
        if (versionOutput == null || versionOutput.isBlank()) {
            return new int[] {0, 0, 0};
        }
        String version = versionOutput.trim();
        if (version.startsWith("v")) {
            version = version.substring(1);
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) {
            return new int[] {0, 0, 0};
        }
        return new int[] {
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        };
    }

    public static int compare(String left, String right) {
        int[] a = parseVersionParts(left);
        int[] b = parseVersionParts(right);
        for (int i = 0; i < 3; i++) {
            int diff = a[i] - b[i];
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    public static boolean isAcceptable(String versionOutput) {
        return compare(versionOutput, MIN_VERSION) >= 0;
    }

    /** 从 nvm list 输出中提取已安装版本号。 */
    public static List<String> parseNvmListVersions(String listOutput) {
        List<String> versions = new ArrayList<>();
        if (listOutput == null || listOutput.isBlank()) {
            return versions;
        }
        Matcher matcher = VERSION_PATTERN.matcher(listOutput);
        while (matcher.find()) {
            String version = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
            if (!versions.contains(version)) {
                versions.add(version);
            }
        }
        versions.sort(NodeVersionSupport::compare);
        return versions;
    }

    public static String findBestAcceptable(List<String> versions) {
        String best = null;
        for (String version : versions) {
            if (isAcceptable(version)) {
                best = version;
            }
        }
        return best;
    }

    public static String requirementLabel() {
        return ">=" + MIN_VERSION;
    }
}
