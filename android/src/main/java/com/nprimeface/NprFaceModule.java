package com.nprimeface;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.nprime.injisdk.FaceLibActivity;
import in.nprime.injisdk.dto.CaptureResponse;
import in.nprime.injisdk.dto.SdkResponse;
import in.nprime.injisdk.dto.InitResponse;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateResponse;

public class NprFaceModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int INIT_REQUEST_CODE = 1000;
    private static final int CAPTURE_REQUEST_CODE = 1001;
    private static final int MATCH_REQUEST_CODE = 1002;

    private Promise initPromise;
    private Promise capturePromise;
    private Promise matchPromise;

    private final ReactApplicationContext reactContext;

    public NprFaceModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
        context.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "NprFaceModule";
    }

    // ✅ EXACT SDK METHOD
    @ReactMethod
    public void configure(Promise promise) {
        try {
            this.initPromise = promise;
            Activity activity = getCurrentActivity();

            if (activity == null) {
                promise.reject("NO_ACTIVITY", "Activity not found");
                return;
            }

            Intent intent = new Intent(activity, FaceLibActivity.class);
            intent.setAction("in.face.lib.init");

            activity.startActivityForResult(intent, INIT_REQUEST_CODE);

        } catch (Exception e) {
            promise.reject("INIT_ERROR", e.getMessage());
        }
    }

    // ✅ STEP 1: CAPTURE
    @ReactMethod
    public void captureFace(boolean cameraSwitch, boolean liveness, int mode, Promise promise) {
        try {
            this.capturePromise = promise;
            Activity activity = getCurrentActivity();

            if (activity == null) {
                promise.resolve("");
                return;
            }

            String captureJson = "{"
                    + "\"request\":{"
                    + "\"captureMode\":\"" + (mode == 1 ? "GUIDED_CAPTURE" : "SIMPLE_CAPTURE") + "\","
                    + "\"cameraId\":\"" + (cameraSwitch ? "0" : "1") + "\","
                    + "\"livenessCheck\":" + liveness
                    + "},"
                    + "\"timestamp\":\"\""
                    + "}";

            Intent intent = new Intent(activity, FaceLibActivity.class);
            intent.setAction("in.face.lib.capture");
            intent.putExtra("input", captureJson.getBytes("UTF-8"));

            activity.startActivityForResult(intent, CAPTURE_REQUEST_CODE);

        } catch (Exception e) {
            promise.resolve("");
        }
    }

    // ✅ STEP 2: MATCH
    @ReactMethod
    public void generateAndIdentifyTemplates(String template, String vcImage, Promise promise) {
        try {
            this.matchPromise = promise;
            Activity activity = getCurrentActivity();

            if (activity == null) {
                promise.resolve(false);
                return;
            }

            String json = "{"
                    + "\"request\":{"
                    + "\"trustLevel\":\"Low\","
                    + "\"capturedTemplateData\":\"" + template + "\","
                    + "\"vcImageData\":\"" + vcImage + "\""
                    + "},"
                    + "\"timestamp\":\"\""
                    + "}";

            Intent intent = new Intent(activity, FaceLibActivity.class);
            intent.setAction("in.face.lib.generateAndIdentifyTemplates");
            intent.putExtra("input", json.getBytes("UTF-8"));

            activity.startActivityForResult(intent, MATCH_REQUEST_CODE);

        } catch (Exception e) {
            promise.resolve(false);
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

        try {
            if (requestCode == INIT_REQUEST_CODE && initPromise != null) {

                boolean success = false;

                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] res = data.getByteArrayExtra("response");

                    SdkResponse<InitResponse> response = new ObjectMapper()
                            .readValue(res, new TypeReference<SdkResponse<InitResponse>>() {});

                    success = response.getResponse() != null &&
                              response.getResponse().isInitSuccessful();
                }

                initPromise.resolve(success);
                initPromise = null;
            }

            else if (requestCode == CAPTURE_REQUEST_CODE && capturePromise != null) {

                String result = "";

                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] res = data.getByteArrayExtra("response");

                    SdkResponse<CaptureResponse> response = new ObjectMapper()
                            .readValue(res, new TypeReference<SdkResponse<CaptureResponse>>() {});

                    if (response.getResponse() != null) {
                        byte[] template = response.getResponse().getBioRecord().getTemplate();
                        result = Base64.encodeToString(template, Base64.NO_WRAP);
                    }
                }

                capturePromise.resolve(result);
                capturePromise = null;
            }

            else if (requestCode == MATCH_REQUEST_CODE && matchPromise != null) {

                boolean match = false;

                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] res = data.getByteArrayExtra("response");

                    SdkResponse<GenerateAndIdentifyTemplateResponse> response = new ObjectMapper()
                            .readValue(res, new TypeReference<SdkResponse<GenerateAndIdentifyTemplateResponse>>() {});

                    match = response.getResponse() != null &&
                            response.getResponse().isMatchSuccessful();
                }

                matchPromise.resolve(match);
                matchPromise = null;
            }

        } catch (Exception e) {
            if (capturePromise != null) capturePromise.resolve("");
            if (matchPromise != null) matchPromise.resolve(false);
            if (initPromise != null) initPromise.resolve(false);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {}
}
