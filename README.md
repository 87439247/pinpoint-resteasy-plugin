1. This is [Pinpoint](https://github.com/naver/pinpoint) plugin for JBoss Resteasy (backended by Netty 3/4).

2. Configure the bootstrap main class in pinpoint.config. For resteasy-netty4, set profiler.resteasy.isnetty to true.
<pre><code>###########################################################
# resteasy                                                  #
###########################################################
#profiler.resteasy.enable=true
#profiler.resteasy.isnetty4=true
# Classes for detecting application server type. Comma separated list of fully qualified class names. Wildcard not supported.
profiler.resteasy.bootstrap.main=org.greg.resteasy.Main
</code></pre>
See the sample project for [resteasy-netty 3](https://github.com/auslides/netty-resteasy-spring) or [resteasy-netty 4](https://github.com/auslides/netty4-resteasy-spring) for details.

3. For displaying the proper resteasy icons on the server map of Pinpoint Web UI, the related icons in the images directory must be configured for [Pinpoint Web](https://github.com/auslides/pinpoint/tree/1.6.0-RC2/web), see ["4, Adding Images"](https://github.com/naver/pinpoint/wiki/Pinpoint-Plugin-Developer-Guide#4-adding-images) in [Pinpoint Plugin Developer Guide](https://github.com/naver/pinpoint/wiki/Pinpoint-Plugin-Developer-Guide). To easy the work, we build  [one](https://github.com/auslides/repository/raw/master/public/pinpoint/pinpoint-web-1.6.0.war) .
