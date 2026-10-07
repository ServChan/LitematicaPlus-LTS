package org.lts.litematicaplus.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class PurpurServer implements AutoCloseable {
    private static final long STARTUP_SECONDS = 420;

    private final Process process;
    private final Path directory;
    private final int port;
    private final Thread reader;
    private final Thread killOnExit;
    private final List<String> output = new ArrayList<>();
    private final CountDownLatch ready = new CountDownLatch(1);

    private PurpurServer(Process process, Path directory, int port) {
        this.process = process;
        this.directory = directory;
        this.port = port;
        this.killOnExit = new Thread(() -> kill(process));
        Runtime.getRuntime().addShutdownHook(this.killOnExit);
        this.reader = new Thread(this::pump, "purpur-output");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    static PurpurServer start(Path directory, boolean fresh) throws IOException, InterruptedException {
        if (!Files.isRegularFile(directory.resolve("purpur.jar"))) {
            throw new IllegalStateException("Purpur is not prepared; run runClientGameTest -PwithSyncmatica");
        }
        if (fresh) {
            for (String name : List.of("world", "world_nether", "world_the_end", "plugins/SyncmaticaPaper")) {
                delete(directory.resolve(name));
            }
        }
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String java = ProcessHandle.current().info().command().orElse("java");
        Process process = new ProcessBuilder(java, "-Xmx1G", "-jar", "purpur.jar", "--nogui", "--port",
                Integer.toString(port)).directory(directory.toFile()).redirectErrorStream(true).start();
        PurpurServer server = new PurpurServer(process, directory, port);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_SECONDS);
        while (!server.ready.await(1, TimeUnit.SECONDS)) {
            if (!process.isAlive() || System.nanoTime() > deadline) {
                server.close();
                throw new IllegalStateException("Purpur did not start:\n" + server.tail(60));
            }
        }
        return server;
    }

    int port() {
        return this.port;
    }

    Path pluginData() {
        return this.directory.resolve("plugins/SyncmaticaPaper");
    }

    void command(String command) throws IOException {
        OutputStream stdin = this.process.getOutputStream();
        stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    List<String> lines(Predicate<String> filter) {
        synchronized (this.output) {
            return this.output.stream().filter(filter).toList();
        }
    }

    String tail(int count) {
        synchronized (this.output) {
            return String.join("\n", this.output.subList(Math.max(0, this.output.size() - count), this.output.size()));
        }
    }

    private void pump() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(this.process.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[purpur] " + line);
                synchronized (this.output) {
                    this.output.add(line);
                }
                if (line.contains("Done (")) {
                    this.ready.countDown();
                }
            }
        } catch (IOException ignored) {
            this.ready.countDown();
        }
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    @Override
    public void close() {
        try {
            if (this.process.isAlive()) {
                try {
                    command("stop");
                } catch (IOException ignored) {
                    kill(this.process);
                }
                if (!this.process.waitFor(60, TimeUnit.SECONDS)) {
                    kill(this.process);
                }
            }
            this.reader.join(5000);
        } catch (InterruptedException exception) {
            kill(this.process);
            Thread.currentThread().interrupt();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(this.killOnExit);
            } catch (IllegalStateException ignored) {
                kill(this.process);
            }
        }
    }
}
