package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class OldDeviceTests {
    // Small set of tests to run on very old devices.
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());

        // put these tests first as they require various permissions be allowed, that can only be set by user action
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSwitchVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationSettings"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testPause"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveModes"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFocusFlashAvailability"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testGallery"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettings"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettingsSaveLoad"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFolderChooserNew"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testFolderChooserInvalid"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSaveFolderHistory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testSettingsPrivacyPolicy"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testLocationOn"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhoto"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevel"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelLowMemory"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAngles"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakePhotoAutoLevelAnglesLowMemory"));

        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testTakeVideoSubtitles"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideo"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testIntentVideoDurationLimit"));

        return suite;
    }
}
