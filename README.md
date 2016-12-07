# Usage

1. Download [pinpoint-resteasy-plugin-1.6.0-RC2.jar](https://github.com/auslides/repository/raw/master/public/pinpoint/pinpoint-resteasy-plugin-1.6.0-RC2.jar), copy the jar file to [Pinpoint agent](https://github.com/naver/pinpoint/releases/download/1.6.0-RC2/pinpoint-agent-1.6.0-RC2.tar.gz)'s /plugins, [Pinpoint Collector](https://github.com/naver/pinpoint/releases/download/1.6.0-RC2/pinpoint-collector-1.6.0-RC2.war)'s WEB-INF/lib and [Pinpoint Web](https://github.com/naver/pinpoint/releases/download/1.6.0-RC2/pinpoint-web-1.6.0-RC2.war)'s WEB-INF/lib directories. 

2. Configure the bootstrap main class in pinpoint.config
<pre><code>###########################################################
# resteasy                                                  #
###########################################################
#profiler.resteasy.enable=true
#profiler.resteasy.isnetty4=true
# Classes for detecting application server type. Comma separated list of fully qualified class names. Wildcard not supported.
profiler.resteasy.bootstrap.main=org.greg.resteasy.Main
</code></pre>

See the sample Project of [resteasy-netty3](https://github.com/auslides/netty-resteasy-spring) or [resteasy-netty4](https://github.com/auslides/netty4-resteasy-spring) for details.
