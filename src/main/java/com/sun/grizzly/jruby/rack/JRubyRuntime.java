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

import org.glassfish.scripting.jruby.common.config.JRubyConfig;
import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.internal.runtime.GlobalVariable.Scope;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ClassCache;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Vivek Pandey
 */
public class JRubyRuntime {
    public final Ruby ruby;

    public JRubyRuntime(JRubyConfig config) {
        long startTime = System.currentTimeMillis();

        RubyInstanceConfig iconfig = new RubyInstanceConfig();
        ArrayList<String> libs = new ArrayList<String>();
        libs.add("META-INF/jruby.home/lib/ruby/site_ruby/1.8");
        String additionalLoadPath = System.getProperty("addtional.load.path");
        if(additionalLoadPath != null){
            libs.add(additionalLoadPath);
        }
        try { // try to set jruby home to jar file path
            String binjruby = RubyInstanceConfig.class.getResource(
                    "/META-INF/jruby.home/bin/jruby").getFile();
            iconfig.setJRubyHome(binjruby.substring(0, binjruby.length() - 10));
        } catch (Exception e) {
            iconfig.setJRubyHome(config.jrubyHome());
        }

        ClassCache cache = new ClassCache(JRubyRuntime.class.getClassLoader());
        iconfig.setLoader(JRubyRuntime.class.getClassLoader());

        //enable jruby to report deeper stack trace for debugging purpose
        if (System.getProperty("jruby.debug") != null) {
            iconfig.processArguments(new String[]{"-d"});
        }

        iconfig.setClassCache(cache);
        this.ruby = JavaEmbedUtils.initialize(libs, iconfig);


        config.getLogger().log(Level.INFO,
                Messages.format(Messages.NEWINSTANCE_CREATION_TIME, "JRuby runtime",
                        System.currentTimeMillis() - startTime));


        configureJRubyRuntime(ruby, config);
    }

    public static void configureJRubyRuntime(Ruby runtime, JRubyConfig config){
    	Scope scope = Scope.GLOBAL;
        runtime.defineReadonlyVariable("$glassfish_config", JavaEmbedUtils.javaToRuby(runtime, config), scope);
        IRubyObject loggerObj = JavaEmbedUtils.javaToRuby(runtime, Logger.getLogger(JRubyRuntime.class.getName()));
        runtime.defineReadonlyVariable("$logger", loggerObj, scope);
        String logLevel = System.getProperty("glassfish.log-level");

        if(logLevel != null){
            if(logLevel.equalsIgnoreCase("OFF")){
                logLevel = "FATAL";
            }else if(logLevel.equalsIgnoreCase("SEVERE")){
                logLevel = "ERROR";
            }else if(logLevel.equalsIgnoreCase("WARNING")){
                logLevel = "WARN";
            }else if(logLevel.equalsIgnoreCase("INFO")||logLevel.equalsIgnoreCase("FINE") || logLevel.equalsIgnoreCase("FINER") || logLevel.equalsIgnoreCase("FINEST")){
                logLevel = "DEBUG";
            }else if(logLevel.equalsIgnoreCase("CONFIG")){
                logLevel = "INFO";
            }
            //set the glassfish log level
            IRubyObject loggerLevelObj = JavaEmbedUtils.javaToRuby(runtime, logLevel);
            runtime.defineReadonlyVariable("$glassfish_log_level", loggerLevelObj, scope);
        }
    }


    // This method should be removed when glassfish/java.util.logger actually implements this
    private String getEffectiveLogLevel(Logger logger) {
        Level myLevel = logger.getLevel();

        Logger pLogger = logger;
        while (myLevel == null) {
            pLogger = pLogger.getParent();
            myLevel = pLogger.getLevel();
        }

        return myLevel.getName();
    }

}
