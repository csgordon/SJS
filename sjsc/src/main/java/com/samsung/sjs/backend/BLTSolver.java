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
 * A driver for Galois' BLT ILP solver
 *
 * @author Colin S. Gordon
 */
package com.samsung.sjs.backend;

import java.util.*;

public final class BLTSolver implements ILPSolver {

    private static class BLTVar {
        public final int id;
        public BLTVar(int i) { id = i; }
    }

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
                           Collection<Set<LayoutVariableFactory.LayoutVariable>> idealsets) {
        Integer[] results = lvf.layoutVars(Integer.class);
        BLTVar[] bltvars = lvf.layoutVars(BLTVar.class);
        for (int i = 0; i < bltvars.length; i++) {
            bltvars[i] = new BLTVar(i);
        }
        
        // TODO: what's the objective function? Sum_v(#vtables(v))?  Subtract smaller penalty for
        // collisions between co-occurring fields?
    }

    /**
     * Adds constraint 0 &le; a-b &le; 0
     */
    private void constraintEqual(BLTVar a, BLTVar b) {
        throw new UnsupportedOperationException();
    }

    /*
     * Adds constraint ... TODO small constraint for inequality
     */
    private void constrainNonequal(BLTVar a, BLTVar b) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds constraint 0 &le; a &le; x
     */
    private void setMaxVal(BLTVar a, int x) {
        throw new UnsupportedOperationException();
    }
}

