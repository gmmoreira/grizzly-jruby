/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.standalone;

import org.glassfish.scripting.jruby.common.config.JRubyConfig;
import org.glassfish.scripting.jruby.common.config.JRubyRuntimeConfig;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Vivek Pandey
 */
public class JRubyConfigImpl implements JRubyConfig {
    private final String jrubyHome;
    private final String railsRoot;
    private final String contextRoot;
    private final String environment;
    private final String appType;
    private boolean mtSafe = false;
    private final String gemPath;
    private final String appName;

    private JRubyRuntimeConfig runtimeConfig;
    private final Logger logger;

    public JRubyConfigImpl(Properties props, String appName, String railsRoot, String contextRoot, Logger logger) {
        this.logger = logger;
        this.railsRoot = railsRoot;
        this.contextRoot = contextRoot;
        this.appName = appName;

        String home = props.getProperty(JRUBY_HOME);
        File f = new File(home);
        try {
            home = f.getCanonicalPath();
        } catch (IOException e) {
            home = f.getAbsolutePath();
        }

        this.jrubyHome = home;

        String re = System.getProperty(RAILS_ENV);
        if (re == null) {
            re = props.getProperty(JRUBY_ENV);
        }
        if (re == null) {
            re = System.getenv("RAILS_ENV");
        }
        this.environment = (re == null) ? "development" : re;
        logger.info("Environment: "+this.environment);
        appType = (props.getProperty(APPLICATION_TYPE) == null)?System.getProperty(APPLICATION_TYPE):props.getProperty(APPLICATION_TYPE);
        String val = props.getProperty(MT_Safe);
        if(val == null) {
            val = System.getProperty(MT_Safe);
        }
        mtSafe = Boolean.valueOf(val);
        gemPath = (System.getenv("GEM_PATH")== null)?System.getProperty("gem.path"):"";

        int numRt = 1;
        int numMinRt = -1;
        int numMaxRt = -1;

        String jrubyRuntime = System.getProperty(JRUBY_RUNTIME);
        String jrubyMinRuntime = System.getProperty(JRUBY_RUNTIME_MIN);
        String jrubyMaxRuntime = System.getProperty(JRUBY_RUNTIME_MAX);

        try {
            if (props.getProperty(JRUBY_RUNTIME) != null) {
                numRt = toInt(props.getProperty(JRUBY_RUNTIME), JRUBY_RUNTIME);
            } else if (jrubyRuntime != null) {
                numRt = toInt(jrubyRuntime, JRUBY_RUNTIME);
            }

            if (props.getProperty(JRUBY_RUNTIME_MIN) != null) {
                numMinRt = toInt(props.getProperty(JRUBY_RUNTIME_MIN), JRUBY_RUNTIME_MIN);
            } else if (jrubyMinRuntime != null) {
                numMinRt = toInt(jrubyMinRuntime, JRUBY_RUNTIME_MIN);
            }

            if (props.getProperty(JRUBY_RUNTIME_MAX) != null) {
                numMaxRt = toInt(props.getProperty(JRUBY_RUNTIME_MAX), JRUBY_RUNTIME_MAX);
            } else if (jrubyMaxRuntime != null) {
                numMaxRt = toInt(jrubyMaxRuntime, JRUBY_RUNTIME_MAX);
            }

        } catch (NumberFormatException ex) {
            //just skip it.
        }

        final int minRuntime = numMinRt;
        final int maxRuntime = numMaxRt;
        final int numberOfRuntime = numRt;

        runtimeConfig = new JRubyRuntimeConfig(){
            public int getInitRuntime() {
                return numberOfRuntime;
            }

            public int getMinRuntime() {
                return minRuntime;
            }

            public int getMaxRuntime() {
                return maxRuntime;
            }
        };
    }

    private int toInt(String value, String propName) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            logger.log(Level.WARNING, "Runtime "+propName+" has invalid value: "+value, ex);
            throw ex;
        }
    }

    public String jrubyHome() {
        return jrubyHome;
    }

    public String appRoot() {
        return railsRoot;
    }

    public String contextRoot() {
        return contextRoot;
    }

    public String environment() {
        return environment;
    }

    public Framework framework() {
        return new Framework(){

            public String type() {
                return appType;
            }

            public File initScript() {
                return new File(appType);
            }
        };
    }

    public String applicationType() {
        return appType;
    }

    public String initScript() {
        return null;
    }

    public String gemPath() {
        return gemPath;
    }

    public Logger getLogger() {
        return logger;
    }

    public boolean isMTSafe() {
        return mtSafe;
    }

    public JRubyRuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    public String getAppName() {
        return appName;
    }
}
