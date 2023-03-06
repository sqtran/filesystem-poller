package io.steve;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

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

        @Override
        public int run(String... args) throws Exception {
            Path path = Paths.get("/var/tmp/");
            WatchService watcher = FileSystems.getDefault().newWatchService();
            path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);

            try {
                while (true) {
                    // Get directory changes:
                    // take() is a blocking method that waits for a signal from the monitor before returning.
                    // You can also use the watcher.poll() method, a non-blocking method that will immediately return
                    // whether there is a signal in the watcher at that time.
                    // The returned result, WatchKey, is a singleton object,
                    // which is the same as the instance returned by the previous register method.
                    WatchKey key = watcher.take();
                    // Handling file change events：
                    // key.pollEvents()It is used to obtain file change events,
                    // which can only be obtained once and cannot be obtained repeatedly,
                    // similar to the form of a queue.
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // event.kind()：event type
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            //event may be lost or discarded
                            continue;
                        }
                        // Returns the path (relative path) of the file or directory that triggered the event
                        Path fileName = (Path) event.context();
                        System.out.println("file changed: " + fileName);
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