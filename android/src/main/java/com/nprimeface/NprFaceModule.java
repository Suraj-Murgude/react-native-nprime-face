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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;


import in.nprime.injisdk.FaceLibActivity;
import in.nprime.injisdk.dto.CaptureRequest;
import in.nprime.injisdk.dto.CaptureResponse;
import in.nprime.injisdk.dto.InitRequest;
import in.nprime.injisdk.dto.InitResponse;
import in.nprime.injisdk.dto.SdkRequest;
import in.nprime.injisdk.dto.SdkResponse;
import in.nprime.injisdk.Constants.CaptureMode;
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

    // --- OVERLOADED CONFIGURE METHODS TO PREVENT CRASH ---

    @ReactMethod
    public void configure(String arg1, String arg2, String arg3, String arg4, Promise promise) {
        Log.d("NPR_JAVA_SHIELD", "Received 4 strings from Inji. Initializing...");
        handleInitialization(promise);
    }

    @ReactMethod
    public void configure(String arg1, String arg2, String arg3, Promise promise) {
        Log.d("NPR_JAVA_SHIELD", "Received 3 strings from Inji. Initializing...");
        handleInitialization(promise);
    }

    @ReactMethod
    public void configure(String arg1, String arg2, Promise promise) {
        Log.d("NPR_JAVA_SHIELD", "Received 2 strings from Inji. Initializing...");
        handleInitialization(promise);
    }

    @ReactMethod
    public void configure(Promise promise) {
        Log.d("NPR_JAVA_SHIELD", "Received only Promise. Initializing...");
        handleInitialization(promise);
    }

    /**
     * Helper method to execute the actual Intent logic
     */
    private void handleInitialization(Promise promise) {
        try {
            this.initPromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                initPromise.reject("Activity not found", "Cannot find current activity");
                this.initPromise = null;
                return;
            }

            InitRequest initRequest = new InitRequest();
            SdkRequest<InitRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(initRequest);
            sdkRequest.setTimestamp("");

            Intent initIntent = new Intent(currentActivity, FaceLibActivity.class);
            initIntent.setAction("in.face.lib.init");

            initIntent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
            currentActivity.startActivityForResult(initIntent, INIT_REQUEST_CODE);

        } catch (JsonProcessingException e) {
            initPromise.reject("Intent setup error", e.getMessage());
            this.initPromise = null;
        }
    }

    // --- REST OF MODULE ---

    @ReactMethod
    public void captureFace(boolean cameraSwitch, boolean livenessSwitch, int cameraMode, Promise promise) {
        try {
            this.capturePromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                capturePromise.reject("Activity not found", "Cannot find current activity");
                this.capturePromise = null;
                return;
            }

            CaptureRequest captureRequest = new CaptureRequest();
            if (cameraMode == 1) {
                captureRequest.setCaptureMode(CaptureMode.GUIDED_CAPTURE);
            } else if (cameraMode == 0) {
                captureRequest.setCaptureMode(CaptureMode.SIMPLE_CAPTURE);
            }
            if (cameraSwitch) {
                captureRequest.setCameraId("0");
            } else {
                captureRequest.setCameraId("1");
            }
            captureRequest.setLivenessCheck(livenessSwitch);
            SdkRequest<CaptureRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(captureRequest);
            sdkRequest.setTimestamp("");

            Intent captureIntent = new Intent(currentActivity, FaceLibActivity.class);
            captureIntent.setAction("in.face.lib.capture");

            captureIntent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
            currentActivity.startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE);

        } catch (JsonProcessingException e) {
            capturePromise.reject("Intent setup error", e.getMessage());
            this.capturePromise = null;
        }
    }

    @ReactMethod
    public void generateAndIdentifyTemplates(String capturedTemplate, String vcImageData, Promise promise) {
        try {
            this.generateAndIdentifyPromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                generateAndIdentifyPromise.reject("Activity not found", "Cannot find current activity");
                this.generateAndIdentifyPromise = null;
                return;
            }
            GenerateAndIdentifyTemplateRequest request = new GenerateAndIdentifyTemplateRequest();
            request.setTrustLevel("Low");
            request.setCapturedTemplateData(capturedTemplate);
            request.setVcImageData(vcImageData);

            SdkRequest<GenerateAndIdentifyTemplateRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(request);
            sdkRequest.setTimestamp("");

            Intent intent = new Intent(currentActivity, FaceLibActivity.class);
            intent.setAction("in.face.lib.generateAndIdentifyTemplates");

            intent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
            currentActivity.startActivityForResult(intent, GENERATE_AND_IDENTIFY_REQUEST_CODE);

        } catch (JsonProcessingException e) {
            generateAndIdentifyPromise.reject("Intent setup error", e.getMessage());
            this.generateAndIdentifyPromise = null;
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_REQUEST_CODE) {
            if (capturePromise == null) return;

            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] sdkResponseBytes = data.getByteArrayExtra("response");
                    if (null != sdkResponseBytes) {
                        SdkResponse<CaptureResponse> sdkResponse = new ObjectMapper()
                                .readValue(sdkResponseBytes, new TypeReference<SdkResponse<CaptureResponse>>() {});

                        if (1000 == sdkResponse.getSdkError().getErrorCode()) {
                            CaptureResponse captureResponse = sdkResponse.getResponse();
                            byte[] captureTemplate = captureResponse.getBioRecord().getTemplate();

                            String encodedCapturedTemplate = Base64.encodeToString(captureTemplate, Base64.DEFAULT);
                            capturePromise.resolve(encodedCapturedTemplate);
                            Toast.makeText(reactContext, "Capture Successful", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(reactContext, "" + sdkResponse.getSdkError().getErrorDescription(), Toast.LENGTH_SHORT).show();
                            capturePromise.resolve("");
                        }
                    } else {
                        Toast.makeText(reactContext, "no response found", Toast.LENGTH_SHORT).show();
                        capturePromise.resolve("");
                    }
                } else {
                    Toast.makeText(reactContext, "Capture Cancelled", Toast.LENGTH_SHORT).show();
                    capturePromise.resolve("");
                }
            } catch (Exception e) {
                e.printStackTrace();
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

                    InitResponse initresponse = response.getResponse();
                    if (initresponse != null && initresponse.isInitSuccessful()) {
                        Toast.makeText(reactContext, "SDK INITIALIZED", Toast.LENGTH_SHORT).show();
                        initPromise.resolve(true);
                    } else {
                        Toast.makeText(reactContext, response.getSdkError().getErrorDescription(), Toast.LENGTH_SHORT).show();
                        initPromise.resolve(false);
                    }
                } else {
                    Toast.makeText(reactContext, "Initialization cancelled", Toast.LENGTH_SHORT).show();
                    initPromise.resolve(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                initPromise.resolve(false);
            } finally {
                initPromise = null;
            }

        } else if (requestCode == GENERATE_AND_IDENTIFY_REQUEST_CODE) {
            if (generateAndIdentifyPromise == null) return;

            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    byte[] sdkResponseBytes = data.getByteArrayExtra("response");
                    if (null != sdkResponseBytes) {
                        SdkResponse<GenerateAndIdentifyTemplateResponse> sdkResponse = new ObjectMapper()
                                .readValue(sdkResponseBytes, new TypeReference<SdkResponse<GenerateAndIdentifyTemplateResponse>>() {});

                        GenerateAndIdentifyTemplateResponse response = sdkResponse.getResponse();
                        boolean match = (response != null) && response.isMatchSuccessful();

                        if (1002 == sdkResponse.getSdkError().getErrorCode()) {
                            Toast.makeText(reactContext, "Match Successful", Toast.LENGTH_SHORT).show();
                            generateAndIdentifyPromise.resolve(match);
                        } else if (-1003 == sdkResponse.getSdkError().getErrorCode() || -1004 == sdkResponse.getSdkError().getErrorCode()) {
                            Toast.makeText(reactContext, "Match Failed", Toast.LENGTH_SHORT).show();
                            generateAndIdentifyPromise.resolve(match);
                        } else {
                            Toast.makeText(reactContext, sdkResponse.getSdkError().getErrorDescription(), Toast.LENGTH_SHORT).show();
                            generateAndIdentifyPromise.resolve(false);
                        }
                    } else {
                        generateAndIdentifyPromise.resolve(false);
                    }
                } else {
                    Toast.makeText(reactContext, "Match Cancelled", Toast.LENGTH_SHORT).show();
                    generateAndIdentifyPromise.resolve(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                generateAndIdentifyPromise.resolve(false);
            } finally {
                generateAndIdentifyPromise = null;
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // Not needed
    }
}
