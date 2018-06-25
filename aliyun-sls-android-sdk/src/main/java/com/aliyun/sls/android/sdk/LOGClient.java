package com.aliyun.sls.android.sdk;

import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.sls.android.sdk.core.AsyncTask;
import com.aliyun.sls.android.sdk.core.RequestOperation;
import com.aliyun.sls.android.sdk.core.auth.CredentialProvider;
import com.aliyun.sls.android.sdk.core.callback.CompletedCallback;
import com.aliyun.sls.android.sdk.model.LogGroup;
import com.aliyun.sls.android.sdk.request.PostCachedLogRequest;
import com.aliyun.sls.android.sdk.request.PostLogRequest;
import com.aliyun.sls.android.sdk.result.PostCachedLogResult;
import com.aliyun.sls.android.sdk.result.PostLogResult;
import com.aliyun.sls.android.sdk.utils.Base64Kit;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.zip.Deflater;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by wangjwchn on 16/8/2.
 * edited by wangzheng on 17/10/15
 */
public class LOGClient {

    private String mEndPoint;
    private String mAccessToken;
    private String mHttpType;
    private URI endpointURI;
    private RequestOperation requestOperation;
    private CacheManager cacheManager;
    private Boolean cachable;
    private ClientConfiguration.NetworkPolicy policy;
    private Context context;
    private WeakHashMap<PostLogRequest, CompletedCallback<PostLogRequest, PostLogResult>> mCompletedCallbacks = new WeakHashMap<PostLogRequest, CompletedCallback<PostLogRequest, PostLogResult>>();
    private CompletedCallback<PostLogRequest, PostLogResult> callbackImp;

    public LOGClient(Context context, String endpoint, CredentialProvider credentialProvider, ClientConfiguration conf) {
        try {
            mHttpType = "http://";
            if (endpoint.trim() != "") {
                mEndPoint = endpoint;
            } else {
                throw new NullPointerException("endpoint is null");
            }

            if (mEndPoint.startsWith("http://")) {
                mEndPoint = mEndPoint.substring(7);
            } else if (mEndPoint.startsWith("https://")) {
                mEndPoint = mEndPoint.substring(8);
                mHttpType = "https://";
            }

            while (mEndPoint.endsWith("/")) {
                mEndPoint = mEndPoint.substring(0, mEndPoint.length() - 1);
            }

            this.endpointURI = new URI(mHttpType + mEndPoint);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Endpoint must be a string like 'http://cn-****.log.aliyuncs.com'," +
                    "or your cname like 'http://image.cnamedomain.com'!");
        }

        if (credentialProvider == null) {
            throw new IllegalArgumentException("CredentialProvider can't be null.");
        }

        if (context == null) {
            throw new IllegalArgumentException("context can't be null.");
        }

        if (conf != null) {
            this.cachable = conf.getCachable();
            this.policy = conf.getConnectType();
        }

        requestOperation = new RequestOperation(endpointURI, credentialProvider, (conf == null ? ClientConfiguration.getDefaultConf() : conf));
        cacheManager = new CacheManager(this);
        this.context = context;
        SLSDatabaseManager.getInstance().setupDB(context);

        callbackImp = new CompletedCallback<PostLogRequest, PostLogResult>() {
            @Override
            public void onSuccess(PostLogRequest request, PostLogResult result) {
                CompletedCallback<PostLogRequest, PostLogResult> callback = mCompletedCallbacks.get(request);
                if (callback != null) {
                    try {
                        callback.onSuccess(request, result);
                    } catch (Exception ignore) {
                        // The callback throws the exception, ignore it
                    }
                }
            }

            @Override
            public void onFailure(PostLogRequest request, LogException exception) {

                if (cachable) {
                    LogEntity item = new LogEntity();
                    item.setProject(request.mProject);
                    item.setStore(request.mLogStoreName);
                    item.setEndPoint(mEndPoint);
                    item.setJsonString(request.mLogGroup.LogGroupToJsonString());
                    item.setTimestamp(new Long(new Date().getTime()));
                    SLSDatabaseManager.getInstance().insertRecordIntoDB(item);
                }

                CompletedCallback<PostLogRequest, PostLogResult> callback = mCompletedCallbacks.get(request);
                if (callback != null) {
                    try {
                        callback.onFailure(request, exception);
                    } catch (Exception ignore) {
                        // The callback throws the exception, ignore it
                    }
                }
            }
        };

    }

    public AsyncTask<PostLogResult> asyncPostLog(PostLogRequest request, CompletedCallback<PostLogRequest, PostLogResult> completedCallback)
            throws LogException {

        mCompletedCallbacks.put(request, completedCallback);

        return requestOperation.postLog(request, callbackImp);
    }

    public AsyncTask<PostCachedLogResult> asyncPostCachedLog(PostCachedLogRequest request, final CompletedCallback<PostCachedLogRequest, PostCachedLogResult> completedCallback)
            throws LogException {
        return requestOperation.postCachedLog(request, completedCallback);
    }


    public void SetToken(String token) {
        mAccessToken = token;
    }

    public String GetEndPoint() {
        return mEndPoint;
    }

    public ClientConfiguration.NetworkPolicy getPolicy() {
        return policy;
    }

    public Context getContext() { return context; }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        Log.d("SLS SDK", "LOGClient finalize");
    }
    //=================================================
}
