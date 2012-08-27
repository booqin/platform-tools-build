/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.builder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generate an AndroidManifest.xml file for test projects.
 */
public class TestManifestGenerator {

    private final static String TEMPLATE = "BuildConfig.template";
    private final static String PH_PACKAGE = "#PACKAGE#";
    private final static String PH_TEST_PACKAGE = "#TESTPACKAGE#";
    private final static String PH_TEST_RUNNER = "#TESTRUNNER#";

    private final static String DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner";

    private final String mOutputFile;
    private final String mPackageName;
    private final String mTestPackageName;
    private final String mTestRunnerName;

    TestManifestGenerator(String outputFile,
                          String packageName,
                          String testPackageName,
                          String testRunnerName) {
        mOutputFile = outputFile;
        mPackageName = packageName;
        mTestPackageName = testPackageName;
        mTestRunnerName = testRunnerName != null ? testRunnerName : DEFAULT_TEST_RUNNER;
    }

    public void generate() throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        map.put(PH_PACKAGE, mPackageName);
        map.put(PH_TEST_PACKAGE, mTestPackageName);
        map.put(PH_TEST_RUNNER, mTestRunnerName);

        TemplateProcessor processor = new TemplateProcessor(
                TestManifestGenerator.class.getResourceAsStream(TEMPLATE),
                map);

        processor.generate(new File(mOutputFile));

    }
}
