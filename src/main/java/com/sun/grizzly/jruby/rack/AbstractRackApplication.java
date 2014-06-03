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
import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.RubyIO;
import org.jruby.RubyString;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Vivek Pandey
 */
public abstract class AbstractRackApplication implements RackApplication {
    private final IRubyObject application;
    public final Ruby runtime;
    private final Logger logger;
    private final RubyString rack_version;
    private final RubyString rack_multithread;
    private final RubyString rack_multiprocess;
    private final RubyString rack_run_once;
    private final RubyHash base;
    private final RubyString rack_input;
    private final RubyString rack_errors;
    private final RubyString rack_url_scheme;


    //Request methods
    private final RubyString req_method;
    private final RubyString script_name;
    private final RubyString request_uri;
    private final RubyString path_info;
    private final RubyString query_string;
    private final RubyString path_translated;
    private final RubyString server_name;
    private final RubyString remote_host;
    private final RubyString remote_addr;
    private final RubyString remote_user;
    private final RubyString server_port;
    private final RubyString content_type;
    private final RubyString content_length;
    private final RubyString server_software;

    protected final RackGrizzlyAdapter adapter;

    public AbstractRackApplication(IRubyObject application, RackGrizzlyAdapter adapter) {
        this.application = application;
        this.runtime = application.getRuntime();
        this.logger = adapter.config.getLogger();
        this.adapter = adapter;

        base = RubyHash.newHash(runtime);

        rack_version = runtime.newString("rack.version");
        rack_multithread = runtime.newString("rack.multithread");
        rack_multiprocess = runtime.newString("rack.multiprocess");
        rack_run_once = runtime.newString("rack.run_once");
        rack_input = runtime.newString("rack.input");
        rack_errors = runtime.newString("rack.errors");
        rack_url_scheme = runtime.newString("rack.url_scheme");

        base.put(rack_version, runtime.evalScriptlet("Rack::VERSION"));
        base.put(rack_multithread, runtime.getTrue());
        base.put(rack_multiprocess, runtime.getFalse());
        base.put(rack_run_once, runtime.getFalse());
        base.put(rack_errors, runtime.evalScriptlet("JRuby::Rack::GrizzlyLog.new"));

        req_method = runtime.newString("REQUEST_METHOD");
        script_name = runtime.newString("SCRIPT_NAME");
        request_uri = runtime.newString("REQUEST_URI");
        path_info = runtime.newString("PATH_INFO");
        query_string = runtime.newString("QUERY_STRING");
        path_translated = runtime.newString("PATH_TRANSLATED");
        server_name = runtime.newString("SERVER_NAME");
        remote_host = runtime.newString("REMOTE_HOST");
        remote_addr = runtime.newString("REMOTE_ADDR");
        remote_user = runtime.newString("REMOTE_USER");
        server_port = runtime.newString("SERVER_PORT");
        content_type = runtime.newString("CONTENT_TYPE");
        content_length = runtime.newString("CONTENT_LENGTH");
        server_software = runtime.newString("SERVER_SOTWARE");

        String serverSoftware = System.getProperty("server.software");
        if(serverSoftware == null){
            serverSoftware = "GlassFish v3";
        }

        base.put(server_software,  runtime.newString(serverSoftware));
    }

    public RackResponse call(final GrizzlyRequest grizzlyRequest) {
        Ruby runtime = application.getRuntime();
        RubyHash rackEnv = (RubyHash) base.dup();

        Request request = grizzlyRequest.getRequest();

        addReqInfo(grizzlyRequest, rackEnv);


        populateFromMimeHeaders(rackEnv, request.getMimeHeaders());
        populateFromMap(rackEnv, request.getAttributes());

        try {
            rackEnv.put(rack_input, RubyIO.newIO(runtime, Channels.newChannel(grizzlyRequest.getInputStream())));
        } catch (IOException ioe) {
            throw runtime.newIOErrorFromException(ioe);
        }
        rackEnv.put(rack_url_scheme, grizzlyRequest.getScheme());

        IRubyObject response = application.callMethod(runtime.getCurrentContext(),
                "call", rackEnv);
        Object obj = JavaEmbedUtils.rubyToJava(runtime, response, RackResponse.class);
        return (RackResponse) obj;
    }

