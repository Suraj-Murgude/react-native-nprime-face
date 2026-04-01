package com.nprimeface;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import android.util.Base64;
import androidx.annotation.NonNull;
import android.util.Log;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import in.nprime.injisdk.dto.CaptureRequest;
import in.nprime.injisdk.dto.CaptureResponse;
import in.nprime.injisdk.FaceLibActivity;
import in.nprime.injisdk.dto.SdkRequest;
import in.nprime.injisdk.dto.SdkResponse;
import in.nprime.injisdk.Constants.CaptureMode;
import in.nprime.injisdk.dto.InitRequest; 
import in.nprime.injisdk.dto.InitResponse;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateRequest;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateResponse;

public class NprFaceModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static ReactApplicationContext reactContext;

    private static final int INIT_REQUEST_CODE = 1000;
    private static final int CAPTURE_REQUEST_CODE = 1001;
    private static final int GENERATE_AND_IDENTIFY_REQUEST_CODE = 1002;

    //  HARDCODED CREDENTIALS
    private static final String LICENSE_CODE = "NPRIMEINJI-48279";
    private static final String CUSTOMER_REF = "MOSIPMECB";

    private Promise capturePromise;
    private Promise initPromise;
    private Promise generateAndIdentifyPromise;

    NprFaceModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
        reactContext.addActivityEventListener(this);
    }

    @NonNull
    @Override
    public String getName() {
        return "NprFaceModule";
    }

    @ReactMethod
    public void configure(Promise promise) {
        Log.d("NPR_JAVA_SHIELD", "Received configure call. Initializing with hardcoded keys...");
        handleInitialization(promise);
    }

    private void handleInitialization(Promise promise) {
        try {
            this.initPromise = promise;
            Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                if (initPromise != null) {
                    initPromise.reject("Activity not found", "Cannot find current activity");
                    this.initPromise = null;
                }
                return;
            }

            //  Create the Init Request DTO
            InitRequest initRequest = new InitRequest();
            initRequest.setLicenseCode(LICENSE_CODE);
            initRequest.setCustomerRef(CUSTOMER_REF);

            //  Wrap in SdkRequest
            SdkRequest<InitRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(initRequest);
            sdkRequest.setTimestamp("");

            Intent initIntent = new Intent(currentActivity, FaceLibActivity.class);
            initIntent.setAction("in.face.lib.init");
            
            // Passing 'input' here tells the SDK to skip the manual login screen
            initIntent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
            
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
                if (capturePromise != null) {
                    capturePromise.reject("Activity not found", "Cannot find current activity");
                    this.capturePromise = null;
                }
                return;
            }

            CaptureRequest captureRequest = new CaptureRequest();
            // Force Mode 0 (Simple Capture) as requested previously
            captureRequest.setCaptureMode(cameraMode == 1 ? CaptureMode.GUIDED_CAPTURE : CaptureMode.SIMPLE_CAPTURE);
            // Force Front Camera (ID 1)
            captureRequest.setCameraId(cameraSwitch ? "0" : "1");
            captureRequest.setLivenessCheck(livenessSwitch);
            
            SdkRequest<CaptureRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(captureRequest);
            sdkRequest.setTimestamp("");

            Intent captureIntent = new Intent(currentActivity, FaceLibActivity.class);
            captureIntent.setAction("in.face.lib.capture");
            captureIntent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
            currentActivity.startActivityForResult(captureIntent, CAPTURE_REQUEST_CODE);

        } catch (Exception e) {
            if (capturePromise != null) {
                capturePromise.reject("Capture setup error", e.getMessage());
                this.capturePromise = null;
            }
        }
    }

    @ReactMethod
    public void generateAndIdentifyTemplates(String capturedTemplate, String vcImageData, Promise promise) {
        try {
            this.generateAndIdentifyPromise = promise;
            Activity currentActivity = getCurrentActivity();

            if (currentActivity == null) {
                if (generateAndIdentifyPromise != null) {
                    generateAndIdentifyPromise.reject("Activity not found", "Cannot find current activity");
                    this.generateAndIdentifyPromise = null;
                }
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

        } catch (Exception e) {
            if (generateAndIdentifyPromise != null) {
                generateAndIdentifyPromise.reject("Match setup error", e.getMessage());
                this.generateAndIdentifyPromise = null;
            }
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
                            //  NO_WRAP prevents base64 newlines from breaking the bridge
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
                Log.e("NPR_ERROR", "Capture result error", e);
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
