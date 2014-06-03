package com.sun.grizzly.jruby.rack;

import com.sun.grizzly.jruby.RackGrizzlyAdapter;
import org.jruby.Ruby;
import org.jruby.runtime.builtin.IRubyObject;


public class TestApplicationFactory extends AbstractRackApplication {
    public TestApplicationFactory(Ruby runtime, RackGrizzlyAdapter adapter) {
        super(createApplicationObject(runtime), adapter);
    }

    private static IRubyObject createApplicationObject(Ruby runtime) {

        //runtime.defineReadonlyVariable("$glassfish_log_level", JavaEmbedUtils.javaToRuby(runtime, logger.getLevel().getName()));
        //runtime.evalScriptlet("require 'jruby/rack'");
        runtime.evalScriptlet("require 'jruby/rack/test'");
        runtime.evalScriptlet("require 'jruby/rack/grizzly_helper'");
        runtime.evalScriptlet("require 'rack/handler/grizzly'");
        return runtime.evalScriptlet("JRuby::Rack::Rack_Test.new");
    }
}