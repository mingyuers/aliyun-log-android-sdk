package com.aliyun.sls.android.producer.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Build;
import android.os.Handler;
import android.util.Log;
import androidx.multidex.MultiDexApplication;
import com.aliyun.sls.android.core.SLSAndroid;
import com.aliyun.sls.android.core.SLSAndroid.OptionConfiguration;
import com.aliyun.sls.android.core.SLSLog;
import com.aliyun.sls.android.core.configuration.AccessKeyDelegate;
import com.aliyun.sls.android.core.configuration.ConfigurationManager;
import com.aliyun.sls.android.core.configuration.Credentials;
import com.aliyun.sls.android.core.configuration.Credentials.NetworkDiagnosisCredentials;
import com.aliyun.sls.android.core.configuration.Credentials.TracerCredentials;
import com.aliyun.sls.android.core.configuration.Credentials.TracerCredentials.TracerLogCredentials;
import com.aliyun.sls.android.core.configuration.ResourceDelegate;
import com.aliyun.sls.android.core.configuration.UserInfo;
import com.aliyun.sls.android.core.sender.Sender.Callback;
import com.aliyun.sls.android.core.utdid.Utdid;
import com.aliyun.sls.android.crashreporter.CrashReporter;
import com.aliyun.sls.android.okhttp.OKHttp3InstrumentationDelegate;
import com.aliyun.sls.android.okhttp.OKHttp3Tracer;
import com.aliyun.sls.android.okhttp.OkHttp3Configuration;
import com.aliyun.sls.android.ot.Attribute;
import com.aliyun.sls.android.ot.ISpanProvider;
import com.aliyun.sls.android.ot.Resource;
import com.aliyun.sls.android.ot.Span;
import com.aliyun.sls.android.producer.BuildConfig;
import com.aliyun.sls.android.producer.LogProducerResult;
import com.aliyun.sls.android.producer.example.utils.PreferenceUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import okhttp3.Request;

/**
 * @author gordon
 * @date 2021/08/31
 */
