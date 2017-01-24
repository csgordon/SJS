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
 * @author Colin S. Gordon
 */
package com.samsung.sjs.backend;

import java.util.*;

public interface ILPSolver {

    /**
     * A method to solve the ILP problem for layout.
     *
     * @param lvf LayoutVariableFactory
     * @param tables The set of vtables to generate, used to generate inequality constraints (i.e.,
     *               that no two fields of the same object have the same offset), and to place size
     *               constraints based on how large the allocations are.
     * @param idealsets The sets of variables that are --- ideally --- the same.
     * @return Array mapping layout variables to their solutions, or null for failure.
     */
    public Integer[] solve(LayoutVariableFactory lvf,
                           Collection<Map<String,LayoutVariableFactory.LayoutVariable>> tables,
                           Collection<Set<LayoutVariableFactory.LayoutVariable>> idealsets);
}
