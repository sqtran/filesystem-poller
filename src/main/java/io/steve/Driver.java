package io.steve;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Driver {

    public static void main(String ... args) {
        System.out.println("Running main method");

        Quarkus.run(MyApp.class);
    }


    public static class MyApp implements QuarkusApplication {

        private static String WORKER_URL = "http://localhost:8080/file/";

        private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

        @Override
        public int run(String... args) throws Exception {
            Path path = Paths.get("/var/tmp/");
            WatchService watcher = FileSystems.getDefault().newWatchService();
            path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);


            WORKER_URL = System.getenv().get("WORKER_URL") != null ? System.getenv().get("WORKER_URL") : WORKER_URL;

            try {
                while (true) {
                    // Get directory changes:
                    // take() is a blocking method that waits for a signal from the monitor before returning.
                    // You can also use the watcher.poll() method, a non-blocking method that will immediately return
                    // whether there is a signal in the watcher at that time.
                    // The returned result, WatchKey, is a singleton object,
                    // which is the same as the instance returned by the previous register method.
                    WatchKey key = watcher.take();
                    // Handling file change events???
                    // key.pollEvents()It is used to obtain file change events,
                    // which can only be obtained once and cannot be obtained repeatedly,
                    // similar to the form of a queue.
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // event.kind()???event type
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            //event may be lost or discarded
                            continue;
                        }
                        // Returns the path (relative path) of the file or directory that triggered the event
                        Path fileName = (Path) event.context();


                        if(!fileName.endsWith(".processing") && !fileName.endsWith(".done")){
                            System.out.println("file changed: " + fileName);

                            System.out.println("Posting to " + WORKER_URL + fileName);

                            this.httpClient
                                     .sendAsync(
                                             HttpRequest.newBuilder()
                                                     .POST(HttpRequest.BodyPublishers.noBody())
                                                     .uri(URI.create(WORKER_URL + fileName))
                                                     .build()
                                             ,
                                             HttpResponse.BodyHandlers.ofString()
                                     )
                                     .thenApply(HttpResponse::body)
                                     .thenApply(Long::parseLong)
                                     .toCompletableFuture();
                        }
                    }
                    // This method needs to be reset every time the take() or poll() method of WatchService is called
                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Quarkus.waitForExit();
            return 0;
        }
    }

}