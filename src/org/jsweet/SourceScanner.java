package org.jsweet;

/*

   Copyright 2016 Eric Ponthiaux -/- ponthiaux.eric@gmail.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/


/* @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com */

import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SourceScanner {

    private List<String> sourcePaths;

    private String[] includes;

    private String[] excludes;

    private String[] shared;

    private AbstractJSweetMojo mojo;


    public SourceScanner(

            AbstractJSweetMojo mojo,
            List<String> sourcePaths,
            String[] includes,
            String[] excludes,
            String[] shared

    ) {

        this.mojo = mojo;
        this.sourcePaths = sourcePaths;
        this.includes = includes;
        this.excludes = excludes;
        this.shared = shared;

    }

    public ArrayList<Path> scan() {

        ArrayList<Path> paths = new ArrayList<>();

        int k, l;

        for (String sourcePath : sourcePaths) {

            mojo.getLog().info("     - Analysing " + sourcePath);

            DirectoryScanner dirScanner = new DirectoryScanner();

            dirScanner.setBasedir(new File(sourcePath));

            dirScanner.setIncludes(ArrayUtils.addAll(includes, shared));

            dirScanner.setExcludes(excludes);

            dirScanner.scan();

            /*  */

            String[] includedDirectories = dirScanner.getIncludedDirectories();

            /*  */

            if (includedDirectories.length == 0) {

                mojo.getLog().info("     - No source includes found , using [" + sourcePath + "]");

                includedDirectories = new String[]{sourcePath};

            } else {

                mojo.getLog().info("     - " + includedDirectories.length + " directory found .");

                for (k = 0, l = includedDirectories.length; k < l; k++) {

                    includedDirectories[k] = dirScanner.getBasedir().getPath() + System.getProperty("file.separator") + includedDirectories[k];

                }

            }

            for (k = 0, l = includedDirectories.length; k < l; k++) {

                Path path = Paths.get(includedDirectories[k]);

                paths.add(path);

            }


        }

        return paths;

    }

}
