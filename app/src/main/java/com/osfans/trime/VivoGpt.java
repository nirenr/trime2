package com.osfans.trime;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.androlua.AsyncTaskX;
import com.osfans.trime.util.HttpUtil;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VivoGpt {


    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static String TAG = "VivoGpt";
    private static final String appId = BuildConfig.API_ID;
    private static final String appKey = BuildConfig.API_KEY;

    private static String generateRandomString(int len) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }


    private static String generateCanonicalQueryString(String queryParams) throws UnsupportedEncodingException {
        if (queryParams == null || queryParams.length() <= 0) {
            return "";
        }

        HashMap<String, String> params = new HashMap<>();
        String[] param = queryParams.split("&");
        for (String item : param) {
            String[] pair = item.split("=");
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            } else {
                params.put(pair[0], "");
            }
        }
        SortedSet<String> keys = new TreeSet<>(params.keySet());
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                sb.append("&");
            }
            String item = URLEncoder.encode(key) + "=" + URLEncoder.encode(params.get(key));
            sb.append(item);
            first = false;
        }

        return sb.toString();
    }

    private static String generateSignature(String appKey, String signingString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret = new SecretKeySpec(appKey.getBytes(UTF8), mac.getAlgorithm());
            mac.init(secret);
            return android.util.Base64.encodeToString(mac.doFinal(signingString.getBytes()), Base64.DEFAULT);
        } catch (Exception err) {
            Log.w(TAG, "create sign exception", err);
            return "";
        }
    }


    public static HashMap<String, String> generateAuthHeaders(String appId, String appKey, String method, String uri, String queryParams)
            throws UnsupportedEncodingException {
        String nonce = generateRandomString(8);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String canonical_query_string = generateCanonicalQueryString(queryParams);
        String signed_headers_string = String.format("x-ai-gateway-app-id:%s\n" +
                "x-ai-gateway-timestamp:%s\nx-ai-gateway-nonce:%s", appId, timestamp, nonce);
//        System.out.println(signed_headers_string);
        String[] fields = {
                method,
                uri,
                canonical_query_string,
                appId,
                timestamp,
                signed_headers_string
        };
        final StringBuilder buf = new StringBuilder(fields.length * 16);
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                buf.append("\n");
            }
            if (fields[i] != null) {
                buf.append(fields[i]);
            }
        }