    private void addReqInfo(GrizzlyRequest grequest, RubyHash hash){
        Request request = grequest.getRequest();

        RubyString reqUri;

        //skip context path
        if(adapter.config.contextRoot().length() > 1){
            byte[] bytes = request.requestURI().getByteChunk().getBuffer();
            byte[] contextpath = adapter.config.contextRoot().getBytes();

            boolean hasContextRoot = true;
            int offset=request.requestURI().getByteChunk().getOffset();

            for (int i = 0; i < contextpath.length; i++) {
                if (bytes[offset] != contextpath[i]) {
                    hasContextRoot = false;
                    break;
                }

                if (offset < bytes.length) {
                    offset++;
                }
            }
            if(hasContextRoot){
                int lenoffset = (offset > request.requestURI().getByteChunk().getOffset())?offset-request.requestURI().getByteChunk().getOffset():offset;
                reqUri = RubyString.newStringShared(
                runtime,
                bytes,
                offset,
                request.requestURI().getByteChunk().getLength() - lenoffset);
            }else{
                reqUri = newStringFromMessageBytes(request.requestURI());
            }
           hash.put(request_uri, newStringFromMessageBytes(request.requestURI()));
        }else{
            reqUri = newStringFromMessageBytes(request.requestURI());
        }


        hash.put(path_info, reqUri);
        hash.put(path_translated, reqUri);

        add(hash, req_method, request.method());
        add(hash, query_string, request.queryString());

         //grizzly reads these headers lazily, so need to send actions.
        request.action(ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE, null);
        add(hash, server_name, request.serverName());
        request.action(ActionCode.ACTION_REQ_HOST_ATTRIBUTE, null);
        add(hash, remote_host, request.remoteHost());
        request.action(ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE, null);
        add(hash, remote_addr, request.remoteAddr());
        request.action(ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE, null);
        hash.put(server_port, runtime.newString(Integer.toString(request.getLocalPort())));

        //There is no action for remote user from grizzly. Hopefully it will be available.
        add(hash, remote_user, request.getRemoteUser());

        hash.put(script_name, runtime.newString(""));

        if(request.getContentLength() > 0){
            hash.put(content_length, runtime.newString(Integer.toString(request.getContentLength())));
        }

        add(hash, content_type, request.contentType());
    }

    private void add(RubyHash hash, RubyString key, MessageBytes value){
        if(value == null || value.isNull())
            return;
        
        //For many mime headers, grizzly maynot have MEssageBytes created from byte[]. so lets get things moved to byte[]
        value.toBytes();

        hash.put(key, newStringFromMessageBytes(value));
    }

    private void populateFromMap(RubyHash env, Map source) {
        for (Object obj : source.entrySet()) {
            Map.Entry entry = (Map.Entry) obj;
            env.put(
                    newStringFromMessageBytes((MessageBytes) entry.getKey()),
                    newStringFromMessageBytes((MessageBytes) entry.getValue()));
        }
    }

    private void populateFromMimeHeaders(RubyHash env, MimeHeaders source) {
        for (int i = 0; i < source.size(); i++) {
            MessageBytes key = source.getName(i);

            //Rack spec disallows adding content-type and content-length with HTTP_ prefix
            //Need to find a way to ignore it.
            if(key.startsWithIgnoreCase("content-type", 0) ||
                    key.startsWithIgnoreCase("content-length", 0))
                continue;

            MessageBytes mb = MessageBytes.newInstance();

            byte[] httpKey = {'H', 'T', 'T', 'P','_'};
            try {
                mb.getByteChunk().append(httpKey, 0, httpKey.length);
                byte[] bytes = key.getByteChunk().getBuffer();
                for(int k=0; k < key.getByteChunk().getLength();k++){
                    bytes[key.getByteChunk().getOffset() + k] = (byte) Character.toUpperCase(bytes[key.getByteChunk().getOffset() + k]);
                    if(bytes[key.getByteChunk().getOffset() + k] == '-')
                        bytes[key.getByteChunk().getOffset() + k] = '_';
                }
                mb.getByteChunk().append(bytes, key.getByteChunk().getOffset(), key.getByteChunk().getLength());
            } catch (IOException e) {
                throw runtime.newIOErrorFromException(e);
            }
            MessageBytes value = source.getValue(i);
            env.put(
                    newStringFromMessageBytes(mb),
                    newStringFromMessageBytes(value));
        }
    }

    private RubyString newStringFromMessageBytes(MessageBytes messageBytes) {
        if (messageBytes == null) {
            //return RubyString.newEmptyString(runtime);
            // Can't return the empty string, since it has to actually be a nil
            return null;
        } else {
            ByteChunk chunk = messageBytes.getByteChunk();
            byte[] bytes = chunk.getBuffer();
            if (bytes == null) {
                return RubyString.newEmptyString(runtime);
            } else {
                return RubyString.newStringShared(
                        runtime,
                        bytes,
                        chunk.getStart(),
                        chunk.getLength());
            }
        }
    }

    public void destroy() {
        if(runtime != null)
            runtime.tearDown();
    }

    public boolean isMTSafe(){
        return adapter.config.isMTSafe();
    }
}