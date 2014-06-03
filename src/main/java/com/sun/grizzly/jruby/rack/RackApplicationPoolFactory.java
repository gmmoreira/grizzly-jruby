/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */
package com.sun.grizzly.jruby.rack;

import com.sun.grizzly.jruby.RackGrizzlyAdapter;
import com.sun.grizzly.scripting.pool.DynamicPool;
import com.sun.grizzly.scripting.pool.DynamicPoolConfig;
import com.sun.grizzly.scripting.pool.PoolAdapter;
import org.glassfish.scripting.jruby.common.config.JRubyRuntimeConfig;
import org.jruby.Ruby;
import org.jruby.exceptions.RaiseException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Vivek Pandey
 */
public class RackApplicationPoolFactory {

    public static RackApplicationPool<AbstractRackApplication> getRackApplocationPool(RackGrizzlyAdapter adapter) {
        try {
            AbstractRackApplication app;
            /**
             * The JVM might be started by JRuby it means there might already be a JRuby runtime present
             */
            if(System.getProperties().get("jruby.runtime") != null){
                adapter.config.getLogger().log(Level.FINE, "New JRuby runtime will not be created. Re-using JRuby runtime that started this JVM");
                Ruby runtime = (Ruby) System.getProperties().get("jruby.runtime");
                JRubyRuntime.configureJRubyRuntime(runtime, adapter.config);
                app = newApplication(runtime, adapter);
            }else{
                app = newApplication(adapter);
            }
            if (app.isMTSafe()) {
                return new MultithreadedRackApplicationPool(app);
            } else {
                return new SingleThreadedRackAppPool(app, adapter);
            }
        } catch (RaiseException e) {
            adapter.config.getLogger().log(Level.SEVERE, exceptionMessage(e), e);
            return null;
        }
    }

    private static AbstractRackApplication newApplication(RackGrizzlyAdapter adapter) {
        return newApplication(new JRubyRuntime(adapter.config).ruby, adapter);
    }

    private static AbstractRackApplication newApplication(Ruby runtime, RackGrizzlyAdapter adapter) {
        String appType = adapter.config.framework().type();
        long startTime = System.currentTimeMillis();

        AbstractRackApplication app;
        Logger logger = adapter.config.getLogger();
        if (appType.equalsIgnoreCase("rails")) {
            logger.fine("Creating RailsApplication...");
            app =  new RailsApplication(runtime, adapter);
        } else if (appType.equalsIgnoreCase("merb")) {
            logger.fine("Creating MerbApplication");
            app =  new MerbApplication(runtime, adapter);
        } else if (appType.equalsIgnoreCase("sinatra")) {
            logger.fine("Creating SinatraApplication");
            app =  new SinatraApplication(runtime, adapter);
        } else {
            logger.fine("Creating RackupApplication");
            app = new RackupApplication(runtime, adapter);
        }

        logger.log(Level.INFO,
                Messages.format(Messages.NEWINSTANCE_CREATION_TIME, appType,
                        System.currentTimeMillis() - startTime));
        return app;

    }


    private static String exceptionMessage(RaiseException re) {
        StringBuilder st = new StringBuilder();
        st.append(re.getException().toString()).append("\n");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        re.getException().printBacktrace(new PrintStream(b));
        st.append(b.toString());
        return st.toString();
    }


    private static class MultithreadedRackApplicationPool implements RackApplicationPool<AbstractRackApplication> {
        private final AbstractRackApplication app;

        public MultithreadedRackApplicationPool(AbstractRackApplication app) {
            this.app = app;
        }

        public void shutdown() {
            app.destroy();
        }

        public AbstractRackApplication getApp() {
            return app;
        }

        public void returnApp(AbstractRackApplication returned) {
            //do nothing, its MT safe so no pooling here
        }
    }


    private static class SingleThreadedRackAppPool implements RackApplicationPool<AbstractRackApplication> {

        private final DynamicPoolConfig myConfig;
        private DynamicPool<AbstractRackApplication> pool;
        private final int numThreads;

        public SingleThreadedRackAppPool(AbstractRackApplication app, RackGrizzlyAdapter adapter) {
            JRubyRuntimeConfig config = adapter.config.runtimeConfig();
            myConfig = new DynamicPoolConfig(config.getInitRuntime(), -1, config.getMaxRuntime(), config.getMinRuntime(), -1, -1, -1, adapter.async(), false);
            // Using defaults for, in order, maximum generating, downThreshold, queueThreshold, newThreshold
            numThreads = adapter.getNumThreads();
            RackApplicationPoolAdapter poolAdapter = new RackApplicationPoolAdapter(app, adapter);
            pool = new DynamicPool<AbstractRackApplication>(poolAdapter, myConfig);
            pool.start(numThreads);

            //TODO: Need to figure out how to update the pool monitoring data
            adapter.config.jRubyRuntimePoolProvider.runtimePoolStartEvent(
                    adapter.config.getAppName(),
                    adapter.config.runtimeConfig().getMinRuntime(),
                    adapter.config.runtimeConfig().getMaxRuntime(),
                    myConfig.getNumberOfObjects());

        }

        public void shutdown() {
            pool.stop();
        }

        public AbstractRackApplication getApp() {
            return pool.borrowObject();
        }

        public void returnApp(AbstractRackApplication returned) {
            pool.returnObject(returned);
        }

        private class RackApplicationPoolAdapter implements PoolAdapter<AbstractRackApplication> {

            private final RackGrizzlyAdapter adapter;

            /**
             * There might be an app created already, this needs to be added to the pool
             */
            private AbstractRackApplication app;

            private boolean addPreviousApp;

            private RackApplicationPoolAdapter(AbstractRackApplication app, RackGrizzlyAdapter adapter) {
                this.adapter = adapter;
                this.app = app;
                this.addPreviousApp = true;
            }

            public AbstractRackApplication initializeObject() {
                /** Is there better way to do it? */
                if (addPreviousApp) {
                    addPreviousApp = false;
                    return app;
                }
                return newApplication(adapter);
            }

            public void dispose(AbstractRackApplication rackApplication) {
                rackApplication.destroy();
            }

            public boolean validate(AbstractRackApplication rackApplication) {
                return true;
            }
        }

    }

}
