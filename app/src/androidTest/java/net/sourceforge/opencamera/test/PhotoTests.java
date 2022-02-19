package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class PhotoTests {
    // Tests related to taking photos; note that tests to do with photo mode that don't take photos are still part of MainTests
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());
        // put these tests first as they require various permissions be allowed, that can only be set by user action
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoSAF"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationOn"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationDirectionOn"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationOff"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationOnSAF"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testDirectionOn"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testDirectionOnSAF"));
        }
        // then do memory intensive tests:
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelLowMemory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAngles"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAnglesLowMemory"));
        // other tests:
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuous"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoContinuousNoTouch"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashAuto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashOn"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashTorch"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAudioButton"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNoAutofocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoNoThumbnail"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFlashBug"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraAll"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraMulti"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoFrontCameraScreenFlash"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoLockedFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoExposureCompensation"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoLockedLandscape"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoLockedPortrait"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPaused"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedAudioButton"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedSAF"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrash"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrashSAF"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedTrash2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoQuickFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeatFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeatFocusLocked"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAfterFocus"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoSingleTap"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoDoubleTap"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAlt"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerBackground"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerSettings"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTimerPopup"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoRepeat"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPicture1"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPicture2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocusRepeat"));
        if( MainActivityTest.test_camera2 ) {
            // test_wait_capture_result only relevant for Camera2 API
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testContinuousPictureFocusRepeatWaitCaptureResult"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testKeyboardControls"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoStamp"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoStampSAF"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoDRO"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoDROPhotoStamp"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPhotoBackgroundHDR"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRSaveExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRFrontCamera"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRAutoStabilise"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoHDRPhotoStamp"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoExpo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanorama"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanoramaMax"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanoramaCancel"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPanoramaCancelBySettings"));
        }
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder1"));
        if( !MainActivityTest.test_camera2 ) {
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder2"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder3"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolder4"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolderUnicode"));
            suite.addTest(TestSuite.createTest(MainActivityTest.class, "testCreateSaveFolderEmpty"));
        }
        // testTakePhotoPreviewPausedShare should be last, as sharing the image may sometimes cause later tests to hang
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoPreviewPausedShare"));
        return suite;
    }
}
