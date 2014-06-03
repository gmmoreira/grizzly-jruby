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
package com.sun.grizzly.jruby;

import com.sun.grizzly.jruby.rack.AbstractRackApplication;
import com.sun.grizzly.jruby.rack.ErrorApplication;
import com.sun.grizzly.jruby.rack.RackApplicationPool;
import com.sun.grizzly.jruby.rack.RackApplicationPoolFactory;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter implementation that bridges Ruby/Rack based application with Grizzly.
 *
 * @author TAKAI Naoto
 * @author Jean-Francois Arcand
 * @author Pramod Gopinath
 * @author Vivek Pandey
 * @author Peter Williams
 */
public class RackGrizzlyAdapter extends GrizzlyAdapter {

    private final Logger logger;
    private final int numThreads;
    private final boolean asyncExecution;
    /*package*/ final String jrubyVersion;
    private RackApplicationPool<AbstractRackApplication> pool;
    private ErrorApplication errorApp;


    public final JRubyGrizzlyConfigImpl config;


    public RackGrizzlyAdapter(JRubyGrizzlyConfigImpl config, boolean asyncExecution) {
        super(config.appRoot());
        this.config = config;
        this.asyncExecution = asyncExecution;
        this.logger = config.getLogger();

        jrubyVersion = org.jruby.runtime.Constants.VERSION;
        logger.log(Level.INFO, Messages.format(Messages.JRUBY_VERSION, jrubyVersion));
        numThreads = Math.min(Runtime.getRuntime().availableProcessors(), config.runtimeConfig().getInitRuntime());
    }


    public boolean async() {
        return asyncExecution;
    }

    public int getNumThreads() {
        return numThreads;
    }

    private FutureTask<RackApplicationPool<AbstractRackApplication>> jrubyStartUp;

    private void startThread() {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        jrubyStartUp = new FutureTask<RackApplicationPool<AbstractRackApplication>>(new Callable<RackApplicationPool<AbstractRackApplication>>() {

            public RackApplicationPool<AbstractRackApplication> call() throws Exception {
                return startJRubyRuntime();
            }
        });

        exec.submit(jrubyStartUp, RackApplicationPool.class);
    }


    @Override
    public void start() {
        startThread();
    }


    private RackApplicationPool<AbstractRackApplication> startJRubyRuntime() {
        return RackApplicationPoolFactory.getRackApplocationPool(this);
    }

    @Override
    public void destroy() {
        if (errorApp != null) {
            errorApp.destroy();
        }
        if (pool != null)
            pool.shutdown();
    }

    private final static String CSS =
            "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " +
                    "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " +
                    "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " +
                    "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " +
                    "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " +
                    "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}" +
                    "A {color : black;}" +
                    "HR {color : #525D76;}";


    private byte[] getHtmlPage(String text) {
        StringBuffer sb = new StringBuffer();
        sb.append("<html><head>");
        sb.append("<style><!--");
        sb.append(CSS);
        sb.append("--></style> ");
        sb.append("</head><body>");
        sb.append(text);
        sb.append("<hr/>");
        sb.append("<span\n" +
                " style=\"font-style: italic;\">Powered by GlassFish v3</span>");
        sb.append("</body></html>");
        return sb.toString().getBytes();
    }

    private void writeHtml(byte[] bytes, GrizzlyResponse res) {
        ByteChunk chunk = new ByteChunk();
        chunk.setBytes(bytes, 0, bytes.length);
        res.setContentLength(bytes.length);
        res.setContentType("text/html");
        try {
            res.getResponse().sendHeaders();
            res.getResponse().doWrite(chunk);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }


    }

    private void writeHtml(InputStream is, GrizzlyResponse res) throws IOException {
        ByteChunk chunk = new ByteChunk();
        int d;
        int i = 0;
        while ((d = is.read()) != -1) {
            chunk.append((byte) d);
            i++;
        }
        res.setContentLength(i);
        res.setContentType("text/html");
        try {
            res.getResponse().sendHeaders();
            res.getResponse().doWrite(chunk);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }


    }

    private void throwError(StringBuffer text, Exception e, GrizzlyResponse res) {
        res.setStatus(500);

        if (config.environment().equals("development") || config.environment().equals("test")) {
            text.append("<p>");
            text.append(e.getMessage()).append("<br/>");
            for (StackTraceElement el : e.getStackTrace()) {
                text.append(el.toString() + "<br/>");
            }
            text.append("</p>");
        }
        byte[] bytes = getHtmlPage(text.toString());
        writeHtml(bytes, res);
    }

    StringBuffer loadingHtml = null;

