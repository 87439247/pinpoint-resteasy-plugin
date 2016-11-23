/**
 * Copyright 2014 NAVER Corp.
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
package com.navercorp.pinpoint.plugin.resteasy;

import static com.navercorp.pinpoint.common.util.VarArgs.va;

import java.security.ProtectionDomain;

import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentMethod;
import com.navercorp.pinpoint.bootstrap.instrument.Instrumentor;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformCallback;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformTemplate;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.TransformTemplateAware;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;
import com.navercorp.pinpoint.bootstrap.resolver.ConditionProvider;

public class RestEasyPlugin implements ProfilerPlugin, TransformTemplateAware {
    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private TransformTemplate transformTemplate;

    @Override
    public void setup(ProfilerPluginSetupContext context) {

        final RestEasyConfiguration config = new RestEasyConfiguration(context.getConfig());
        if (logger.isInfoEnabled()) {
            logger.info("RestEasyPlugin config:{}", config);
        }
        if (!config.isRestEasyEnable()) {
            logger.info("RestEasyPlugin disabled");
            return;
        }

        RestEasyDetector restEasyDetector = new RestEasyDetector(config.getRestEasyBootstrapMains());
        context.addApplicationTypeDetector(restEasyDetector);

        if (shouldAddTransformers(config)) {
            logger.info("Adding Resteasy transformers");
            addTransformers(config);
        } else {
            logger.info("Not adding Resteasy transfomers");
        }
    }

    private boolean shouldAddTransformers(RestEasyConfiguration config) {
        // Only transform if it's a Resteasy application
        ConditionProvider conditionProvider = ConditionProvider.DEFAULT_CONDITION_PROVIDER;
        boolean isRestEasyApplication = conditionProvider.checkMainClass(config.getRestEasyBootstrapMains());
        return isRestEasyApplication ;
    }

    private void addTransformers(final RestEasyConfiguration config){
        transformTemplate.transform("org.jboss.resteasy.plugins.server.netty.RequestDispatcher",  new TransformCallback() {
            @Override
            public byte[] doInTransform(Instrumentor instrumentor, ClassLoader classLoader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentor.getInstrumentClass(classLoader, className, classfileBuffer);

                InstrumentMethod handleMethodEditorBuilder = target.getDeclaredMethod(
                        "service",
                        "org.jboss.netty.channel.ChannelHandlerContext",
                        "org.jboss.resteasy.spi.HttpRequest",
                        "org.jboss.resteasy.spi.HttpResponse",
                        "boolean");
                if (handleMethodEditorBuilder != null) {
                    handleMethodEditorBuilder.addInterceptor("com.navercorp.pinpoint.plugin.resteasy.interceptor.RequestDispatcherServiceInterceptor");
                } else {
                    logger.error("RequestDispatcher.service() not found") ;
                }

                return target.toBytecode();
            }
        });
    }

    @Override
    public void setTransformTemplate(TransformTemplate transformTemplate) {
        this.transformTemplate = transformTemplate;
    }
}
