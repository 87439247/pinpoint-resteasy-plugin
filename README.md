# How to build it

1. Download [pinpoint-resteasy-plugin-1.6.0-RC2.jar](https://github.com/auslides/repository/raw/master/public/pinpoint/pinpoint-resteasy-plugin-1.6.0-RC2.jar), copy the jar file to [Pinpoint agent](https://github.com/naver/pinpoint/releases/download/1.6.0-RC2/pinpoint-agent-1.6.0-RC2.tar.gz)'s plugins directory.

2. Configure the bootstrap main class in pinpoint.config
<pre><code>###########################################################
# resteasy                                                  #
###########################################################
#profiler.resteasy.enable=true
# Classes for detecting application server type. Comma separated list of fully qualified class names. Wildcard not supported.
profiler.resteasy.bootstrap.main=org.greg.resteasy.Main
</code></pre>

See the [Sample Project](https://github.com/auslides/netty-resteasy-spring) for details.
