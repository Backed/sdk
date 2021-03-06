/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ddmlib.testrunner;


import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Runs a Android test command remotely and reports results.
 */
public class RemoteAndroidTestRunner implements IRemoteAndroidTestRunner  {

    private final String mPackageName;
    private final String mRunnerName;
    private IDevice mRemoteDevice;
    // default to no timeout
    private int mMaxTimeToOutputResponse = 0;
    private String mRunName = null;

    /** map of name-value instrumentation argument pairs */
    private Map<String, String> mArgMap;
    private InstrumentationResultParser mParser;

    private static final String LOG_TAG = "RemoteAndroidTest";
    private static final String DEFAULT_RUNNER_NAME = "android.test.InstrumentationTestRunner";

    private static final char CLASS_SEPARATOR = ',';
    private static final char METHOD_SEPARATOR = '#';
    private static final char RUNNER_SEPARATOR = '/';

    // defined instrumentation argument names
    private static final String CLASS_ARG_NAME = "class";
    private static final String LOG_ARG_NAME = "log";
    private static final String DEBUG_ARG_NAME = "debug";
    private static final String COVERAGE_ARG_NAME = "coverage";
    private static final String PACKAGE_ARG_NAME = "package";
    private static final String SIZE_ARG_NAME = "size";

    /**
     * Creates a remote Android test runner.
     *
     * @param packageName the Android application package that contains the tests to run
     * @param runnerName the instrumentation test runner to execute. If null, will use default
     *   runner
     * @param remoteDevice the Android device to execute tests on
     */
    public RemoteAndroidTestRunner(String packageName,
                                   String runnerName,
                                   IDevice remoteDevice) {

        mPackageName = packageName;
        mRunnerName = runnerName;
        mRemoteDevice = remoteDevice;
        mArgMap = new Hashtable<String, String>();
    }

    /**
     * Alternate constructor. Uses default instrumentation runner.
     *
     * @param packageName the Android application package that contains the tests to run
     * @param remoteDevice the Android device to execute tests on
     */
    public RemoteAndroidTestRunner(String packageName,
                                   IDevice remoteDevice) {
        this(packageName, null, remoteDevice);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRunnerName() {
        if (mRunnerName == null) {
            return DEFAULT_RUNNER_NAME;
        }
        return mRunnerName;
    }

    /**
     * Returns the complete instrumentation component path.
     */
    private String getRunnerPath() {
        return getPackageName() + RUNNER_SEPARATOR + getRunnerName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClassName(String className) {
        addInstrumentationArg(CLASS_ARG_NAME, className);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClassNames(String[] classNames) {
        StringBuilder classArgBuilder = new StringBuilder();

        for (int i = 0; i < classNames.length; i++) {
            if (i != 0) {
                classArgBuilder.append(CLASS_SEPARATOR);
            }
            classArgBuilder.append(classNames[i]);
        }
        setClassName(classArgBuilder.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMethodName(String className, String testName) {
        setClassName(className + METHOD_SEPARATOR + testName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestPackageName(String packageName) {
        addInstrumentationArg(PACKAGE_ARG_NAME, packageName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInstrumentationArg(String name, String value) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("name or value arguments cannot be null");
        }
        mArgMap.put(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeInstrumentationArg(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name argument cannot be null");
        }
        mArgMap.remove(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addBooleanArg(String name, boolean value) {
        addInstrumentationArg(name, Boolean.toString(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogOnly(boolean logOnly) {
        addBooleanArg(LOG_ARG_NAME, logOnly);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDebug(boolean debug) {
        addBooleanArg(DEBUG_ARG_NAME, debug);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCoverage(boolean coverage) {
        addBooleanArg(COVERAGE_ARG_NAME, coverage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestSize(TestSize size) {
        addInstrumentationArg(SIZE_ARG_NAME, size.getRunnerValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxtimeToOutputResponse(int maxTimeToOutputResponse) {
        mMaxTimeToOutputResponse = maxTimeToOutputResponse;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRunName(String runName) {
        mRunName = runName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestRunListener... listeners)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        run(Arrays.asList(listeners));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(Collection<ITestRunListener> listeners)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        final String runCaseCommandStr = String.format("am instrument -w -r %1$s %2$s",
            getArgsCommand(), getRunnerPath());
        Log.i(LOG_TAG, String.format("Running %1$s on %2$s", runCaseCommandStr,
                mRemoteDevice.getSerialNumber()));
        String runName = mRunName == null ? mPackageName : mRunName;
        mParser = new InstrumentationResultParser(runName, listeners);

        try {
            mRemoteDevice.executeShellCommand(runCaseCommandStr, mParser, mMaxTimeToOutputResponse);
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("IOException %1$s when running tests %2$s on %3$s",
                    e.toString(), getPackageName(), mRemoteDevice.getSerialNumber()));
            // rely on parser to communicate results to listeners
            mParser.handleTestRunFailed(e.toString());
            throw e;
        } catch (ShellCommandUnresponsiveException e) {
            Log.w(LOG_TAG, String.format(
                    "ShellCommandUnresponsiveException %1$s when running tests %2$s on %3$s",
                    e.toString(), getPackageName(), mRemoteDevice.getSerialNumber()));
            mParser.handleTestRunFailed(String.format(
                    "Failed to receive adb shell test output within %1$d ms. " +
                    "Test may have timed out, or adb connection to device became unresponsive",
                    mMaxTimeToOutputResponse));
            throw e;
        } catch (TimeoutException e) {
            Log.w(LOG_TAG, String.format(
                    "TimeoutException when running tests %1$s on %2$s", getPackageName(),
                    mRemoteDevice.getSerialNumber()));
            mParser.handleTestRunFailed(e.toString());
            throw e;
        } catch (AdbCommandRejectedException e) {
            Log.w(LOG_TAG, String.format(
                    "AdbCommandRejectedException %1$s when running tests %2$s on %3$s",
                    e.toString(), getPackageName(), mRemoteDevice.getSerialNumber()));
            mParser.handleTestRunFailed(e.toString());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel() {
        if (mParser != null) {
            mParser.cancel();
        }
    }

    /**
     * Returns the full instrumentation command line syntax for the provided instrumentation
     * arguments.
     * Returns an empty string if no arguments were specified.
     */
    private String getArgsCommand() {
        StringBuilder commandBuilder = new StringBuilder();
        for (Entry<String, String> argPair : mArgMap.entrySet()) {
            final String argCmd = String.format(" -e %1$s %2$s", argPair.getKey(),
                    argPair.getValue());
            commandBuilder.append(argCmd);
        }
        return commandBuilder.toString();
    }
}
