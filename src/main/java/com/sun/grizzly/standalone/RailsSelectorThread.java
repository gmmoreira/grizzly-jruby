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

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.jruby.JRubyGrizzlyConfigImpl;
import com.sun.grizzly.jruby.RackGrizzlyAdapter;
import org.glassfish.scripting.jruby.common.config.JRubyConfig;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;

/**
 * JRuby on rails implementation of Grizzly SelectorThread
 *
 * @author TAKAI Naoto
 * @author Jeanfrancois Arcand
 * @author Pramod Gopinath
 * @author Vivek Pandey
 */
public class RailsSelectorThread extends SelectorThread {

    private final static String NUMBER_OF_RUNTIME =
            "com.sun.grizzly.rails.numberOfRuntime";

    private String jrubyHome;

    private int numberOfRuntime = 1;
    private int maxRt = -1;
    private int minRt = -1; 

    private String railsRoot = null;

    public String getRailsRoot() {
        return railsRoot;
    }

    @Override
    public void initEndpoint() throws IOException, InstantiationException {
        setupSystemProperties();
        asyncExecution = true;
        RubyRuntimeAsyncFilter asyncFilter = new RubyRuntimeAsyncFilter();
        Properties props = new Properties();
        props.setProperty(JRubyConfig.JRUBY_HOME, jrubyHome);
        props.setProperty(JRubyConfig.JRUBY_RUNTIME, String.valueOf(numberOfRuntime));
        props.setProperty(JRubyConfig.JRUBY_RUNTIME_MIN, String.valueOf(minRt));
        props.setProperty(JRubyConfig.JRUBY_RUNTIME_MAX, String.valueOf(maxRt));
        JRubyGrizzlyConfigImpl config = new JRubyGrizzlyConfigImpl(new JRubyConfigImpl(props, railsRoot, railsRoot, "/", logger));
        adapter = new RackGrizzlyAdapter(config, asyncExecution);

        setWebAppRootPath(railsRoot + "/public");
        setBufferResponse(false);

        DefaultAsyncHandler asyncHandler = new DefaultAsyncHandler();
        setAsyncHandler(asyncHandler);
        asyncHandler.addAsyncFilter(asyncFilter);

        algorithmClassName = StaticStreamAlgorithm.class.getName();
        super.initEndpoint();
    }

    public void setNumberOfRuntime(int numberOfRuntime) {
        this.numberOfRuntime = numberOfRuntime;
    }

    public void setRailsRoot(String railsRoot) {
        this.railsRoot = railsRoot;
    }

    @Override
    public synchronized void stopEndpoint() {
        ((RackGrizzlyAdapter) adapter).destroy();
        super.stopEndpoint();
    }

    protected void setupSystemProperties() {
        jrubyHome = System.getenv().get("JRUBY_HOME");
        if (jrubyHome == null)
            jrubyHome = System.getProperty(JRubyConfig.JRUBY_HOME);

        if (System.getProperty(NUMBER_OF_RUNTIME) != null) {
            try {
                numberOfRuntime = Integer.parseInt(
                        System.getProperty(NUMBER_OF_RUNTIME));
            } catch (NumberFormatException ex) {
                SelectorThread.logger().log(Level.WARNING,
                        "Invalid number of Runtime: " + System.getProperty(NUMBER_OF_RUNTIME));
            }
        }

        //TODO: provide CLI options for max/min runtimes
        if (System.getProperty("jruby.runtime.max") != null) {
            try {
                maxRt = Integer.parseInt(System.getProperty("jruby.runtime.max"));
            } catch (NumberFormatException ex) {
                SelectorThread.logger().log(Level.WARNING,
                        "Invalid number of max runtime: " + System.getProperty("jruby.runtime.max"));
            }
        }
        if (System.getProperty("jruby.runtime.min") != null) {
             try {
                 minRt = Integer.parseInt(System.getProperty("jruby.runtime.min"));
             } catch (NumberFormatException ex) {
                 SelectorThread.logger().log(Level.WARNING,
                         "Invalid number of min runtime: " + System.getProperty("jruby.runtime.min"));
             }
         }
    }
}
