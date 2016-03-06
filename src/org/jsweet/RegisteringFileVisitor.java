package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.maven.plugin.AbstractMojo;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class RegisteringFileVisitor extends SimpleFileVisitor<Path> {

    private List<Path> directories;
    private WatchService watchService;
    private AbstractMojo mojo;

    private SensitivityWatchEventModifier sensitivity = SensitivityWatchEventModifier.HIGH;

    public RegisteringFileVisitor(List<Path> directories, WatchService watchService, AbstractMojo mojo) {

        this.directories = directories;
        this.watchService = watchService;
        this.mojo = mojo;

    }

    public void setSensitivity(SensitivityWatchEventModifier sensitivity) {

        this.sensitivity = sensitivity;

    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {

        // --> DO NOTHING

        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException exc) {

        directories.add(directory);

        try {

            directory.register(

                    this.watchService,

                    new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW},

                    sensitivity

            );

            this.mojo.getLog().info("  - Added [" + directory.toString() + "]");

        } catch (IOException ioException) {

            this.mojo.getLog().error("  * Cannot register [" + directory.toString() + "]");

        }

        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {

        // --> DO NOTHING

        return CONTINUE;
    }
}

