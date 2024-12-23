package io.github.defective4.minecraft.ngrokscanner;

import static io.github.defective4.minecraft.ngrokscanner.CommandLineOption.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.defective4.minecraft.ngrokscanner.NgrokScanner.AddressResolverCallback;
import io.github.defective4.minecraft.ngrokscanner.NgrokScanner.HostPortPair;
import io.github.defective4.minecraft.ngrokscanner.NgrokScanner.ScannerCallback;

public class ScannerMain {

    private static int progress = 0;

    private static boolean verbose = false;

    private ScannerMain() {}

    public static boolean askYN(String prompt) {
        System.err.print(prompt + " [y/N]: ");
        try {
            return new InputStreamReader(System.in).read() == 'y';
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        Map<CommandLineOption, String> options = parseOptions(args);
        if (options == null) return;
        if (options.containsKey(CommandLineOption.HELP)) {
            printHelp();
        } else {
            run(options);
        }
    }

    private static Map<CommandLineOption, String> parseOptions(String[] args) {
        Map<CommandLineOption, String> map = new HashMap<>();

        for (String arg : args) if (arg.startsWith("-")) {
            boolean sh = !arg.startsWith("--");
            String lk = arg.substring(arg.startsWith("--") ? 2 : 1);
            String opArgs = null;
            int eqIndex = lk.indexOf('=');
            if (eqIndex != -1) {
                opArgs = lk.substring(eqIndex + 1);
                lk = lk.substring(0, eqIndex);
            }
            String[] keys = sh ? lk.split("") : new String[] {
                    lk
            };
            for (String key : keys) {
                CommandLineOption option = CommandLineOption.getByKey(key);
                if (option == null) {
                    printHelp("Unknown option \"" + key + "\"");
                    return null;
                }

                if (option.argument != null) {
                    if (opArgs == null) {
                        printHelp("Missing arguments for option \"" + key + "\"");
                        return null;
                    }
                    String parserResult = option.parser.parse(opArgs);
                    if (parserResult != null) {
                        printHelp(parserResult);
                        return null;
                    }
                } else if (option.argument == null && opArgs != null) {
                    printHelp("Option \"" + key + "\" does not accept any arguments");
                    return null;
                }

                map.put(option, opArgs);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    private static void printHelp() {
        printHelp(null);
    }

    private static void printHelp(String error) {
        String file = new File(ScannerMain.class.getProtectionDomain().getCodeSource().getLocation().getFile())
                .getName();
        System.err.println("Usage: " + (file.endsWith(".jar") ? "java -jar " : "") + file + " [options...]");
        System.err.println();
        System.err.println("A scanner utility for Minecraft servers shared over ngrok.");
        System.err.println();
        List<StringBuilder> lines = new ArrayList<>();
        CommandLineOption[] ops = CommandLineOption.values();
        for (CommandLineOption option : ops) {
            StringBuilder lineBuilder = new StringBuilder();
            String key = option.key;
            lineBuilder.append(" ").append(key.length() == 1 ? "-" : "--").append(key);
            if (option.argument != null) {
                lineBuilder.append("=<").append(option.argument).append(">");
            }
            lines.add(lineBuilder);
        }
        int longest = lines.stream().mapToInt(StringBuilder::length).max().getAsInt() + 2;

        for (int i = 0; i < ops.length; i++) {
            StringBuilder line = lines.get(i);
            for (int j = line.length(); j < longest; j++) line.append(" ");
            line.append(ops[i].description);
            int index = 0;
            for (String subline : line.toString().split("\n")) {
                subline = subline.replace("\r", "");
                if (index++ > 0) {
                    for (int j = 0; j < longest + 2; j++) System.err.print(" ");
                }
                System.err.println(subline);
            }
        }
        if (error != null) {
            System.err.println();
            System.err.println(error);
        }
    }

    private synchronized static void progress(int max, String addr, int port) {
        progress++;
        if (progress % 100 == 0) {
            System.err.println(String.format("Scanning... (%s/%s)", progress, max));
        }
        if (verbose) System.err.println("Scanned " + addr + ":" + port);
    }

    private static void run(Map<CommandLineOption, String> options) {

        String region = "us";
        int threads = 2;
        long timeout = 1000;
        int pvn = 754;

        if (options.containsKey(REGION)) region = options.get(REGION);
        if (options.containsKey(THREADS)) threads = Integer.parseInt(options.get(THREADS));
        if (options.containsKey(TIMEOUT)) timeout = Long.parseLong(options.get(TIMEOUT));
        if (options.containsKey(PROTOCOL)) pvn = Integer.parseInt(options.get(PROTOCOL));

        System.err.println();
        System.err.println(" ngrok region: " + region);
        System.err.println(" No. of threads: " + threads);
        System.err
                .println(" Legacy ping: " + (options.containsKey(FORCE_LEGACY_PING) ? "Force"
                        : options.containsKey(LEGACY_PING) ? "Fallback" : "No"));
        System.err.println(" JSON output: " + options.containsKey(JSON));
        System.err.println(" Ignore empty servers: " + options.containsKey(SKIP_EMPTY));
        System.err.println(" Timeout (ms): " + timeout + "ms");
        System.err.println(" Discover unlisted: " + options.containsKey(FORCE_JOIN));
        System.err.println(" Skip ngrok IP resolve: " + options.containsKey(DONT_RESOLVE));
        if (options.containsKey(OUTPUT)) System.err.println(" Output file: " + options.get(OUTPUT));
        if (options.containsKey(PROTOCOL)) System.err.println(" Protocol version number: " + options.get(PROTOCOL));

        System.err.println();
        if (!options.containsKey(NON_INTERACTIVE) && !askYN("Are these settings correct?")) {
            System.err.println("Aborted.");
            System.exit(1);
            return;
        }

        Map<String, String> map = new HashMap<>();
        if (!options.containsKey(DONT_RESOLVE)) {
            System.err.println("Resolving IP addresses...");
            AtomicBoolean failed = new AtomicBoolean(false);
            map.putAll(NgrokScanner.resolveAllNgrokHostnames(region, new AddressResolverCallback() {

                @Override
                public void resolved(String host, boolean success, String address) {
                    if (success) {
                        System.err.println("Address resolved for " + host + ": [" + address + "]");
                    } else {
                        failed.set(true);
                        System.err.println("Couldn't resolve address for " + host);
                    }
                }

                @Override
                public void resolving(String host) {
                    System.err.println("Resolving address for " + host + "...");
                }
            }));
            if (!map.isEmpty() && failed.get() && !options.containsKey(NON_INTERACTIVE)
                    && !askYN("Failed to resolve addresses for some hostnames.\nDo you want to continue?")) {
                System.err.println("Aborted.");
                System.exit(1);
                return;
            }
        } else {
            System.err.println("Skipped resolving IP addresses");
            if (region == null || "us".equalsIgnoreCase(region)) region = ".";
            else region = "." + region + ".";
            for (int x = 0; x < 10; x++) {
                String host = x + ".tcp" + region + "ngrok.io";
                map.put(host, host);
            }
        }

        if (map.isEmpty()) {
            System.err.println("Couldn't resolve any of ngrok's addresses. Aborting.");
            System.exit(1);
            return;
        }

        PrintWriter fileWriter = null;

        if (options.containsKey(OUTPUT)) {
            File targetFile = new File(options.get(OUTPUT));
            if (!options.containsKey(NON_INTERACTIVE) && targetFile.exists()
                    && !askYN("File " + targetFile + " already exists.\nDo you want to overwrite it?")) {
                System.err.println("Aborted.");
                System.exit(1);
                return;
            }
            try {
                fileWriter = new PrintWriter(targetFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.err.println("Aborted.");
                System.exit(1);
                return;
            }
        }

        PrintWriter finalPrintWriter = fileWriter;

        List<HostPortPair> targetAddrs = NgrokScanner.generateTargetAddresses(map.values());
        List<List<HostPortPair>> split = NgrokScanner.split(targetAddrs, threads);
        int size = targetAddrs.size();
        System.err.println("We have " + size + " addresses to scan (~" + split.get(0).size() + " per thread).");
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        verbose = options.containsKey(VERBOSE);

        NgrokScanner.scan(split, new ScannerCallback() {

            @Override
            public void discovered(PingResponse data, String host, int port) {
                if (options.containsKey(SKIP_EMPTY) && !data.isUnlisted() && data.getPlayers() == 0) return;
                String txt = options.containsKey(JSON) ? data.toJson(host, port)
                        : String
                                .format("""
                                        --- Discovered a Minecraft server
                                        Address: %s
                                        Version: %s
                                        Protocol: %s
                                        Players: %s/%s
                                        %s%s
                                        %s
                                        ---""", host + ":" + port, data.getVersion(), data.getProtocol(),
                                        data.getPlayers(), data.getMaxPlayers(), data.isLegacy() ? "LEGACY\n" : "",
                                        data.isUnlisted() ? "UNLISTED\n" : "",
                                        data.getDescription() == null ? "No description"
                                                : data.getDescription().toPlainString());
                System.out.println(txt);
                if (finalPrintWriter != null) {
                    finalPrintWriter.println(txt);
                    finalPrintWriter.flush();
                }
            }

            @Override
            public void scanned(String host, int port) {
                progress(size, host, port);
            }
        }, options.containsKey(FORCE_LEGACY_PING) ? 2 : options.containsKey(LEGACY_PING) ? 1 : 0,
                options.containsKey(FORCE_JOIN), (int) timeout, pvn);

        System.err.println("Scan started!");
    }
}
