package org.jsweet;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class WatcherFileVisitor extends SimpleFileVisitor<Path> {

    private ArrayList<Path> directories = new ArrayList<>();

    public WatcherFileVisitor() {

    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {

        // --> DO NOTHING

        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException exc) {

        directories.add(directory);

        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {

        // --> DO NOTHING

        return CONTINUE;
    }

    public ArrayList<Path> getDirectories() {
        return directories;
    }
}

