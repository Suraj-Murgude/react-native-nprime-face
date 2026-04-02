package com.nprimeface;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray; 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import in.nprime.injisdk.FaceLibActivity;
import in.nprime.injisdk.dto.CaptureRequest;
import in.nprime.injisdk.dto.CaptureResponse;
import in.nprime.injisdk.dto.InitResponse;
import in.nprime.injisdk.Constants.CaptureMode;
import in.nprime.injisdk.dto.SdkResponse;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateRequest;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateResponse;

public class NprFaceModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    public static final String NAME = "NprFaceModule";
    private static final int CAPTURE_REQUEST_CODE = 101;
    private static final int INIT_REQUEST_CODE = 102;
    private static final int GENERATE_AND_IDENTIFY_REQUEST_CODE = 103;

    private Promise capturePromise;
    private Promise initPromise;
    private Promise generateAndIdentifyPromise;
    private final ReactApplicationContext reactContext;

    public NprFaceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "NprFaceModule";
    }

    // --- 🟢 FIXED CONFIGURE METHOD: Catch-all for any number of arguments ---
    @ReactMethod
    public void configure(ReadableArray args, Promise promise) {
        Log.d("NPR_JAVA_SHIELD", "Received configure call with " + args.size() + " arguments. Initializing...");
        handleInitialization(promise);
    }

    private void handleInitialization(Promise promise) {
        try {
            this.initPromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                if (initPromise != null) initPromise.reject("Activity not found", "Cannot find current activity");
                return;
            }

            // Raw JSON to avoid "cannot find symbol SdkRequest/InitRequest"
            String initJson = "{\"request\":{},\"timestamp\":\"\"}";
            byte[] inputData = initJson.getBytes("UTF-8");

            Intent initIntent = new Intent(currentActivity, FaceLibActivity.class);
            initIntent.setAction("in.face.lib.init");
            initIntent.putExtra("input", inputData);

            currentActivity.startActivityForResult(initIntent, INIT_REQUEST_CODE);

        } catch (Exception e) {
            if (initPromise != null) {
                initPromise.reject("Intent setup error", e.getMessage());
                this.initPromise = null;
            }
        }
    }

    @ReactMethod
    public void captureFace(boolean cameraSwitch, boolean livenessSwitch, int cameraMode, Promise promise) {
        try {
            this.capturePromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                if (capturePromise != null) capturePromise.resolve("");
                return;
            }

            // Manual JSON construction to bypass DTO class issues
            String mode = (cameraMode == 1) ? "GUIDED_CAPTURE" : "SIMPLE_CAPTURE";
            String camId = cameraSwitch ? "0" : "1";
            
            String captureJson = "{\"request\":{\"captureMode\":\"" + mode + "\",\"cameraId\":\"" + camId + "\",\"livenessCheck\":" + livenessSwitch + "},\"timestamp\":\"\"}";

            Intent captureIntent = new Intent(currentActivity, FaceLibActivity.class);
            captureIntent.setAction("in.face.lib.capture");
            captureIntent.putExtra("input", captureJson.getBytes("UTF-8"));
            currentActivity.startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE);

        } catch (Exception e) {
            if (capturePromise != null) capturePromise.resolve("");
        }
    }

    @ReactMethod
    public void generateAndIdentifyTemplates(String capturedTemplate, String vcImageData, Promise promise) {
        try {
            this.generateAndIdentifyPromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                if (generateAndIdentifyPromise != null) generateAndIdentifyPromise.resolve(false);
                return;
            }
            
            String identifyJson = "{\"request\":{\"trustLevel\":\"Low\",\"capturedTemplateData\":\"" + capturedTemplate + "\",\"vcImageData\":\"" + vcImageData + "\"},\"timestamp\":\"\"}";

            Intent intent = new Intent(currentActivity, FaceLibActivity.class);
            intent.setAction("in.face.lib.generateAndIdentifyTemplates");
            intent.putExtra("input", identifyJson.getBytes("UTF-8"));
            currentActivity.startActivityForResult(intent, GENERATE_AND_IDENTIFY_REQUEST_CODE);

        } catch (Exception e) {
            if (generateAndIdentifyPromise != null) generateAndIdentifyPromise.resolve(false);
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_REQUEST_CODE) {
            if (capturePromise == null) return;
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] sdkResponseBytes = data.getByteArrayExtra("response");
                    if (sdkResponseBytes != null) {
                        SdkResponse<CaptureResponse> sdkResponse = new ObjectMapper()
                                .readValue(sdkResponseBytes, new TypeReference<SdkResponse<CaptureResponse>>() {});

                        if (sdkResponse.getSdkError() != null && 1000 == sdkResponse.getSdkError().getErrorCode()) {
                            byte[] captureTemplate = sdkResponse.getResponse().getBioRecord().getTemplate();
                            String encodedTemplate = Base64.encodeToString(captureTemplate, Base64.NO_WRAP);
                            capturePromise.resolve(encodedTemplate);
                        } else {
                            capturePromise.resolve("");
                        }
                    } else {
                        capturePromise.resolve("");
                    }
                } else {
                    capturePromise.resolve("");
                }
            } catch (Exception e) {
                Log.e("NPR_ERROR", "Capture Error", e);
                capturePromise.resolve("");
            } finally {
                capturePromise = null;
            }

        } else if (requestCode == INIT_REQUEST_CODE) {
            if (initPromise == null) return;
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] sdkResponseBytes = data.getByteArrayExtra("response");
                    SdkResponse<InitResponse> response = new ObjectMapper()
                            .readValue(sdkResponseBytes, new TypeReference<SdkResponse<InitResponse>>() {});

                    boolean success = response.getResponse() != null && response.getResponse().isInitSuccessful();
                    if (success) Toast.makeText(reactContext, "SDK INITIALIZED", Toast.LENGTH_SHORT).show();
                    initPromise.resolve(success);
                } else {
                    initPromise.resolve(false);
                }
            } catch (Exception e) {
                initPromise.resolve(false);
            } finally {
                initPromise = null;
            }

        } else if (requestCode == GENERATE_AND_IDENTIFY_REQUEST_CODE) {
            if (generateAndIdentifyPromise == null) return;
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] sdkResponseBytes = data.getByteArrayExtra("response");
                    if (sdkResponseBytes != null) {
                        SdkResponse<GenerateAndIdentifyTemplateResponse> sdkResponse = new ObjectMapper()
                                .readValue(sdkResponseBytes, new TypeReference<SdkResponse<GenerateAndIdentifyTemplateResponse>>() {});

                        boolean match = sdkResponse.getResponse() != null && sdkResponse.getResponse().isMatchSuccessful();
                        generateAndIdentifyPromise.resolve(match);
                    } else {
                        generateAndIdentifyPromise.resolve(false);
                    }
                } else {
                    generateAndIdentifyPromise.resolve(false);
                }
            } catch (Exception e) {
                generateAndIdentifyPromise.resolve(false);
            } finally {
                generateAndIdentifyPromise = null;
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {}
}
