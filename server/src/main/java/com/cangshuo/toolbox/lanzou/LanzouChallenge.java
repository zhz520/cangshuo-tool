package com.cangshuo.toolbox.lanzou;

import java.util.*;
import java.util.regex.*;

/** Deterministic public download handshake; no IP spoofing and no interactive captcha handling. */
final class LanzouChallenge {
    private static final Pattern ARG1 = Pattern.compile("var\\s+arg1\\s*=\\s*['\"]([0-9a-f]{40})['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final List<Integer> POSITIONS = List.of(
            15,35,29,24,33,16,1,38,10,9,19,31,40,27,22,23,25,13,6,11,
            39,18,20,8,14,21,32,26,2,30,7,4,17,5,3,28,34,37,12,36);
    private static final String MASK = "3000176000856006061501533003690027800375";

    private LanzouChallenge() {}
    static boolean isChallenge(String body) {
        return body != null && (body.contains("acw_sc__v2") || ARG1.matcher(body).find());
    }
    static String cookie(String body) {
        Matcher matcher = ARG1.matcher(body == null ? "" : body);
        return matcher.find() ? solve(matcher.group(1)) : "";
    }
    static String solve(String arg1) {
        if (arg1 == null || !arg1.matches("(?i)[0-9a-f]{40}")) return "";
        char[] shuffled = new char[40];
        for (int source = 0; source < arg1.length(); source++)
            for (int target = 0; target < POSITIONS.size(); target++)
                if (POSITIONS.get(target) == source + 1) shuffled[target] = arg1.charAt(source);
        StringBuilder result = new StringBuilder(40);
        for (int i = 0; i < MASK.length(); i += 2) {
            int left = Integer.parseInt(new String(shuffled, i, 2), 16);
            int right = Integer.parseInt(MASK.substring(i, i + 2), 16);
            result.append(String.format(Locale.ROOT, "%02x", left ^ right));
        }
        return result.toString();
    }
    static String addCookie(String current, String value) {
        if (value == null || !value.matches("[0-9a-f]{40}")) return current == null ? "" : current;
        String clean = current == null ? "" : current;
        if (clean.matches("(?s).*\\bacw_sc__v2=[^;]*.*"))
            return clean.replaceAll("acw_sc__v2=[^;]*", "acw_sc__v2=" + value);
        return clean.isBlank() ? "acw_sc__v2=" + value : clean + "; acw_sc__v2=" + value;
    }
}
