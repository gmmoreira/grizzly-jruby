module Rails
  class Configuration
    def threadsafe?
      if(Rails::Configuration.instance_methods.include?'threadsafe!')
        if(Rails::Configuration.instance_methods.include?'preload_frameworks')
          preload_frameworks && cache_classes && !dependency_loading && action_controller.allow_concurrency
        else
          cache_classes && !dependency_loading && action_controller.allow_concurrency
        end
      else
        false
      end
    end
  end
end