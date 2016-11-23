/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.resteasy.interceptor;

import com.navercorp.pinpoint.bootstrap.config.Filter;
import com.navercorp.pinpoint.bootstrap.context.*;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.sampler.SamplingFlagUtils;
import com.navercorp.pinpoint.bootstrap.util.NumberUtils;
import com.navercorp.pinpoint.bootstrap.util.StringUtils;
import com.navercorp.pinpoint.common.trace.AnnotationKey;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.plugin.resteasy.RestEasyConfiguration;
import com.navercorp.pinpoint.plugin.resteasy.RestEasyConstants;
import com.navercorp.pinpoint.plugin.resteasy.RestEasySyncMethodDescriptor;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyUriInfo;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Set;

public class RequestDispatcherServiceInterceptor implements AroundInterceptor {
    public static final RestEasySyncMethodDescriptor RESTEASY_SYNC_METHOD_DESCRIPTOR = new RestEasySyncMethodDescriptor();
    public static final String Header_Host = "Host" ;  // hostname:port
    private PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();
    private final boolean isTrace = logger.isTraceEnabled();

    private final Filter<String> excludeUrlFilter;
    private final RemoteAddressResolver remoteAddressResolver;

    private MethodDescriptor methodDescriptor;
    private TraceContext traceContext;

