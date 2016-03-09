package org.jsweet;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

import static java.nio.file.FileVisitResult.CONTINUE;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class FileVisitor extends SimpleFileVisitor<Path> {

    private ArrayList<Path> directories = new ArrayList<>();

    public FileVisitor() {

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

