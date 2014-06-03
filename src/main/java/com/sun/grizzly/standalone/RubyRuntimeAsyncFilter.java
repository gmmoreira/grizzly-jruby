/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */
package com.sun.grizzly.standalone;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.arp.AsyncTask;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

/*
    Is this class even needed anymore? I didn't notice it suspending things in my load testing, so I'm not sure where in the code path it is invoked - Jacob 10/02/08
 */

/**
 * AsyncFilter that parks the request if the Ruby runtimes are all in use.
 *
 * @author Jeanfrancois Arcand
 */
public class RubyRuntimeAsyncFilter implements AsyncFilter {

    /**
     * Pool of Ruby Runtimes.
     */
    private BlockingQueue<?> rubyRuntimeQueue;


    /**
     * If we run out of runtimes, suspend the request and store it inside
     * a collection until a runtime becomes available.
     */
    private LinkedBlockingQueue<AsyncProcessorTask> suspendedRequests
            = new LinkedBlockingQueue<AsyncProcessorTask>();


    /**
     * Default AsynFilter constructor.
     */
    public RubyRuntimeAsyncFilter() {
    }


    /**
     * If we know we gonna run out of ruby runtimes, suspend the request and
     * release the current thread. The request will be resumed as soon as
     * the RailsAdapter terminates handling the request.
     *
     * @param asyncExecutor the AsyncExecutor with the current request
     * @return true if the request can execute, false if it needed to be suspended.
     */
    public boolean doFilter(AsyncExecutor asyncExecutor) {
        AsyncProcessorTask apt = (AsyncProcessorTask) asyncExecutor.getAsyncTask();

        if (rubyRuntimeQueue.size() == 0) {
            suspendedRequests.offer(apt);
            return false;
        } else {
            try {
                asyncExecutor.getProcessorTask().invokeAdapter();
            } catch (IllegalStateException ex) {
                suspendedRequests.offer(apt);
                return false;
            }
            apt.setStage(AsyncTask.POST_EXECUTE);
            return true;
        }
    }


    /**
     * Updates the runtimeQueue with the current one from the pool
     *
     * @param rubyRuntimeQueue the queue to set
     */
    protected void setRubyRuntimeQueue(BlockingQueue<?> rubyRuntimeQueue) {
        this.rubyRuntimeQueue = rubyRuntimeQueue;
    }


    /**
     * Resume a request that has been suspended because we ran out of
     * ruby runtimes.
     */
    protected void resume() {
        AsyncProcessorTask apt = suspendedRequests.poll();

        // Nothing, bye.
        if (apt == null) return;

        apt.setStage(AsyncTask.EXECUTE);
        try {
            apt.doTask();
        } catch (IllegalStateException e) {
            // Runtime was zero, add the token back to the queue.
            // That means we just hit a thread race
            // TODO: FIX ME
            suspendedRequests.offer(apt);
        } catch (IOException ex) {
            if (RailsSelectorThread.logger().isLoggable(Level.FINEST)) {
                RailsSelectorThread.logger().log(Level.FINEST, "", ex);
            }
        }
    }

}
