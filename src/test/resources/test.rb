module JRuby
    module Rack
        class Rack_Test
            def initialize
                puts "Test app initialized!"
            end
            def call (env)
                resp = Array["<html><body> Hello World! <br>"]
                env.each do |key, value|
                    resp << "#{key} is #{value}<br>"
                end
                resp << "</body></html>"
                JRuby::Rack::Response.new([200, {"Content-Type" => "text/plain"}, resp])
            end #def
        end #RackTest
    end #Rack
end #Jruby

#hack to get around DefaultApplicationFactory's (correct) use of Rack::VERSION
module Rack
    VERSION = [0,4]
end