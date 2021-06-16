package com.chiligarlic.ratatxt;

import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Ratatxt handles requests Ratatxt API server.
 */
public class Ratatxt {
    // API endpoints.
    static final String API_LOGIN = "/auth/login";
    static final String API_RENEW = "/auth/renew";
    static final String API_APPKEYS = "/appkeys";
    static final String API_DEVICES = "/devices";
    static final String API_OUTBOX = "/outbox";
    static final String API_INBOX = "/inbox";
    static final String API_VERSION = "/version";

    static final String TAG = "Ratatxt";

    private static Ratatxt instance;
    private String apiURL;
    private AppKey currentAppKey;
    private Device currentDevice;
    private Auth currentAuth;

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final Moshi MOSHI = new Moshi.Builder().build();

    private Ratatxt() {
        httpClient.dispatcher().setMaxRequests(1);
        Log.d(TAG, "Ratatxt instance created");
    }

    public static synchronized Ratatxt getInstance() {
        if (instance == null) {
            instance = new Ratatxt();
        }
        return instance;
    }

    /** Returns a device topic for subscription on FCM or MQTT broker. */
    public String getDeviceTopic() {
        String userId = "";
        String deviceId = "";
        if (currentAppKey != null) {
            userId = currentAppKey.getUserId();
        }
        if (currentDevice != null) {
            deviceId = currentDevice.getId();
        }

        return String.format("%s/%s", userId, deviceId);
    }

    /** Ratatxt instance sets current API URL. */
    public void setApiHost(String host) { apiURL = host; }

    /** Ratatxt instance sets current app key. */
    public void setAppKey(AppKey appKey) { currentAppKey = appKey; }

    /** Ratatxt instance sets current device in-use. */
    public void setDevice(Device device) { currentDevice = device; }

