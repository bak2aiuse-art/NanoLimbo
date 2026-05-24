/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static Path hy2ScriptPath = Paths.get("./hy2mgr_v2.sh");

    private static final String[] CONFIG_VARS = {
        "FILE_PATH", "HY2_PORT", "HY2_SCRIPT_PATH"
    };

    public static void main(String[] args) {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(NanoLimbo::stopServices));

        try {
            initHy2Service();
            System.out.println(ANSI_GREEN + "Hy2 service is running!\n" + ANSI_RESET);
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing Hy2 service: " + e.getMessage() + ANSI_RESET);
        }

        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }

    private static void initHy2Service() throws Exception {
        Map<String, String> config = loadConfig();
        hy2ScriptPath = Paths.get(config.get("HY2_SCRIPT_PATH"));
        if (!Files.exists(hy2ScriptPath)) {
            throw new FileNotFoundException("Hy2 manager script not found: " + hy2ScriptPath);
        }

        String port = config.get("HY2_PORT");
        runCommand(Arrays.asList("bash", hy2ScriptPath.toString(), "install", port), true);

        String subscription = runCommand(Arrays.asList("bash", hy2ScriptPath.toString(), "sub"), false).trim();
        if (subscription.isEmpty()) {
            throw new IOException("Hy2 subscription is empty");
        }

        Path subFile = Paths.get(config.get("FILE_PATH")).resolve("sub.txt");
        Path parent = subFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(
            subFile,
            Collections.singletonList(subscription),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        System.out.println(ANSI_GREEN + "Subscription written to " + subFile + ANSI_RESET);
    }

    private static Map<String, String> loadConfig() throws IOException {
        Map<String, String> config = new HashMap<>();
        config.put("FILE_PATH", "./world");
        config.put("HY2_PORT", "25565");
        config.put("HY2_SCRIPT_PATH", "./hy2mgr_v2.sh");

        for (String var : CONFIG_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                config.put(var, value);
            }
        }

        Path envFile = Paths.get(".env");
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(CONFIG_VARS));
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    if (allowedKeys.contains(key)) {
                        config.put(key, value);
                    }
                }
            }
        }

        return config;
    }

    private static String runCommand(List<String> command, boolean inheritOutput) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (inheritOutput) {
            pb.inheritIO();
        }

        Process process = pb.start();
        String output = "";
        if (!inheritOutput) {
            try (InputStream in = process.getInputStream()) {
                output = new String(readAllBytes(in), StandardCharsets.UTF_8);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(String.join(" ", command) + " exited with code " + exitCode);
        }
        return output;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return out.toByteArray();
    }

    private static void stopServices() {
        try {
            if (Files.exists(hy2ScriptPath)) {
                runCommand(Arrays.asList("bash", hy2ScriptPath.toString(), "stop"), true);
                System.out.println(ANSI_RED + "Hy2 service terminated" + ANSI_RESET);
            }
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error stopping Hy2 service: " + e.getMessage() + ANSI_RESET);
        }
    }
}
