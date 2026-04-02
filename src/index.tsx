import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-nprime-face' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// Safe native module access
const NprFaceModule = NativeModules.NprFaceModule
  ? NativeModules.NprFaceModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

// Prevents the  multiple SDK initializations
let isInitialized = false;

//  Init helper
async function ensureInit(): Promise<boolean> {
  try {
    if (isInitialized) return true;

    const result = await NprFaceModule.configure();

    if (result) {
      isInitialized = true;
      console.info('NPrime: SDK initialized successfully');
    } else {
      console.warn('NPrime: SDK initialization failed');
    }

    return result;
  } catch (e) {
    console.error('NPrime init error', e);
    return false;
  }
}

/**
 *  FINAL FACE COMPARE FUNCTION
 */
export async function faceCompare(
  rearCamera: boolean, // kept for compatibility (ignored)
  liveness: boolean,
  vcImage: string
): Promise<boolean> {
  try {
    // 🔹 STEP 0: Initialize SDK (only once)
    const init = await ensureInit();

    if (!init) {
      console.error('NPrime: Initialization failed');
      return false;
    }

    // 🔹 STEP 1: Capture Face (force FRONT camera)
    const capturedTemplate = await NprFaceModule.captureFace(
      false,        // force front camera
      liveness,
      1             // GUIDED_CAPTURE
    );

    if (!capturedTemplate || capturedTemplate === '') {
      console.error('NPrime: Face capture failed or cancelled');
      return false;
    }

    // 🔹 STEP 2: Match with VC image
    const matchResult = await NprFaceModule.generateAndIdentifyTemplates(
      capturedTemplate,
      vcImage
    );

    if (matchResult) {
      console.info('NPrime: Face match successful');
    } else {
      console.warn('NPrime: Face match failed');
    }

    return matchResult;

  } catch (e) {
    console.error('NPrime: Face comparison error', e);
    return false;
  }
}
