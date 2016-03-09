package org.jsweet;

import org.apache.maven.plugin.logging.Log;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @
 */
public class JSweetPrintStream extends PrintStream {

    private Log log;

    public JSweetPrintStream(Log log, OutputStream out) {

        super(out);

        this.log = log;

    }

    @Override
    public void println(String x) {
        log.info(x);
    }

}