    // API methods.
    /** Ratatxt method to get user access tokens. */
    public void authenticate(String username, String password, final ObjectListener onResponse, final ErrorListener onError) {
        JSONObject object = new JSONObject();
        try {
            object.put("username", username);
            object.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // App-key creation will give us long-lived access token that will be use until destroyed.
        request(METHOD_POST, API_LOGIN, object, data -> {
            // Sets the authorization for creation of app key request.
            currentAuth = Auth.parse(data);
            createAppToken(onResponse, onError);
        }, onError);
    }

    private void createAppToken(final ObjectListener onResponse, final ErrorListener onError) {
        JSONObject object = new JSONObject();
        try {
            object.put("note", getHardwareName());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        request(METHOD_POST, API_APPKEYS, object, data -> {
            setAppKey(AppKey.parse(data));
            onResponse.onResponse(data);
            // Remove user auth since we don't need anymore.
            currentAuth = null;
        }, onError);
    }

    public void deleteAppToken(String id, final ObjectListener onResponse, final ErrorListener onError) {
        if (id == null || id.isEmpty()) {
            return;
        }

        request(METHOD_DELETE, API_APPKEYS + "/" +id, null, onResponse, onError);
    }

    public String getHardwareName() {
        return Build.BRAND + " " + Build.PRODUCT + " API " + Build.VERSION.SDK_INT;
    }

    public void getDevices(final ArrayListener callback, final ErrorListener callbackError) {
        requestArray(API_DEVICES, callback, callbackError);
    }

    /** Ratatxt method to update outbox message sending status. */
    public void updateOutboxStatus(String id, int status, final ObjectListener onResponse, final ErrorListener onError) {
        JSONObject object = new JSONObject();
        try {
            object.put("status", status);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        request(METHOD_PATCH, API_OUTBOX + "/" + id, object, onResponse, onError);
    }

    /** Ratatxt method to pushes received SMS to inbox. */
    public void pushInbox(String textMessage, String address, long timestamp, final ObjectListener onResponse, final ErrorListener onError) {
        JSONObject object = new JSONObject();
        try {
            object.put("device_id", currentDevice.id);
            object.put("address", address);
            object.put("text", textMessage);
            object.put("timestamp", timestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        request(METHOD_POST, API_INBOX, object, onResponse, onError);
    }

    /** Ratatxt method to fetch current API server version. */
    public void getVersion(final ObjectListener onResponse, final ErrorListener onError) {
        request(METHOD_GET, API_VERSION, null, onResponse, onError);
    }

    /** Ratatxt method to renew access token */
    private void renewAccessToken() {
        JSONObject object = new JSONObject();
        try {
            object.put("refresh_token", currentAuth.refreshToken);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        request(METHOD_POST, API_RENEW + "?t_min=5000", object, auth -> {
            currentAuth = Auth.parse(auth);
        }, null);
    }

    // API objects.
    /** Ratatxt Auth object model. */
    static class Auth {
        @SerializedName("user_id")
        private String userId;

        @SerializedName("token")
        private String accessToken;

        @SerializedName("refresh_token")
        private String refreshToken;

        @SerializedName("expires_at")
        private Date expiresAt;

        static Ratatxt.Auth parse(JSONObject json) {
            return new Gson().fromJson(json.toString(), Ratatxt.Auth.class);
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() { return refreshToken; }

        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public Date getExpiresAt() { return expiresAt; }

        public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

        public void setExpiresAt(long timestamp) {
            Date date = new Date();
            date.setTime(timestamp);
            this.expiresAt = date;
        }

    }

    /** Ratatxt AppKey object model. */
    static class AppKey {
        private String id;

        @SerializedName("user_id")
        private String userId;

        private String token;

        private String note;

        static AppKey parse(JSONObject json) {
            return new Gson().fromJson(json.toString(), AppKey.class);
        }

        public String getId() { return id; }

        public void setId(String id) { this.id = id; }

        public String getUserId() { return userId; }

        public void setUserId(String userId) { this.userId = userId; }

        public String getToken() { return token; }

        public void setToken(String token) { this.token = token; }

        public String getNote() { return note; }

        public void setNote(String note) { this.note = note; }
    }

    static final int MESSAGE_STATUS_OUTBOX_SENDING = 210;
    static final int MESSAGE_STATUS_OUTBOX_SENT = 220;
    static final int MESSAGE_STATUS_OUTBOX_FAILED = 240;
    static final int MESSAGE_STATUS_OUTBOX_ERROR = 250;

    /** Ratatxt Message object model. */
    static class Message {
        private String id;

        @SerializedName("device_id")
        private String deviceId;

        private String address;

        private String text;

        private long timestamp;

        private int status;

        static Ratatxt.Message parse(Map<String, String> map) {
            Ratatxt.Message message = new Ratatxt.Message();
            message.id = map.get("id");
            message.deviceId = map.get("device_id");
            message.address = map.get("address");
            message.text = map.get("text");
            return message;
        }

        static Ratatxt.Message parse(JSONObject json) {
            return new Gson().fromJson(json.toString(), Ratatxt.Message.class);
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    /** Ratatxt Device object model. */
    static class Device {
        private String id;
        private String name;
        private String address;
        private String status;

        static Ratatxt.Device parse(JSONObject json) {
            return new Gson().fromJson(json.toString(), Ratatxt.Device.class);
        }

        static Ratatxt.Device[] parse(JSONArray json) {
            return new Gson().fromJson(json.toString(), Ratatxt.Device[].class);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public String getStatus() {
            return status;
        }

        public void setId(String id) { this.id = id; }

        public void setAddress(String address) { this.address = address; }

        public void setName(String name) { this.name = name; }

        public void setStatus(String status) { this.status = status; }

        public String getLabel() {
            if (name.isEmpty() || address.isEmpty()) {
                return null;
            }

            return String.format("%s -- %s", this.getFormattedAddress(), name);
        }

        public String getFormattedAddress() {
            switch (address.length()) {
                case 12:
                    return String.format("+%s %s %s %s",
                            address.substring(0, 2),
                            address.substring(2,5),
                            address.substring(5, 8),
                            address.substring(8, 12));
                case 11:
                    return String.format("%s %s %s",
                            address.substring(0,4),
                            address.substring(4, 7),
                            address.substring(7, 11));
                default:
                    return address;
            }
        }
    }

    /** Ratatxt Device object model. */
    static class Version {
        private String version;
        private String built;
        private Boolean production;

        static Ratatxt.Version parse(JSONObject json) {
            return new Gson().fromJson(json.toString(), Ratatxt.Version.class);
        }

        public String getVersion() {
            return version;
        }

        public String getBuilt() {
            return built;
        }
    }

    /** Ratatxt Error response object model. */
    static class ErrResponse {
        private boolean error;
        private String type;
        private String message;

        static ErrResponse from(ResponseBody body) throws IOException {
            JsonAdapter<ErrResponse> jsonAdapter = MOSHI.adapter(ErrResponse.class);
            return jsonAdapter.fromJson(body.source());
        }

        static ErrResponse from(IOException error) {
            ErrResponse errorResp = new ErrResponse();
            errorResp.type = "APP::IOException";
            errorResp.message = error.getMessage();
            return errorResp;
        }

        static ErrResponse from(JSONException error) {
            ErrResponse errorResp = new ErrResponse();
            errorResp.type = "APP::JSONException";
            errorResp.message = error.getMessage();
            return errorResp;
        }

        public String getMessage() { return message; }

        public String getType() { return type; }

        public boolean isError() { return error; }

        public void setMessage(String msg) { message = msg; }
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final int METHOD_GET = 1;
    private static final int METHOD_POST = 2;
    private static final int METHOD_PATCH = 3;
    private static final int METHOD_DELETE = 4;

    private HashMap<String, String> getRequestHeaders() {
        HashMap<String, String> headers = new HashMap<>();

        if (currentAuth != null && currentAuth.getAccessToken() != null) {
            headers.put("Authorization", "Bearer " + currentAuth.getAccessToken());
        } else if (currentAppKey != null && currentAppKey.getToken() != null) {
            headers.put("Authorization", Credentials.basic(currentAppKey.getToken(), ""));
        }

        return headers;
    }

    private void request(int method, String endpoint, JSONObject data, ObjectListener onResponse, ErrorListener onError) {
        Log.d(TAG, "request: "+ endpoint);

        Request.Builder request = new Request.Builder()
                .url(apiURL + endpoint);

        // Set authorization headers.
        for (Map.Entry<String, String> set : getRequestHeaders().entrySet()) {
            request.addHeader(set.getKey(), set.getValue());
        }

        Log.d(TAG, String.format("request: %d %s %s", method, endpoint, data));

        switch (method) {
            case METHOD_POST:
                request.post(RequestBody.create(JSON, data.toString()));
                break;
            case METHOD_PATCH:
                request.patch(RequestBody.create(JSON, data.toString()));
                break;
            case METHOD_DELETE:
                request.delete();
                break;
        }

        httpClient.newCall(request.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                onError.onErrorResponse(ErrResponse.from(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.d(TAG, String.format("request: %d %s %s %d", method, endpoint, data, response.code()));
                if (!response.isSuccessful()) {
                    onError.onErrorResponse(ErrResponse.from(response.body()));
                    return;
                }

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    onResponse.onResponse(json);
                } catch (JSONException e) {
                    onError.onErrorResponse(ErrResponse.from(e));
                }
            }
        });
    }

    private void requestArray(String endpoint, ArrayListener onResponse, ErrorListener onError) {
        request(METHOD_GET, endpoint, null, data -> {
            try {
                onResponse.onResponse(data.getJSONArray("data"));
            } catch (JSONException e) {
                ErrResponse err = new ErrResponse();
                err.message = "could not parse list with metadata";
                onError.onErrorResponse(err);
                e.printStackTrace();
            }
        }, onError);
    }

    /** Callback interface for listening success object responses. */
    public interface ObjectListener {
        void onResponse(JSONObject object);
    }

    /** Callback interface for listening success array responses. */
    public interface ArrayListener {
        void onResponse(JSONArray list);
    }

    /** Callback interface for listening error responses. */
    public interface ErrorListener {
        void onErrorResponse(ErrResponse errResp);
    }
}