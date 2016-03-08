package org.jsweet;

import org.apache.maven.plugin.logging.Log;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

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

public abstract class TickThread extends Thread {

    /* */

    private AbstractJSweetMojo mojo;

    private ReentrantLock lock = new ReentrantLock();

    private LinkedList<String> randomTriggers = new LinkedList<>();

    private SecureRandom secureRandom;

    private boolean isRunning;

    public TickThread(AbstractJSweetMojo mojo) {

        this.mojo = mojo;

        try {

            secureRandom = SecureRandom.getInstance("SHA1PRNG");

        } catch (NoSuchAlgorithmException e) {

            this.mojo.getLog().error(e);
        }

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

        randomTriggers.add(String.valueOf(secureRandom.nextLong()));

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
