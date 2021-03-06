/* 
 * Copyright 2014-2016 Samsung Research America, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Unit tests for C Code generation backend, for doing the double codegen for interop mode
 *
 * @author colin.gordon
 */
package com.samsung.sjs;

import java.io.IOException;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.junit.Ignore;

public class GenInteropTest extends EndToEndTest {
    public GenInteropTest(String testName) throws IOException
    { 
        super(testName);
    }
    public static Test suite() {
        return new TestSuite(GenInteropTest.class);
    }
    @Override
    protected boolean doInterop() { return true; }
}
