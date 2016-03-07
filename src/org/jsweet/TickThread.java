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

    private boolean isRunning;

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

    public boolean isRunning() {

        try {

            lock.lock();

            return isRunning;

        } finally {

            lock.unlock();

        }

    }

    /* */

    public void run() {

        onRun();

        lock.lock();

        isRunning = true;

        lock.unlock();

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

    public abstract void onRun();

    /* */

    public abstract void execute();

    /* */

    private String generateRandomEntry() {

        return "" + ((int) Math.random() * 10000);

    }

}