    private synchronized InputStream getTransformedHtml(String reqUri) throws IOException {
        if(loadingHtml != null){
            return new ByteArrayInputStream(loadingHtml.toString().getBytes());            
        }
        loadingHtml = new StringBuffer();
        InputStream is = getClass().getClassLoader().getResourceAsStream("loading.html");
        String cr = (config.contextRoot().equals("/")) ? config.contextRoot() : config.contextRoot() + "/";
        if(reqUri.startsWith(config.contextRoot())){
            cr = reqUri;
            if(!reqUri.endsWith("/")){
                cr += "/";
            }


        }

        int c;
        while ((c = is.read()) != -1) {
            if (c == '@') {
                int d = is.read();
                if (d == '@') {
                    //skip @@CONTEXT_ROOT@@
                    if (is.skip(14) == 14) {
                        loadingHtml.append(cr);
                    }
                } else {
                    loadingHtml.append(Character.toString((char) c));
                    if (d != -1) {
                        loadingHtml.append(Character.toString((char) d));
                    }
                }
            } else {
                loadingHtml.append(Character.toString((char) c));
            }
        }

        return new ByteArrayInputStream(loadingHtml.toString().getBytes());
    }

    public void service(GrizzlyRequest req, GrizzlyResponse res) {
        config.jrubyHttpProbeProvider.requestStartEvent(config.getAppName(), config.contextRoot(), req.getServerName(), req.getServerPort());
        if (!jrubyStartUp.isDone()) {
            res.setStatus(503);

            //jruby is still being loaded, wait for 3 sec
            String delta = System.getProperty("jruby.http.retry-after");
            if (delta == null) {
                delta = "3";
            }
            res.setHeader("Retry-After", delta);

            String cr = (config.contextRoot().equals("/")) ? config.contextRoot() : config.contextRoot() + "/";
            InputStream is = new ByteArrayInputStream(getHtmlPage("<h3>Server is busy loading the application... </h3>"));
            try {
                if (req.getRequestURI().endsWith("asynch-1F.gif")) {
                    is = getClass().getClassLoader().getResourceAsStream("asynch-1F.gif");
                } else if (req.getRequestURI().endsWith("backimage.jpg")) {
                    is = getClass().getClassLoader().getResourceAsStream("backimage.jpg");
                } else if (req.getRequestURI().startsWith(config.contextRoot()) || req.getRequestURI().startsWith(config.contextRoot()+"/")) {
                    if(req.getHeader("x-requested-with") != null){
                        new ByteArrayInputStream(new byte[]{' '});
                    }else{
                        is = getTransformedHtml(req.getRequestURI());
                    }
                }

                writeHtml(is, res);
            } catch (IOException e) {
                byte[] bytes = getHtmlPage("<h3>GlassFish is busy loading the application... </h3>");
                writeHtml(bytes, res);
            }
            return;
        }

        try {
            pool = jrubyStartUp.get();
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, Messages.format(Messages.RACKGRIZZLYADAPTER_SERVICE_JRUBYINSTANCE_FAILED), e);
            StringBuffer text = new StringBuffer();
            text.append("<h3>Something went wrong on the server.<br/>Check the server log for details!</h3>");
            throwError(text, e, res);
            return;
        } catch (ExecutionException e) {
            logger.log(Level.SEVERE, Messages.format(Messages.RACKGRIZZLYADAPTER_SERVICE_JRUBYINSTANCE_FAILED), e);
            StringBuffer text = new StringBuffer();
            text.append("<h3>Something went wrong on the server.<br/>Check the server log for details!</h3>");
            throwError(text, e, res);
            return;
        }

        if (pool == null) {
            logger.log(Level.SEVERE, Messages.format(Messages.RACKGRIZZLYADAPTER_SERVICE_JRUBYINSTANCE_FAILED));
            StringBuffer text = new StringBuffer();
            text.append("<h3>Something went wrong on the server.<br/>Check the server log for details!</h3>");
            res.setStatus(500);
            byte[] bytes = getHtmlPage(text.toString());
            writeHtml(bytes, res);
            return;
        }

        AbstractRackApplication serviceApp = null;
        try {
            // Borrow a Runtime
            serviceApp = pool.getApp();

            if (serviceApp == null) {
                throw new IllegalStateException(Messages.format(Messages.JRUBY_RUNTIME_NOTAVAILABLE));
            }
            dispatchRequest(serviceApp, req, res);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serviceApp != null) {
                pool.returnApp(serviceApp);
            }
            config.jrubyHttpProbeProvider.requestEndEvent(config.getAppName(), config.contextRoot(), res.getStatus());
        }
    }

    private void dispatchRequest(final AbstractRackApplication app, GrizzlyRequest req, GrizzlyResponse res) throws IOException {
        try {
            app.call(req).respond(res);
        } catch (Exception e) {
            res.setError();
            if (res.isCommitted()) {
                logger.log(Level.WARNING, Messages.format(Messages.RACKGRIZZLYADAPTER_SERVICE_CANTHANDLE_RESCOMIT), e);
                return;
            }
            res.reset();

            try {
                createErrorApp(app);
                req.setAttribute("rack.exception", e);
                logger.log(Level.WARNING, e.getMessage(), e);
                errorApp.call(req).respond(res);
            } catch (Exception ex) {
                logger.log(Level.WARNING, Messages.format(Messages.RACKGRIZZLYADAPTER_SERVICE_CANTHANDLE, ex.getMessage()), ex);
                res.sendError(500);
            }
        }
    }

    private synchronized void createErrorApp(AbstractRackApplication app) {
        if (errorApp == null)
            errorApp = new ErrorApplication(app.runtime, this);
    }
}
