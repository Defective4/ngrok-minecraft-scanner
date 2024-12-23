package io.github.defective4.minecraft.ngrokscanner;

import java.io.File;

public enum CommandLineOption {
    DONT_RESOLVE("d", "Don't resolve ngrok hostnames"),
    FORCE_JOIN(
            "J",
            "Try detecting servers with disabled listings.\nMakes the scan take more time.\nMay result in false positives."),

    FORCE_LEGACY_PING("L", "Force ONLY legacy server list ping."),
    HELP("h", "Display help"),
    JSON("j", "Output data in JSON format."),
    LEGACY_PING(
            "l",
            "Fall back to legacy server list ping, in case the first attempt fails.\n"
                    + "Makes the scan take more time."),

    NON_INTERACTIVE("y", "Assume \"yes\" to all questions."),
    OUTPUT(
            "f",
            "file",
            "Target while, where discovered servers will be saved.\nThis option won't suppress standard output",
            in -> {
                File f = new File(in);
                if (f.isDirectory()) return "File " + in + " is a directory!";
                return null;
            }),
    REGION(
            "r",
            "region",
            "ngrok region.\n" + "EU - Europe," + "\nUS - United States (default)",
            in -> switch (in.toLowerCase()) {
                case "eu", "us" -> null;
                default -> "Unknown region \"" + in + "\"";
            }),
    SKIP_EMPTY("e", "Don't print servers with no online players."),
    THREADS("t", "threads", "Number of threads to use for scanning.\n" + "Default: 2", in -> {
        try {
            int n = Integer.parseInt(in);
            if (n <= 0) return "Number of threads must be greater than 0";
            return null;
        } catch (Exception e) {
            return "Invalid number of threads: " + in;
        }
    }),
    TIMEOUT(
            "o",
            "timeout ms",
            "How many milliseconds should a connection take before timing out.\nDefault: 1000",
            in -> {
                try {
                    Long.parseLong(in);
                    return null;
                } catch (Exception e) {
                    return "Invalid number: " + in;
                }
            }),
    VERBOSE("v", "Be more verbose");

    final String argument;
    final String description;
    final String key;
    final CommandLineOptionParser parser;

    private CommandLineOption(String key, String description) {
        this(key, null, description, null);
    }

    private CommandLineOption(String key, String argument, String description, CommandLineOptionParser parser) {
        this.key = key;
        this.argument = argument;
        this.description = description;
        this.parser = parser;
    }

    public static CommandLineOption getByKey(String key) {
        for (CommandLineOption op : values()) if (op.key.equals(key)) return op;
        return null;
    }

}