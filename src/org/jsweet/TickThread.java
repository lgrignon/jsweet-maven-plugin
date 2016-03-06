package org.jsweet;

import org.apache.maven.plugin.logging.Log;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public abstract class TickThread extends Thread {

    /* */

    private AbstractJSweetMojo mojo;
    private ReentrantLock lock = new ReentrantLock();
    private LinkedList<String> randomTriggers = new LinkedList<>();

    /* */

    public TickThread(AbstractJSweetMojo mojo) {

        this.mojo = mojo;

    }

    /* */

    public AbstractJSweetMojo getMojo() {

        return this.mojo;

    }

    /* */

    public Log getLog() {

        return this.mojo.getLog();

    }

    /* */

    public void run() {

        onStart();

        for (; ; ) {

            if (lock.tryLock()) {

                if (randomTriggers.size() != 0) {

                    randomTriggers.removeLast();

                    execute();

                }

                lock.unlock();
            }

            yield();

        }

    }

    /* */

    public void tick() {

        lock.lock();

        randomTriggers.add(generateRandomEntry());

        lock.unlock();

    }

    public abstract void onStart();

    /* */

    public abstract void execute();

    /* */

    private String generateRandomEntry() {

        return "" + ((int) Math.random() * 10000);

    }

}
