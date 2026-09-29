package com.donquaan.dfast.core;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JvmFlags {
    private static final Pattern SIZE_FLAG = Pattern.compile("-X(mx|ms|ss|mn)\\d+[kKmMgGtT]?");
    private static final Pattern BOOLEAN_FLAG = Pattern.compile("-XX:[+-]\\w+");
    private static final Pattern NUMERIC_FLAG = Pattern.compile("-XX:\\w+=\\d+(\\.\\d+)?[kKmMgGtT%]?");

    private JvmFlags() {
    }

    public static String sanitize(List<String> arguments) {
        return arguments.stream()
                .filter(arg -> SIZE_FLAG.matcher(arg).matches()
                        || BOOLEAN_FLAG.matcher(arg).matches()
                        || NUMERIC_FLAG.matcher(arg).matches())
                .collect(Collectors.joining(" "));
    }
}
