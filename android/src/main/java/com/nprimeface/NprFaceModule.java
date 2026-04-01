package com.nprimeface;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;
import androidx.annotation.NonNull;
import android.util.Log;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import in.nprime.injisdk.dto.CaptureRequest;
import in.nprime.injisdk.dto.CaptureResponse;
import in.nprime.injisdk.FaceLibActivity;
import in.nprime.injisdk.dto.SdkRequest;
import in.nprime.injisdk.dto.SdkResponse;
import in.nprime.injisdk.Constants.CaptureMode;
import in.nprime.injisdk.dto.InitResponse;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateRequest;
import in.nprime.injisdk.dto.GenerateAndIdentifyTemplateResponse;

public class NprFaceModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static ReactApplicationContext reactContext;

    private static final int INIT_REQUEST_CODE = 1000;
    private static final int CAPTURE_REQUEST_CODE = 1001;
    private static final int GENERATE_AND_IDENTIFY_REQUEST_CODE = 1002;

    // 🟢 PRODUCTION CREDENTIALS
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
        Log.d("NPR_JAVA", "Configuring SDK with hardcoded credentials...");
        handleInitialization(promise);
    }

    private void handleInitialization(Promise promise) {
        try {
            this.initPromise = promise;
            Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                if (initPromise != null) initPromise.reject("Activity not found", "Cannot find activity");
                return;
            }

            // 🟢 SILENT PAYLOAD WITH TIMESTAMP
            String jsonInput = "{\"request\":{\"licenseCode\":\"" + LICENSE_CODE + "\",\"customerRef\":\"" + CUSTOMER_REF + "\"},\"timestamp\":\"" + System.currentTimeMillis() + "\"}";
            byte[] jsonBytes = jsonInput.getBytes("UTF-8");

            // Base64 fallback for certain SDK versions
            String base64Input = Base64.encodeToString(jsonBytes, Base64.NO_WRAP);

            Intent initIntent = new Intent(currentActivity, FaceLibActivity.class);
            initIntent.setAction("in.face.lib.init");

            // Pass both to ensure the SDK finds the keys
            initIntent.putExtra("input", jsonBytes);
            initIntent.putExtra("inputString", base64Input);

            currentActivity.startActivityForResult(initIntent, INIT_REQUEST_CODE);
        } catch (Exception e) {
            if (initPromise != null) initPromise.resolve(false);
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

            // 🟢 FORCE SILENT RE-AUTH FOR SHARING FLOW
            String jsonInputInit = "{\"request\":{\"licenseCode\":\"" + LICENSE_CODE + "\",\"customerRef\":\"" + CUSTOMER_REF + "\"},\"timestamp\":\"" + System.currentTimeMillis() + "\"}";
            byte[] initBytes = jsonInputInit.getBytes("UTF-8");
            String base64Init = Base64.encodeToString(initBytes, Base64.NO_WRAP);

            Intent silentInit = new Intent(currentActivity, FaceLibActivity.class);
            silentInit.setAction("in.face.lib.init");
            silentInit.putExtra("input", initBytes);
            silentInit.putExtra("inputString", base64Init);
            silentInit.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            currentActivity.startActivity(silentInit);

            // Prepare Capture Request
            CaptureRequest captureRequest = new CaptureRequest();
            captureRequest.setCaptureMode(cameraMode == 1 ? CaptureMode.GUIDED_CAPTURE : CaptureMode.SIMPLE_CAPTURE);
            captureRequest.setCameraId(cameraSwitch ? "0" : "1");
            captureRequest.setLivenessCheck(livenessSwitch);

            SdkRequest<CaptureRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(captureRequest);
            sdkRequest.setTimestamp("" + System.currentTimeMillis());

            Intent captureIntent = new Intent(currentActivity, FaceLibActivity.class);
            captureIntent.setAction("in.face.lib.capture");
            captureIntent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
            
            // Passing license_input extra as a secondary bypass for older SDK logic
            captureIntent.putExtra("license_input", initBytes); 

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
                if (generateAndIdentifyPromise != null) generateAndIdentifyPromise.reject("Activity not found", "Cannot find activity");
                return;
            }
            
            GenerateAndIdentifyTemplateRequest request = new GenerateAndIdentifyTemplateRequest();
            request.setTrustLevel("Low");
            request.setCapturedTemplateData(capturedTemplate);
            request.setVcImageData(vcImageData);

            SdkRequest<GenerateAndIdentifyTemplateRequest> sdkRequest = new SdkRequest<>();
            sdkRequest.setRequest(request);
            sdkRequest.setTimestamp("" + System.currentTimeMillis());

            Intent intent = new Intent(currentActivity, FaceLibActivity.class);
            intent.setAction("in.face.lib.generateAndIdentifyTemplates");
            intent.putExtra("input", new ObjectMapper().writeValueAsBytes(sdkRequest));
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
                            // NO_WRAP prevents bridge breakage
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
