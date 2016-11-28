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

import com.navercorp.pinpoint.bootstrap.config.ExcludePathFilter;
import com.navercorp.pinpoint.bootstrap.config.Filter;
import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.config.SkipFilter;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class RestEasyConfiguration {
    //private static final String REQUIRED_MAIN_CLASS = "avicit.platform6.core.rest.NettyStart";
    private static final String REQUIRED_MAIN_CLASS = "org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer";

    private final boolean restEasyEnable;
    private final Filter<String> restEasyExcludeUrlFilter;
    private final String restEasyRealIpHeader;
    private final String resteasyRealIpEmptyValue;
    private List<String> restEasyBootstrapMains;

    public RestEasyConfiguration(ProfilerConfig config) {
        this.restEasyEnable = config.readBoolean("profiler.resteasy.enable", true);
        this.restEasyBootstrapMains = config.readList("profiler.resteasy.bootstrap.main");
        if ( restEasyBootstrapMains == null || restEasyBootstrapMains.isEmpty()) {
            restEasyBootstrapMains = new ArrayList<String>();
            restEasyBootstrapMains.add(REQUIRED_MAIN_CLASS) ;
        }
        final String resteasyExcludeURL = config.readString("profiler.resteasy.excludeurl", "");
        if (!resteasyExcludeURL.isEmpty()) {
            this.restEasyExcludeUrlFilter = new ExcludePathFilter(resteasyExcludeURL);
        } else{
            this.restEasyExcludeUrlFilter = new  SkipFilter<String>();
        }

        this.restEasyRealIpHeader = config.readString("profiler.resteasy.realipheader", null);
        this.resteasyRealIpEmptyValue = config.readString("profiler.resteasy.realipemptyvalue", null);
    }

    public boolean isRestEasyEnable() {
        return restEasyEnable;
    }

    public List<String> getRestEasyBootstrapMains() {
        return restEasyBootstrapMains;
    }

    public Filter<String> getRestEasyExcludeUrlFilter() {
        return restEasyExcludeUrlFilter;
    }

    public String getRestEasyRealIpHeader() {
        return restEasyRealIpHeader;
    }

    public String getResteasyRealIpEmptyValue() {
        return resteasyRealIpEmptyValue;
    }
}
