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

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.jruby.JRubyGrizzlyConfigImpl;
import com.sun.grizzly.jruby.RackGrizzlyAdapter;
import com.sun.grizzly.standalone.JRubyConfigImpl;
import com.sun.grizzly.util.LoggerUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class JrubyTest {
RackGrizzlyAdapter myApp;
    
    GrizzlyWebServer ws;
    String target;
    int port;

//    @BeforeTest
    public void sanity() throws Exception {
        // set out with some basic properties
        String portVal = (System.getProperty("jruby.port") == null)?"8080":System.getProperty("jruby.port");
        port = Integer.valueOf(portVal);
        ws  = new GrizzlyWebServer(port);
        target = "http://localhost:"+port+"/test";
        Properties props = new Properties();
        props.setProperty("jruby.home", "/home/jacob/jruby");
        props.setProperty("jruby.applicationType", "test");
        try {
            JRubyGrizzlyConfigImpl config = new JRubyGrizzlyConfigImpl(new JRubyConfigImpl(props, "test", "bar","foo", LoggerUtils.getLogger()));
            myApp = new RackGrizzlyAdapter(config, false); // Will throw IllegalStateException if it can't load the test app
            ws.start();
            ws.addGrizzlyAdapter(myApp, new String[]{"/test"});

            try {
                System.out.println("Starting sleep");
                Thread.sleep(15000);
                System.out.println("Ending sleep");
            } catch (InterruptedException f) {
                System.err.println("zzz");
            }
            testURL(target, " ", "");
        } catch (Exception e) {
            System.err.println("Sanity Test failed: " + e.getMessage());
            e.printStackTrace(System.err);
            throw e;
        }
        System.out.println("Sanity Test completed!");
    }
//    @Test(groups={"basic"})
    public void requestResponse() throws Exception {
        final String EXPECTED_RESPONSE = "200 OK";
        System.out.println("Jruby basic test target is " + target + ", expected response is " + EXPECTED_RESPONSE);
        testURL(target, EXPECTED_RESPONSE, "");


    }
//    @Test(groups={"passthrough"})
    public void read() throws Exception {
        // tests to make sure that we can get a body out of the WSGI adapter
        final String EXPECTED_RESPONSE = "<html>";
        System.out.println("Jruby read test target is " + target + ", expected response is " + EXPECTED_RESPONSE);
        testURL(target, EXPECTED_RESPONSE, "");
    }
//    @Test(groups={"passthrough"})
    public void environment()  throws Exception {
        // tests to make sure that are sending environment variables through correctly
        final String EXPECTED_RESPONSE = "SERVER_PORT";
        System.out.println("Jruby environment test target is " + target + ", expected response is " + EXPECTED_RESPONSE);
        testURL(target, EXPECTED_RESPONSE, "");
        testURL(target, String.valueOf(port), "");
    }
    // Post test disabled pending modification of test app to correctly display posted data
    /*
    @Test(groups={"passthrough"})
    public void post()  throws Exception {
        // tests to make sure that we are passing post data through correctly
        String postData = URLEncoder.encode("text", "UTF-8") + "=" + URLEncoder.encode("Post Data Test", "UTF-8");
        final String EXPECTED_RESPONSE = "Post Data Test";
        System.out.println("Jython post test target is " + target + ", expected response is " + EXPECTED_RESPONSE);
        testURL(target, "Post Data Test", postData);
    }
    */

    private void testURL(String targetURL, String expected, String post) throws Exception {
        try {
            URL url = new URL(targetURL);
            //echo("Connecting to: " + url.toString());
            InputStream is;
            HttpURLConnection conn= (HttpURLConnection) url.openConnection();
            try {
                if (!(post.equals(""))) {
                    conn.setDoOutput(true);
                    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                    wr.write(post);
                    wr.flush();
                    wr.close();
                }
                conn.connect();
                is = conn.getInputStream();

            } catch (FileNotFoundException e) {
                System.out.println("FNF exception on " + targetURL);
                is = conn.getErrorStream();
            }



            BufferedReader input = new BufferedReader(new InputStreamReader(is));

            String line;
            boolean result=false;
            String testLine = "";
            if(expected.compareTo("" + conn.getResponseCode() + " " + conn.getResponseMessage()) == 0) {
                result=true;
                System.out.println("expected response matched status code + message");
            } else {
                while ((line = input.readLine()) != null) {
                    //System.out.println(line);
                    if(line.indexOf(expected)!=-1){
                        result=true;

                        testLine = testLine + "\n" + line;
                    }

                }
            }
            if (result) {System.out.println("URL " + targetURL + " passed!");}
            else {System.out.println("URL " + targetURL + " FAILED!");}
            Assert.assertEquals(result, true,"Unexpected HTML");


        }catch(Exception e){
            System.out.println("Exception! " + e);
            e.printStackTrace();
            throw new Exception(e);
        }
    }
}
