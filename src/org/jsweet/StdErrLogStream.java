package org.jsweet;

import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;


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


public class StdErrLogStream extends ByteArrayOutputStream {

    private Log log;

    public StdErrLogStream( Log log ) {

        this.log = log;

    }

    @Override
    public void flush() throws IOException {

        int s = 0;

        byte[] byteArray = toByteArray();

        for (int i = 0; i < byteArray.length; i++) {

            if ( byteArray[i] == '\n' ) {

                log.info(new String(Arrays.copyOfRange(byteArray,s,i)));

                s = i;

                s++;
                i++;

            }

        }

        reset();

    }
}
