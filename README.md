# How to build it

1, First, following the Pinpoint [Installation Guide](https://github.com/naver/pinpoint/blob/master/doc/installation.md) to build Pinpoint, so that your local maven repository has the required pinpoint artifacts (Currently it requires Pinpoint-1.6.0-RC2).

2, Using
      mvn install -Dmaven.test.skip=true
to build this repository, then copy the jar to Pinpoint agent's plugins directory

# Configuration

Configure the bootstrap main class in pinpoint.cfg

<pre><code>
###########################################################
# resteasy                                                  #
###########################################################
#profiler.resteasy.enable=true
# Classes for detecting application server type. Comma separated list of fully qualified class names. Wildcard not supported.
profiler.resteasy.bootstrap.main=org.greg.resteasy.Main
</code></pre>

