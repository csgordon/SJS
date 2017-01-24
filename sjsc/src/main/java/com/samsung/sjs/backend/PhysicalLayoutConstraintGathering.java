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
 * A pass to collect data on the sets of vtables that should be generated, so a global optimization
 * problem can be solved to optimize the applicability of the field access optimizations.
 *
 * @author colin.gordon
 */
package com.samsung.sjs.backend;

/**
 * Design notes:
 *
 * One of the most important optimizations done by the SJS compiler is eliminating lookups in the
 * indirection table when accessing fields.  This is done by statically predicting the physical
 * offset in the object where some field would reside, based on a global analysis of all vtables
 * generated during this compilation.  This is done one of two ways:
 * 1. When all objects containing some field 'f' place it at the same physical offset (slot number)
 *    x, we say 'f' is <i>globally aligned</i> at offset x.  In this case, the compiler can replace
 *    all virtual field accesses to field 'f' by a direct access to slot x.
 * 2. When all vtables containing some field 'f' as well as all other fields present in the base
 *    object's type at a given (static) field access place the field in slot x, we say the 'f' is
 *    <i>partition aligned by co-occurrence</i> at x.  The terminology stems from the fact that
 *    pass essentially prunes the set of relevant vtables by first ruling out all vtables that are
 *    missing fields required at the access site, by looking at whic fields co-occur with the
 *    accessed field.
 * The effectiveness of this optimization is limited by where fields are laid out at each
 * allocation site --- by the vtable generated for each allocation.  Prior to this pass being
 * completed and used for vtable generation, this layout was luck-of-the-draw --- fields were
 * placed in the order they were syntactically mentioned in the source code (for object literals)
 * or in the order type inference arranged their members (constructor allocations).  Despite the
 * unpredictable field layout results, the optimization already makes a big difference in
 * performance --- it's SJS's static equivalent to inline caching if you compare to V8 (really,
 * it's just devirtualization, but applied to all members).
 *
 * This pass needs to gather information about what vtables need to be generated, and figure
 * out the optimal (by some measure) layout for each.  In the ideal case, this is an ILP:
 *      - Generate a variable for each field in each vtable (including FFI-declared ones).
 *        The variable represents the offset of that field in the vtable.
 *      - Constrain each variable X in vtable V to be 0 &le; X &le; |V|
 *      - For each field name, constrain all of the variables for that field to be the same
 *        offset.  This ideally constrains all instances of a given field to be at the
 *        same physical offset, making the field access optimizations very effective.
 *      - Invoke an ILP solver.
 *      - Produce the corresponding vtables.
 * Note the repeated use of forms of the word "ideal" above.  In general, the constraints
 * above won't always have a perfect solution, because it may not be possible to align all instances
 * of the same field across vtables, so some fields will end up with different slots in different
 * objects.  ILP already has a notion of maximizing some objective function, so we still only
 * require one call the ILP solver, as long as we have a good objective function to guide the
 * solver to a set of vtables that aligns the "most important" fields.
 *
 * The best solution to this would be based on
 * profiling information for dynamic accesses to particular fields and vtables, to focus on
 * enabling optimizations for the most frequent fields accesses.  That's a long way off, so
 * we'll need to heuristically approximate it.  Long term there are lots of things to do, from
 * PGO to doing dataflow analysis to identify sets of vtables with a common field that never flow
 * to the same field access.  But for now we'll do something more naive.
 *
 * For now, we'll look at how frequently two fields occur in the same object.  Roughly, the more
 * vtables containing a field, the more important it is to make it possible to optimize.  The more
 * often two fields occur together the more important it is to ensure they're consistently
 * separated (which we'll impose by trying to align both fields across all objects).  In each
 * iteration, we'll drop the same-offset-in-all-vtables constraint for the least common fields, or
 * fields that rarely co-occur with other more common fields (since the field access optimizer also
 * consider co-occurrence).
 *
 * Note: We could in principle use field types to partition instances of a field --- accesses to
 * field 'f' at type int will never be confused with a field 'f' at some object type.  Then we
 * could in principle extend the field access optimizer to exploit this.  But we really don't want
 * to: we have our eye on parametric polymorphism down the line, and if we only generate one code
 * path for polymoprhic code (as opposed to duplication + specialization) that would break this
 * type-based partitioning.  It's easier to add it later if we decide on template-style generics
 * than to rip it out later and try to "earn back" any related performance degredation.
 *
 * TODO: where should this objective function be managed?  Right now it's easiest to do inside the
 * BLTSolver, but then if we add support for a new solver later we'd have to duplicate that
 * function.  The alternative, though, is to expose the solver's full constraint language to this
 * class, which is hard to do portably since different ILP engines have different restrictions on
 * the form of a constraint or objective function...
 *
 * Near term:
 * - Consider permitting over-allocation by a couple slots, to improve the odds of some field being
 *   globally aligned by allowing an object to skip some spaces.
 *
 * Long term:
 * - Consider dataflow to partition cases where two sets of vtables with the same field never flow
 *   to the same access of that field, in which case that field doesn't need to have the same 
 *   offset across those sets.
 * - Consider frequency of access + co-occurrence of fields based on the type at the access site,
 *   to further partition groups of tables with the same field.
 *
 */