public class SLSDemoApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        SLSGlobal.application = this;
        SLSGlobal.applicationContext = this.getApplicationContext();

        if (BuildConfig.CONFIG_ENABLE) {
            PreferenceUtils.overrideConfig(this);
        }

        ConfigurationManager.setResourceDelegate(new ResourceDelegate() {
            @Override
            public String getEndpoint(String scope) {
                return "https://cn-hangzhou.log.aliyuncs.com";
            }

            @Override
            public String getProject(String scope) {
                return "sls-aysls-rum-mobile";
            }

            @Override
            public String getInstanceId(String scope) {
                return "sls-aysls-rum-mobile-rum-raw";
            }
        });

        ConfigurationManager.setAccessKeyDelegate(new AccessKeyDelegate() {
            @Override
            public String getAccessKeyId(String scope) {
                return PreferenceUtils.getAccessKeyId(SLSDemoApplication.this);
            }

            @Override
            public String getAccessKeySecret(String scope) {
                return PreferenceUtils.getAccessKeySecret(SLSDemoApplication.this);
            }

            @Override
            public String getAccessKeyToken(String scope) {
                return PreferenceUtils.getAccessKeyToken(SLSDemoApplication.this);
            }
        });
        if (true) {
            new CrashReporter(this).init(true);
            return;
        }

        initOTel();

        Credentials credentials = new Credentials();
        credentials.instanceId = "androd-dev-f1a8";
        credentials.endpoint = "https://cn-hangzhou.log.aliyuncs.com";
        credentials.project = "yuanbo-test-1";
        //credentials.accessKeyId = PreferenceUtils.getAccessKeyId(this);
        //credentials.accessKeySecret = PreferenceUtils.getAccessKeySecret(this);
        //credentials.securityToken = PreferenceUtils.getAccessKeyToken(this);

        TracerCredentials tracerCredentials = credentials.createTraceCredentials();
        //tracerCredentials.instanceId = "sls-mall";
        tracerCredentials.endpoint = "https://cn-beijing.log.aliyuncs.com";
        tracerCredentials.project = "qs-demos";
        // 自定义 Trace Logs 的写入位置
        //TracerLogCredentials logCredentials = tracerCredentials.createLogCredentials();
        //logCredentials.endpoint = "https://cn-beijing.log.aliyuncs.com";
        //logCredentials.project = "qs-demos";
        //logCredentials.logstore = "sls-mall-custom-logs";

        NetworkDiagnosisCredentials networkDiagnosisCredentials = credentials.getNetworkDiagnosisCredentials();
        networkDiagnosisCredentials.secretKey = PreferenceUtils.getNetworkSecKey(this);
        networkDiagnosisCredentials.endpoint = "https://cn-beijing.log.aliyuncs.com";
        networkDiagnosisCredentials.project = "mobile-demo-beijing-b";

        SLSAndroid.setUtdid(this, "123123131232");
        SLSAndroid.setLogLevel(Log.VERBOSE);
        final OptionConfiguration optionConfiguration = configuration -> {
            configuration.debuggable = true;
            configuration.spanProvider = new ISpanProvider() {
                @Override
                public Resource provideResource() {
                    return Resource.of("other_resource_key", "other_resource_value");
                }

                @Override
                public List<Attribute> provideAttribute() {
                    List<Attribute> attributes = new ArrayList<>();
                    attributes.add(Attribute.of("other_attribute_key", "other_attribute_value"));
                    return attributes;
                }
            };

            configuration.enableCrashReporter = true;
            configuration.enableNetworkDiagnosis = true;
            configuration.enableTracer = true;
            configuration.enableTracerLog = true;

            UserInfo info = new UserInfo();
            info.uid = "123321";
            info.channel = "dev";
            info.addExt("ext_key", "ext_value");
            configuration.userInfo = info;
        };

        // 预初始化，功能可正常使用，但敏感信息不会采集
        SLSAndroid.preInit(this, credentials, optionConfiguration);

        // 10 秒后完整初始化，模拟获得用户授权
        new Handler(getMainLooper()).postDelayed(
            () -> SLSAndroid.initialize(SLSDemoApplication.this, credentials, optionConfiguration),
            10 * 1000
        );

        SLSAndroid.setExtra("extra_key", "extra_value");
        SLSAndroid.setExtra("extra_key2", "extra_value2");
        SLSAndroid.registerCredentialsCallback(new Callback() {
            @Override
            public void onCall(String feature, LogProducerResult result) {
                SLSLog.v("DEBUGGGG", "feature: " + feature + ", result: " + result);
                if (LogProducerResult.LOG_PRODUCER_SEND_UNAUTHORIZED == result ||
                    LogProducerResult.LOG_PRODUCER_PARAMETERS_INVALID == result) {
                    // 处理token过期，AK失效等鉴权类问题
                    Credentials credentials = new Credentials();
                    credentials.accessKeyId = PreferenceUtils.getAccessKeyId(SLSDemoApplication.this);
                    credentials.accessKeySecret = PreferenceUtils.getAccessKeySecret(SLSDemoApplication.this);
                    credentials.securityToken = PreferenceUtils.getAccessKeyToken(SLSDemoApplication.this);

                    // 如果仅是更新 AK 的话，可以不对TracerCredentials进行更新
                    TracerCredentials tracerCredentials = credentials.createTraceCredentials();
                    tracerCredentials.securityToken = credentials.securityToken;
                    tracerCredentials.instanceId = "sls-mall";

                    TracerLogCredentials logCredentials = tracerCredentials.createLogCredentials();
                    logCredentials.endpoint = "https://cn-beijing.log.aliyuncs.com";
                    logCredentials.project = "qs-demos";
                    logCredentials.logstore = "sls-mall-custom-logs";

                    // 如果是仅更新 AK 的话，可以不对NetworkDiagnosisCredentials进行更新
                    //NetworkDiagnosisCredentials networkDiagnosisCredentials = credentials
                    // .getNetworkDiagnosisCredentials();
                    //networkDiagnosisCredentials.accessKeyId = credentials.accessKeyId;
                    //networkDiagnosisCredentials.accessKeySecret = credentials.accessKeySecret;
                    //networkDiagnosisCredentials.securityToken = credentials.securityToken;
                    //networkDiagnosisCredentials.secretKey = PreferenceUtils.getNetworkSecKey(SLSDemoApplication.this);

                    SLSAndroid.setCredentials(credentials);
                }
            }
        });
        OKHttp3Tracer.registerOKHttp3InstrumentationDelegate(new OKHttp3InstrumentationDelegate() {
            @Override
            public Map<String, String> injectCustomHeaders(Request request) {
                Map<String, String> headers = new HashMap<>();
                headers.put("h_key_1", "h_value_1");
                headers.put("h_key_2", "h_value_2");
                return headers;
            }

            @Override
            public String nameSpan(Request request) {
                // 自定义 http request span 的名称
                return request.method() + " " + request.url().encodedPath();
            }

            @Override
            public void customizeSpan(Request request, Span span) {
                // 自定义 http request span
                //span.setService(request.url().encodedPath());
            }

            @Override
            public boolean shouldInstrument(Request request) {
                final String host = request.url().url().getHost();
                // 只有request符合预期时才植入trace信息
                return !host.contains("log.aliyuncs.com");
            }
        });

        final OkHttp3Configuration configuration = new OkHttp3Configuration();
        // 允许采集 http request header 信息，默认不采集
        configuration.captureHeaders = true;
        // 允许采集 http request body 信息，默认不采集
        configuration.captureBody = true;
        // 允许采集 http response 信息，默认不采集
        configuration.captureResponse = true;
        OKHttp3Tracer.updateOkHttp3Configuration(configuration);

        //OKHttp3TracerInterceptor okHttp3TracerInterceptor = new OKHttp3TracerInterceptor();
        //okHttp3TracerInterceptor.registerOKHttp3InstrumentationDelegate(new OKHttp3InstrumentationDelegate() {
        //    @Override
        //    public Map<String, String> injectCustomHeaders(Request request) {
        //        // 返回自定义Header信息，该信息会插入到http request header 中
        //        return null;
        //    }
        //
        //    @Override
        //    public boolean shouldInstrument(Request request) {
        //        // 是否对当前请求插入trace信息，返回true表示插入trace信息
        //        // 如下表示对host中包含log.aliyuncs.com的请求都插入trace信息
        //        return request.url().host().contains("log.aliyuncs.com");
        //    }
        //});

        //redirectLog();
    }

    private void redirectLog() {
        final File logFile = new File(getCacheDir() + "/logfile.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String cmd = "logcat -b all -f " + logFile.getAbsolutePath() + "\n";
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initOTel() {
        OtlpGrpcSpanExporter grpcSpanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("https://cn-beijing.log.aliyuncs.com:10010")
            .addHeader("x-sls-otel-project", "qs-demos")
            .addHeader("x-sls-otel-instance-id", "sls-mall")
            .addHeader("x-sls-otel-ak-id", PreferenceUtils.getAccessKeyId(this))
            .addHeader("x-sls-otel-ak-secret", PreferenceUtils.getAccessKeySecret(this))
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(grpcSpanExporter).build())
            .setResource(io.opentelemetry.sdk.resources.Resource.create(Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, "Android Demo App")
                .put(ResourceAttributes.SERVICE_NAMESPACE, "Android")
                .put(ResourceAttributes.SERVICE_VERSION, BuildConfig.VERSION_NAME)
                .put(ResourceAttributes.HOST_NAME, Build.HOST)
                .put(ResourceAttributes.OS_NAME, "Android")
                .put(ResourceAttributes.OS_TYPE, "Android")
                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "dev")
                .put(ResourceAttributes.DEVICE_ID, Utdid.getInstance().getUtdid(this))
                .build()))
            .build();

        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
    }
}
