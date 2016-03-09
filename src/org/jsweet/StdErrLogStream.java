package org.jsweet;

import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

/**
 *
 * @EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 *
 */

public class StdErrLogStream extends ByteArrayOutputStream {

    private Log log;

    public StdErrLogStream(Log log) {

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
