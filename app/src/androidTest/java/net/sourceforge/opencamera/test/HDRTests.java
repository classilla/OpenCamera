package net.sourceforge.opencamera.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class HDRTests {
    /** Tests for HDR algorithm - only need to run on a single device
     *  Should manually look over the images dumped onto DCIM/
     *  To use these tests, the testdata/ subfolder should be manually copied to the test device in the DCIM/testOpenCamera/
     *  folder (so you have DCIM/testOpenCamera/testdata/). We don't use assets/ as we'd end up with huge APK sizes which takes
     *  time to transfer to the device every time we run the tests.
     *  On Android 10+, scoped storage permission needs to be given to Open Camera for the DCIM/testOpenCamera/ folder.
     */
    public static Test suite() {
        TestSuite suite = new TestSuite(MainTests.class.getName());
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testDROZero"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testDRODark0"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testDRODark1"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR1"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR2"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR3"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR4"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR5"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR6"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR7"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR8"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR9"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR10"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR11"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR12"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR13"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR14"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR15"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR16"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR17"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR18"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR19"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR20"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR21"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR22"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR23"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR24"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR25"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR26"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR27"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR28"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR29"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR30"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR31"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR32"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR33"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR34"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR35"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR36"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR37"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR38Filmic"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR39"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR40"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR40Exponential"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR40Filmic"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR41"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR42"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR43"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR44"));
        // don't include testHDR45, this is tested as part of HDRNTests
        // don't include testHDR46, this is tested as part of HDRNTests
        // don't include testHDR47, this is tested as part of HDRNTests
        // don't include testHDR48, this is tested as part of HDRNTests
        // don't include testHDR49, this is tested as part of HDRNTests
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR50"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR51"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR52"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR53"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR54"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR55"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR56"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR57"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR58"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR59"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR60"));
        suite.addTest(TestSuite.createTest(MainActivityTest.class, "testHDR61"));
        return suite;
    }
}
