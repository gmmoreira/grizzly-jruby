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

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.ClassLoaderUtil;

import java.io.File;
import java.io.IOException;

public class JRuby {

    /**
     * System property for the <code>SelectorThread</code> value.
     */
    private static final String SELECTOR_THREAD = "com.sun.grizzly.selectorThread";


    private static final String ADAPTER = "com.sun.grizzly.adapterClass";


    private static int port = 3000;


    private static String folder = "/public";


    private static int numberOfRuntime = 1;


    public static void main(String[] args) throws IOException, InstantiationException {
        if (args.length == 0) {
            printHelpAndExit();
        }
        // parse options
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
            } else if ("-n".equals(arg)) {
                i++;
                setNumberOfRuntimes(args[i]);
            } else if (arg.startsWith("--num-runtimes=")) {
                String num = arg.substring("--num-runtimes=".length(), arg.length());
                setNumberOfRuntimes(num);
            } else if ("-p".equals(arg)) {
                i++;
                setPort(args[i]);
            } else if (arg.startsWith("--port=")) {
                String num = arg.substring("--port=".length(), arg.length());
                setPort(num);
            }
        }
        // last argument must be RAILS_ROOT
        String railsRoot = args[args.length - 1];
        if (!new File(railsRoot).isDirectory()) {
            System.err.println("Illegal Rails root");
            printHelpAndExit();
        }

        if (new File("lib").exists()) {
            // Load jar under the lib directory
            Thread.currentThread().setContextClassLoader(
                    ClassLoaderUtil.createClassloader(
                            new File("lib"), JRuby.class.getClassLoader()));
        }

        RailsSelectorThread selectorThread = null;
        String selectorThreadClassname = System.getProperty(SELECTOR_THREAD);
        if (selectorThreadClassname != null) {
            selectorThread = (RailsSelectorThread)
                    loadInstance(selectorThreadClassname);
        } else {
            selectorThread = new RailsSelectorThread();
        }

        String applicationRoot = railsRoot + folder;
        selectorThread.setDisplayConfiguration(true);
        selectorThread.setRailsRoot(railsRoot);
        selectorThread.setWebAppRootPath(applicationRoot);
        selectorThread.setPort(port);
        selectorThread.setNumberOfRuntime(numberOfRuntime);
        
        String rdebug = System.getProperty("glassfish.rdebug");
        if(rdebug != null && rdebug.length() > 0) {
            selectorThread.setKeepAliveTimeoutInSeconds(-1);
        }

        String adapterClass = System.getProperty(ADAPTER);
        Adapter adapter;
        if (adapterClass == null) {
            adapter = new StaticResourcesAdapter();
            ((StaticResourcesAdapter) adapter).setRootFolder(folder);
        } else {
            adapter = (Adapter) loadInstance(adapterClass);
        }

        selectorThread.setAdapter(adapter);
        selectorThread.initEndpoint();
        selectorThread.startEndpoint();
    }

    /**
     * Util to load classes using reflection.
     */
    private static Object loadInstance(String property) {
        Class<?> className = null;
        try {
            className = Class.forName(property, true,
                    Thread.currentThread().getContextClassLoader());
            return className.newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            // Log me 
        }
        return null;
    }

    private static void setNumberOfRuntimes(String num) {
        try {
            numberOfRuntime = Integer.parseInt(num);
        } catch (NumberFormatException e) {
            System.err.println("Illegal number -- " + num);
            printHelpAndExit();
        }
    }

    private static void setPort(String num) {
        try {
            port = Integer.parseInt(num);
        } catch (NumberFormatException e) {
            System.err.println("Illegal port number -- " + num);
            printHelpAndExit();
        }
    }

    private static void printHelpAndExit() {
        System.err.println("Usage: " + JRuby.class.getCanonicalName() + " [options] RAILS_ROOT");
        System.err.println();
        System.err.println("    -p, --port=port                  Runs Rails on the specified port.");
        System.err.println("                                     Default: 3000");
        System.err.println("    -n, --num-runtimes=num           Specifies number of JRuby runtimes.");
        System.err.println("                                     Default: 1");
        System.err.println("    -h, --help                       Show this help message.");
        System.exit(1);
    }

}