//        System.out.println(buf.toString());
        HashMap<String, String> headers = new HashMap<>();
        headers.put("X-AI-GATEWAY-APP-ID", appId.toString());
        headers.put("X-AI-GATEWAY-TIMESTAMP", timestamp.toString());
        headers.put("X-AI-GATEWAY-NONCE", nonce.toString());
        headers.put("X-AI-GATEWAY-SIGNED-HEADERS", "x-ai-gateway-app-id;x-ai-gateway-timestamp;x-ai-gateway-nonce");
        headers.put("X-AI-GATEWAY-SIGNATURE", generateSignature(appKey, buf.toString()));
        return headers;
    }

    public static void gpt(String s, HttpUtil.HttpCallback callback) {
        try {
            vivogpt(s, callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onDone(new HttpUtil.HttpResult(e.toString()));
        }

        /*new AsyncTaskX<String,String,String>(){

            @Override
            protected String doInBackground(String... strings) {
                try {
                    return vivogpt(s);
                } catch (Exception e) {
                    return e.toString();
                }
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                Log.w(TAG, "onPostExecute: "+s );
                try {
                    s=new JSONObject(s).getJSONObject("data").getString("content");
                } catch (JSONException e) {
                }
                callback.onDone(new HttpUtil.HttpResult(s));
            }
        }.execute();*/
    }

    public static String vivogpt(String s, HttpUtil.HttpCallback callback) throws Exception {

        String URI = "/vivogpt/completions/stream";
        String DOMAIN = "api-ai.vivo.com.cn";
        String METHOD = "POST";
        UUID requestId = UUID.randomUUID();
        System.out.println("requestId: " + requestId);
        Log.w("vivogpt", "vivogpt: " + requestId);

        Map<String, Object> map = new HashMap<>();
        map.put("requestId", requestId.toString());
        String queryStr = mapToQueryString(map);

        //构建请求体
        Map<String, Object> data = new HashMap<>();
        data.put("prompt", s);
        ArrayList<Map<String, String>> msg = new ArrayList<>();
        HashMap<String, String> m1 = new HashMap<>();
        m1.put("role", "system");
        m1.put("content", "你的名字是蓝心小v，专为输入法服务，");
        msg.add(m1);
        HashMap<String, String> m = new HashMap<>();
        m.put("role", "user");
        m.put("content", s);
        msg.add(m);
        data.put("messages", msg);
        data.put("model", "BlueLM-Vision-Aid");
        HashMap<Object, Object> extra = new HashMap<>();
        extra.put("enable_thinking", false);
        data.put("extra", extra);
        //data.put("model_version", "2024-05-10");
        UUID sessionId = UUID.randomUUID();
        data.put("sessionId", sessionId.toString());
        System.out.println(sessionId);


        HashMap<String, String> headers = generateAuthHeaders(appId, appKey, METHOD, URI, queryStr);
        headers.put("Content-Type", "application/json");
        System.out.println(headers);
        String url = String.format("https://%s%s?%s", DOMAIN, URI, queryStr);


        OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(okhttp3.MediaType.parse("application/json"), new JSONObject(data).toString())) // 设置请求体和Content-Type
                .headers(Headers.of(headers))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                callback.onDone(new HttpUtil.HttpResult(e.toString()));
                callback.onDone(null);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                BufferedReader stream = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                boolean think=true;
                String line = stream.readLine();
                while (line != null) {
                    try {
                        if (!TextUtils.isEmpty(line)) {
                            if (line.startsWith("event")) {
                                callback.onDone(null);
                                break;
                            }
                            line = line.substring(5);
                            Log.w(TAG, "onResponse:line " + line);
                            String r = new JSONObject(line).getString("message");
                            callback.onDone(new HttpUtil.HttpResult(r));
                        }
                    } catch (JSONException e) {
                        callback.onDone(null);
                        break;
                    }
                    line = stream.readLine();
                }

            }
        });
        /*Response response = client.newCall(request).execute(); // 同步发送请求并执行响应
        if (response.isSuccessful()) {
            return response.body().string(); // 处理响应体数据...
        } else {
            throw new IOException("Unexpected code " + response); // 处理错误情况...
        }*/
        return null;
    }

    public static void gpt1(String s, HttpUtil.HttpCallback callback) {
        new AsyncTaskX<String, String, String>() {

            @Override
            protected String doInBackground(String... strings) {
                try {
                    return vivogpt1(s);
                } catch (Exception e) {
                    return e.toString();
                }
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                Log.w(TAG, "onPostExecute: " + s);
                try {
                    s = new JSONObject(s).getJSONObject("data").getString("content");
                } catch (JSONException e) {
                }
                callback.onDone(new HttpUtil.HttpResult(s));
            }
        }.execute();
    }

    public static String vivogpt1(String s) throws Exception {
        String URI = "/vivogpt/completions";
        String DOMAIN = "api-ai.vivo.com.cn";
        String METHOD = "POST";
        UUID requestId = UUID.randomUUID();
        System.out.println("requestId: " + requestId);
        Log.w("vivogpt", "vivogpt: " + requestId);

        Map<String, Object> map = new HashMap<>();
        map.put("requestId", requestId.toString());
        String queryStr = mapToQueryString(map);

        //构建请求体
        Map<String, Object> data = new HashMap<>();
        data.put("prompt", s);
        ArrayList<Map<String, String>> msg = new ArrayList<>();
        HashMap<String, String> m1 = new HashMap<>();
        m1.put("role", "system");
        m1.put("content", "你的名字是蓝心小v，专为输入法服务，");
        msg.add(m1);
        HashMap<String, String> m = new HashMap<>();
        m.put("role", "user");
        m.put("content", s);
        msg.add(m);
        data.put("messages", msg);

        data.put("model", "BlueLM-Vision-Aid");
        HashMap<Object, Object> extra = new HashMap<>();
        extra.put("enable_thinking", false);
        data.put("extra", extra);
        //data.put("model_version", "2024-05-10");
        UUID sessionId = UUID.randomUUID();
        data.put("sessionId", sessionId.toString());
        System.out.println(sessionId);


        HashMap<String, String> headers = generateAuthHeaders(appId, appKey, METHOD, URI, queryStr);
        headers.put("Content-Type", "application/json");
        System.out.println(headers);
        String url = String.format("https://%s%s?%s", DOMAIN, URI, queryStr);


        OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(okhttp3.MediaType.parse("application/json"), new JSONObject(data).toString())) // 设置请求体和Content-Type
                .headers(Headers.of(headers))
                .build();
        Response response = client.newCall(request).execute(); // 同步发送请求并执行响应
        if (response.isSuccessful()) {
            return response.body().string(); // 处理响应体数据...
        } else {
            throw new IOException("Unexpected code " + response); // 处理错误情况...
        }
    }

    public static void gpt1(ArrayList<String> s, HttpUtil.HttpCallback callback) {
        new AsyncTaskX<String, String, String>() {

            @Override
            protected String doInBackground(String... strings) {
                try {
                    return vivogpt1(s);
                } catch (Exception e) {
                    return e.toString();
                }
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                Log.w(TAG, "onPostExecute: " + s);
                try {
                    s = new JSONObject(s).getJSONObject("data").getString("content");
                } catch (JSONException e) {
                }
                callback.onDone(new HttpUtil.HttpResult(s));
            }
        }.execute();
    }

    public static String vivogpt1(ArrayList<String> s) throws Exception {

        String URI = "/vivogpt/completions";
        String DOMAIN = "api-ai.vivo.com.cn";
        String METHOD = "POST";
        UUID requestId = UUID.randomUUID();
        System.out.println("requestId: " + requestId);
        Log.w("vivogpt", "vivogpt: " + requestId);

        Map<String, Object> map = new HashMap<>();
        map.put("requestId", requestId.toString());
        String queryStr = mapToQueryString(map);

        //构建请求体
        Map<String, Object> data = new HashMap<>();
        ArrayList<Map<String, String>> msg = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            HashMap<String, String> m = new HashMap<>();
            m.put("role", i % 2 == 0 ? "user" : "assistant");
            m.put("content", s.get(i));
            msg.add(m);
        }
        data.put("messages", msg);
        data.put("model", "BlueLM-Vision-Aid");
        HashMap<Object, Object> extra = new HashMap<>();
        extra.put("enable_thinking", false);
        data.put("extra", extra);
        //data.put("model_version", "2024-05-10");
        UUID sessionId = UUID.randomUUID();
        data.put("sessionId", sessionId.toString());
        System.out.println(sessionId);


        HashMap<String, String> headers = generateAuthHeaders(appId, appKey, METHOD, URI, queryStr);
        headers.put("Content-Type", "application/json");
        System.out.println(headers);
        String url = String.format("https://%s%s?%s", DOMAIN, URI, queryStr);


        OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(okhttp3.MediaType.parse("application/json"), new JSONObject(data).toString())) // 设置请求体和Content-Type
                .headers(Headers.of(headers))
                .build();
        Response response = client.newCall(request).execute(); // 同步发送请求并执行响应
        if (response.isSuccessful()) {
            return response.body().string(); // 处理响应体数据...
        } else {
            throw new IOException("Unexpected code " + response); // 处理错误情况...
        }
    }

    public static String mapToQueryString(Map<String, Object> map) {
        if (map.isEmpty()) {
            return "";
        }
        StringBuilder queryStringBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (queryStringBuilder.length() > 0) {
                queryStringBuilder.append("&");
            }
            queryStringBuilder.append(entry.getKey());
            queryStringBuilder.append("=");
            queryStringBuilder.append(entry.getValue());
        }
        return queryStringBuilder.toString();
    }
}