import com.samsung.sjs.CompilerOptions;
import com.samsung.sjs.FFILinkage;
import com.samsung.sjs.backend.asts.ir.*;
import com.samsung.sjs.types.*;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhysicalLayoutConstraintGathering extends VoidIRVisitor {

    private static Logger logger = LoggerFactory.getLogger(PhysicalLayoutConstraintGathering.class);

    private IRFieldCollector.FieldMapping field_codes;
    // For now, we can start by just trying to optimize based on picking a random set of fields to
    // give up on aligning.  Down the road, we can also accumulate different weighting statistics to
    // choose to e.g., try really hard to optimizes accesses to 'foo' because that's 30% of all
    // property accesses in the source, or based on profiling information...
    private int[][] cooccurrence_table;
    private FFILinkage ffi;

    private LayoutVariableFactory lvf;

    private Map<IRNode, Map<String,LayoutVariableFactory.LayoutVariable>> tablevars;

    private Map<String, Set<LayoutVariableFactory.LayoutVariable>> varsets;

    private ILPSolver ilp;

    public PhysicalLayoutConstraintGathering(CompilerOptions opts, IRFieldCollector.FieldMapping field_codes, FFILinkage ffi) {
        this.field_codes = field_codes;

        cooccurrence_table = new int[field_codes.size()][field_codes.size()];

        lvf = new LayoutVariableFactory();

        tablevars = new HashMap<>();
        varsets = new HashMap<>();

        // The FFI interface defines some vtables that may *not* be modified by the compiler.  It
        // dictates the physical offsets of some properties in some objects that aren't under
        // control of the compiler, and must be accounted for in a solution.
        this.ffi = ffi;

        // TODO: Implement an ILP solver.  Eventually make this a constructor arg, so a compiler
        // flag can switch between e.g., BLT and Z3.
        ilp = null;
    }

    protected void addFieldVar(String field, LayoutVariableFactory.LayoutVariable var) {
        Set<LayoutVariableFactory.LayoutVariable> fset = varsets.get(field);
        if (fset != null) {
            fset.add(var);
        } else {
            fset = new HashSet<>();
            fset.add(var);
            varsets.put(field, fset);
        }
    }

    protected void processObject(IRNode n, ObjectType t) {
        // Note that we have the luxury of assuming the writable properties are *exactly* the
        // properties physically present on the object, though that won't matter until we stop doing
        // copy-down inheritance...
        
        List<Property> props = t.properties();
        Map<String,LayoutVariableFactory.LayoutVariable> varvtable = new HashMap<>();
        tablevars.put(n, varvtable);
        
        for (Property p1 : props) {

            LayoutVariableFactory.LayoutVariable v = lvf.fresh();
            varvtable.put(p1.getName(), v);
            addFieldVar(p1.getName(), v);

            for (Property p2 : props) {
                if (!p1.getName().equals(p2.getName())) {
                    int off1 = field_codes.indexOf(p1.getName());
                    int off2 = field_codes.indexOf(p2.getName());
                    cooccurrence_table[off1][off2]++;
                    cooccurrence_table[off2][off1]++;
                }
            }
        }
    }

    public void dumpLayoutConstraints() {
        System.err.println("WARNING: Current constraints don't include FFI tables!");
        System.err.println("Known vtables to generate: ");
        for (Map.Entry<IRNode, Map<String,LayoutVariableFactory.LayoutVariable>> table : tablevars.entrySet()) {
            System.err.print("\t{");
            boolean comma = false;
            for (Map.Entry<String,LayoutVariableFactory.LayoutVariable> fvar : table.getValue().entrySet()) {
                if (comma) { System.err.print(","); }
                comma = true;
                System.err.print(fvar.getKey() + ":" + fvar.getValue());
            }
            System.err.println("}");
        }

        System.err.println("Ideal Sets:");
        for (Map.Entry<String, Set<LayoutVariableFactory.LayoutVariable>> set : varsets.entrySet()) {
            System.err.println(set.getKey() + ": "+set.getValue());
        }
    }

    public void solve() {
        // TODO: Currently optimistically assuming we'll solve the ideal constraints.
        // TODO: Constructor should take a choice of ILPSolver
        Integer[] solution = ilp.solve(lvf, tablevars.values(), varsets.values());
    }

    @Override
    public Void visitAllocObjectLiteral(AllocObjectLiteral node) {
        processObject(node, (ObjectType)node.getType());
        return super.visitAllocObjectLiteral(node);
    }

    @Override
    public Void visitAllocNewObject(AllocNewObject node) {
        processObject(node, (ObjectType)node.getType());
        return super.visitAllocNewObject(node);
    }

    public int[] getVTable(ObjectType t) {
        // TODO: compute and cache results on first call
        throw new UnsupportedOperationException();
    }
}