    public RequestDispatcherServiceInterceptor(TraceContext traceContext, MethodDescriptor descriptor) {
        this.traceContext = traceContext;
        this.methodDescriptor = descriptor;

        RestEasyConfiguration restEasyConfig = new RestEasyConfiguration(traceContext.getProfilerConfig());
        this.excludeUrlFilter = restEasyConfig.getRestEasyExcludeUrlFilter();
        final String proxyIpHeader = restEasyConfig.getRestEasyRealIpHeader();
        if (proxyIpHeader == null || proxyIpHeader.isEmpty()) {
            this.remoteAddressResolver = new Bypass();
        } else {
            final String restEasyRealIpEmptyValue = restEasyConfig.getResteasyRealIpEmptyValue() ;
            this.remoteAddressResolver = new RealIpHeaderResolver(proxyIpHeader, restEasyRealIpEmptyValue);
        }

        traceContext.cacheApi(RESTEASY_SYNC_METHOD_DESCRIPTOR);
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        try {
            final Trace trace = createTrace(target, args);
            if (trace == null) {
                return;
            }
            // TODO STATDISABLE this logic was added to disable statistics tracing
            if (!trace.canSampled()) {
                return;
            }
            // ------------------------------------------------------
            SpanEventRecorder recorder = trace.traceBlockBegin();
            recorder.recordServiceType(RestEasyConstants.RESTEASY_METHOD);
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("BEFORE. Caused:{}", th.getMessage(), th);
            }
        }
    }

    public static class Bypass implements RemoteAddressResolver<HttpRequest> {
        @Override
        public String resolve(HttpRequest httpRequest) {
            return httpRequest.getHttpHeaders().getHeaderString(Header_Host) ;
        }
    }

    public static class RealIpHeaderResolver implements RemoteAddressResolver<HttpRequest> {

        public static final String X_FORWARDED_FOR = "x-forwarded-for";
        public static final String X_REAL_IP = "x-real-ip";
        public static final String UNKNOWN = "unknown";

        private final String realIpHeaderName;
        private final String emptyHeaderValue;

        public RealIpHeaderResolver() {
            this(X_FORWARDED_FOR, UNKNOWN);
        }

        public RealIpHeaderResolver(String realIpHeaderName, String emptyHeaderValue) {
            if (realIpHeaderName == null) {
                throw new NullPointerException("realIpHeaderName must not be null");
            }
            this.realIpHeaderName = realIpHeaderName;
            this.emptyHeaderValue = emptyHeaderValue;
        }

        @Override
        public String resolve(HttpRequest httpRequest) {
            final HttpHeaders httpHeaders = httpRequest.getHttpHeaders() ;
            final String realIp = httpHeaders.getHeaderString(this.realIpHeaderName);

            if (realIp == null || realIp.isEmpty()) {
                return httpRequest.getHttpHeaders().getHeaderString(Header_Host);
            }

            if (emptyHeaderValue != null && emptyHeaderValue.equalsIgnoreCase(realIp)) {
                return httpRequest.getHttpHeaders().getHeaderString(Header_Host);
            }

            final int firstIndex = realIp.indexOf(',');
            if (firstIndex == -1) {
                return realIp;
            } else {
                return realIp.substring(0, firstIndex);
            }
        }
    }



    private Trace createTrace(Object target, Object[] args) {
        final HttpRequest request = (HttpRequest) args[1];
        final URI absulutePath =  request.getUri().getAbsolutePath() ;
        final String remoteHost = request.getHttpHeaders().getHeaderString(Header_Host) ;
        final String requestURI = absulutePath.getPath();
        if (excludeUrlFilter.filter(requestURI)) {
            if (isTrace) {
                logger.trace("filter requestURI:{}", requestURI);
            }
            return null;
        }

        // check sampling flag from client. If the flag is false, do not sample this request.
        final boolean sampling = samplingEnable(request);
        if (!sampling) {
            // Even if this transaction is not a sampling target, we have to create Trace object to mark 'not sampling'.
            // For example, if this transaction invokes rpc call, we can add parameter to tell remote node 'don't sample this transaction'
            final Trace trace = traceContext.disableSampling();
            if (isDebug) {
                logger.debug("remotecall sampling flag found. skip trace requestUrl:{}, remoteAddr:{}", absulutePath.getPath(), absulutePath.getHost());
            }
            return trace;
        }

        final TraceId traceId = populateTraceIdFromRequest(request);
        if (traceId != null) {
            // TODO Maybe we should decide to trace or not even if the sampling flag is true to prevent too many requests are traced.
            final Trace trace = traceContext.continueTraceObject(traceId);
            if (trace.canSampled()) {
                SpanRecorder recorder = trace.getSpanRecorder();
                recordRootSpan(recorder, request);
                if (isDebug) {
                    logger.debug("TraceID exist. continue trace. traceId:{}, requestUrl:{}, remoteAddr:{}", traceId, absulutePath.getPath(), remoteHost);
                }
            } else {
                if (isDebug) {
                    logger.debug("TraceID exist. camSampled is false. skip trace. traceId:{}, requestUrl:{}, remoteAddr:{}", traceId, absulutePath.getPath(), remoteHost);
                }
            }
            return trace;
        } else {
            final Trace trace = traceContext.newTraceObject();
            if (trace.canSampled()) {
                SpanRecorder recorder = trace.getSpanRecorder();
                recordRootSpan(recorder, request);
                if (isDebug) {
                    logger.debug("TraceID not exist. start new trace. requestUrl:{}, remoteAddr:{}", absulutePath.getPath(), remoteHost);
                }
            } else {
                if (isDebug) {
                    logger.debug("TraceID not exist. camSampled is false. skip trace. requestUrl:{}, remoteAddr:{}", absulutePath.getPath(),remoteHost);
                }
            }
            return trace;
        }
    }

    private void recordRootSpan(final SpanRecorder recorder, final HttpRequest request) {
        // root
        recorder.recordServiceType(RestEasyConstants.RESTEASY);

        final URI absulutePath =  request.getUri().getAbsolutePath() ;
        final String requestURL = absulutePath.getPath();
        recorder.recordRpcName(requestURL);
        final String endPoint = request.getHttpHeaders().getHeaderString(Header_Host);
        recorder.recordEndPoint(endPoint);

        final String remoteAddr = remoteAddressResolver.resolve(request);
        recorder.recordRemoteAddress(remoteAddr);

        if (!recorder.isRoot()) {
            recordParentInfo(recorder, request);
        }
        recorder.recordApi(RESTEASY_SYNC_METHOD_DESCRIPTOR);
    }

    private void recordParentInfo(SpanRecorder recorder, HttpRequest request) {
        HttpHeaders httpHeaders = request.getHttpHeaders() ;
        String parentApplicationName = httpHeaders.getHeaderString(Header.HTTP_PARENT_APPLICATION_NAME.toString());
        if (parentApplicationName != null) {
            final String host = httpHeaders.getHeaderString(Header.HTTP_HOST.toString());
            if (host != null) {
                recorder.recordAcceptorHost(host);
            } else {
                recorder.recordAcceptorHost(request.getHttpHeaders().getHeaderString(Header_Host));
            }
            final String type = httpHeaders.getHeaderString(Header.HTTP_PARENT_APPLICATION_TYPE.toString());
            final short parentApplicationType = NumberUtils.parseShort(type, ServiceType.UNDEFINED.getCode());
            recorder.recordParentApplication(parentApplicationName, parentApplicationType);
        }
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logger.afterInterceptor(target, args, result, throwable);
        }

        final Trace trace = traceContext.currentRawTraceObject();
        if (trace == null) {
            return;
        }

        // TODO STATDISABLE this logic was added to disable statistics tracing
        if (!trace.canSampled()) {
            traceContext.removeTraceObject();
            return;
        }
        // ------------------------------------------------------
        try {
            SpanEventRecorder recorder = trace.currentSpanEventRecorder();
            final HttpRequest request = (HttpRequest) args[1];
            final String parameters = getRequestParameter(request, 64, 512);
            if (parameters != null && parameters.length() > 0) {
                recorder.recordAttribute(AnnotationKey.HTTP_PARAM, parameters);
            }

            recorder.recordApi(methodDescriptor);
            recorder.recordException(throwable);
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("AFTER. Caused:{}", th.getMessage(), th);
            }
        } finally {
            traceContext.removeTraceObject();
            deleteTrace(trace, target, args, result, throwable);
        }
    }

    /**
     * Populate source trace from HTTP Header.
     *
     * @param request
     * @return TraceId when it is possible to get a transactionId from Http header. if not possible return null
     */
    private TraceId populateTraceIdFromRequest(HttpRequest request) {

        HttpHeaders httpHeaders = request.getHttpHeaders() ;
        String transactionId = httpHeaders.getHeaderString(Header.HTTP_TRACE_ID.toString());
        if (transactionId != null) {

            long parentSpanID = NumberUtils.parseLong(httpHeaders.getHeaderString(Header.HTTP_PARENT_SPAN_ID.toString()), SpanId.NULL);
            long spanID = NumberUtils.parseLong(httpHeaders.getHeaderString(Header.HTTP_SPAN_ID.toString()), SpanId.NULL);
            short flags = NumberUtils.parseShort(httpHeaders.getHeaderString(Header.HTTP_FLAGS.toString()), (short) 0);

            final TraceId id = traceContext.createTraceId(transactionId, parentSpanID, spanID, flags);
            if (isDebug) {
                logger.debug("TraceID exist. continue trace. {}", id);
            }
            return id;
        } else {
            return null;
        }
    }

    private boolean samplingEnable(HttpRequest request) {
        // optional value
        final String samplingFlag = request.getHttpHeaders().getHeaderString(Header.HTTP_SAMPLED.toString());
        if (isDebug) {
            logger.debug("SamplingFlag:{}", samplingFlag);
        }
        return SamplingFlagUtils.isSamplingFlag(samplingFlag);
    }

    private String getRequestParameter(HttpRequest request, int eachLimit, int totalLimit) {
        ResteasyUriInfo resteasyUriInfo = request.getUri() ;
        MultivaluedMap<String, String> queryParameters = resteasyUriInfo.getQueryParameters(true) ;
        Set<String> attrs = queryParameters.keySet() ;
        final StringBuilder params = new StringBuilder(64);

        for (String key : attrs ) {
            if (params.length() != 0) {
                params.append('&');
            }
            // skip appending parameters if parameter size is bigger than totalLimit
            if (params.length() > totalLimit) {
                params.append("...");
                return params.toString();
            }
            params.append(StringUtils.drop(key, eachLimit));
            params.append("=");
            Object value = queryParameters.getFirst(key);
            if (value != null) {
                params.append(StringUtils.drop(StringUtils.toString(value), eachLimit));
            }
        }
        return params.toString();
    }

    private void deleteTrace(Trace trace, Object target, Object[] args, Object result, Throwable throwable) {
        trace.traceBlockEnd();
        trace.close();
    }

    private static String extracHostName(String host) {
        int n = host.indexOf(":") ;
        if ( n != -1 ) {
            return host.substring(0, n) ;
        } else
            return host ;
    }
}
