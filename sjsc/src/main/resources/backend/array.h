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
#ifndef __ARRAY_H
#define __ARRAY_H

// We assume this is included in the presence of the value_t definition, e.g. from within runtime.h

/*
 * The following methods on the Array prototype are experimental (ECMAScript 6 features) and
 * currently unimplemented:
 *   - from()
 *   - of()
 *   - copyWithin()
 *   - entries()
 *   - fill()
 *   - find()
 *   - findIndex()
 *   - keys()
 *   - toSource() <-- This seems too reflective for SJS
 */

// NOTE: The front end of this is compatible with object layouts!
#define ARRAY_N_FIELDS 5

// Note: The prefix of this --- up to the last field entry --- matches the layout for a general
// object
/*
 * Note for New Object Model
 *
 * We will not copy array backing store support --- nelems, store_size, start, backing --- to the
 * core object represenation, for several reasons:
 * 1) This would nearly double the size of the object header
 * 2) There doesn't need to be a promotion from bag-of-props to fast array if some untyped code
 *    starts sticking random numeric properties on a non-array.  We can just stick them in the
 *    propbag, keyed by the string representation of the property name.
 * 3) The trick we play with sticking nelems inside a proper field can't work if numeric properties
 *    are added later, and semantically shouldn't since it's not really an array object (this won't
 *    happen in node either).  So even if we did store numeric properties in a separate contiguous
 *    backing store, length access would still be screwy, so we couldn't actually use the current
 *    fast array accessors we rely on for performance.
 */
typedef struct array_obj {
    object_map vtbl;
    object_t* __proto__;
    struct map* __propbag;
    type_tag_t* type;
#ifdef SMALL_POINTER
    // on 32-bit, this is sometimes necessary to get nelems 8-byte aligned
    // see comment in object_t
    //void *_____padding;
#endif
    int32_t nelems;    // fields[0], same size as value, TODO: very large vals collide with NaNboxing scheme
    uint32_t nelems_tag; // tagging for int fields
    value_t pop_op;     // fields[1]
    value_t push_op;    // fields[2]
    value_t shift_op;   // fields[3]
    value_t unshift_op; // fields[4]
    unsigned int store_size;
    value_t* start;
    value_t* backing;
} array_obj_t;
STATIC_ASSERT(offsetof(array_obj_t, nelems) == offsetof(object_t, fields[0]), array_object_layout_consistency);
#define __array_len(v) (((array_obj_t*)v.obj)->nelems)

/* A short note on array design
 *
 * The goal of the array design is to make in-bounds access --- for reading or writing --- as fast
 * as possible, without making anything else ridiculously slow.  Once an access is in bounds, it's
 * fairly cheap to just get the right slot, so the subtlety is in arranging the representation to
 * support cheap bounds checks.
 *
 * The ES spec gives operations on arrays that allow it to act as a dequeue --- pushing or popping
 * on either end via push/pop (high index) and shift/unshift (low index).  The natural choice for
 * this in general is to represent the array elements as an array list with factor-of-two growth.
 * But this comes at the cost of need to wrap indices into the array.  Doing this with modulus (%)
 * is tremendously expensive, since it's the slowest FPU operation (50-75x slower than integer
 * addition, depending on the CPU).  Even avoiding that, simply checking if the 'start index' for 
 * the array list is 0 on the fast path adds significant overhead.  So instead of tracking the start
 * offset into a backing C array, we just bump the base pointer to the backing C array.  The
 * conservative GC recognizes this as an interior pointer, and preserves the array allocation.
 * We separately maintain the original base pointer, so we can manually free large backing stores
 * as we grow the array.
 * 
 * Note: Future work to optimize out array bounds access is absolutely worthwhile --- currently 50%
 * of execution time on fannkuch is spent on array bounds accesses (removing bounds checks, the
 * program runs in half the time).
 *
 * There are also a few alternatives I've tried with mixed results (fannkuch goes much faster on ARM, other
 * things go slightly slower):
 * - Use nelems as the bound for array_get: This helps fannkuch slightly, but hurts others.  This
 *   would generally help --- fewer reads generate the additional memory access to the backing store
 *   --- but on benchmarks where such reads are in close proximity to writes of the same offset, the
 *   C compiler seems to manage to combine intermediate address calculations.
 * - Use nelems as the bound for array_put: This helps fannkuch quite a bit (17% faster), but hurts
 *   others.  It primarily helps workloads that write primarily in-bounds, rather than extending the
 *   array by writing to new offsets (fannkuch allocates Array(n) early on), since they don't need
 *   to look at updating nelems.  But for workloads which incrementally extend the array by one
 *   element by writing one index past the "end," comparing to store_size and updating nelems inline
 *   is much faster than incurring the function call to slow_array_put every time.
 * - Both of the above: similar results, for the same reasons.
 */

// TODO: Exceptions for array bounds
#define FAIL (*(value_t*)0x1)  
#define ACCESS_ARRAY_REP(o) ((array_obj_t*)(o))

static inline value_t array_get(object_t *o, int32_t i) {
    if (i < ACCESS_ARRAY_REP(o)->store_size && i >= 0) {
        return ACCESS_ARRAY_REP(o)->backing[i];
    } else { //if ( i >= ACCESS_ARRAY_REP(o)->store_size || i < 0 ) {
        return (value_t)(0xFFFF000700000000ULL);
    }
}


value_t slow_array_put(object_t* objthis, int elem, value_t v);
static inline value_t array_put(object_t *o, int32_t i, value_t v) {
    if (i < ACCESS_ARRAY_REP(o)->store_size && i >= 0) {
        if (i >= ACCESS_ARRAY_REP(o)->nelems) {
            ACCESS_ARRAY_REP(o)->nelems = i + 1;
        }
        return (ACCESS_ARRAY_REP(o)->backing[i] = v);
    } else { //if ( i >= ACCESS_ARRAY_REP(o)->store_size || i < 0 ) {
        return slow_array_put(o, i, v);
    }
}

typedef struct { env_t env; value_t (*func)(env_t, value_t, value_t); } array_clos_t;
extern value_t* Array;

#ifdef __cplusplus
extern "C" {
#endif
object_t* array___lit(type_tag_t *tag, int length, ...);
#ifdef __cplusplus
}
#endif

int array_length(object_t* arr);

// TODO: Array.isArray() Seems too reflective for SJS

// TODO: Array.concat takes arguments of type (U Array<T> T)

// TODO: Support the optional thisArg
//int array_every(object_t* this, CLOSURETY2(int (*func)(env_t env, value_t v)) *f);

// TODO: Support the optional thisArg
//array_ptr array_filter(object_t* this, CLOSURETY2(int (*f)(env_t, value_t)));

// TODO: Support the optional thisArg
//void array_forEach(object_t* this, CLOSURETY2(void (*func)(env_t, value_t)) *f);

// TODO: Support optional fromIndex arg
//void array_indexOf(object_t* this, value_t searchElement);

// TODO: Support optional separator argument
//char* array_join();

// TODO: Support optional fromIndex arg
//void array_lastIndexOf(object_t* this, value_t searchElement);

// TODO: Support the optional thisArg
//array_ptr array_map(object_t* this, CLOSURETY2(void (*func)(env_t, value_t, int, array_ptr)) *f);

value_t array_pop(env_t, value_t arr);

// TODO: make this variadic
value_t array_push(env_t, value_t arr, value_t v);

// TODO: reduce (return is union type)
// TODO: reduceRight

//void array_reverse(object_t* this);

value_t array_shift(env_t, value_t arr);

//TODO: slice
//TODO: some
//TODO: sort
//TODO: splice
//TODO: toLocaleString()
//TODO: toString()

// TODO: make this variadic
void array_unshift(env_t, value_t arr, value_t v);

object_t* array_reverse(env_t, object_t*);

//object_t* c_to_js_array(char** __arguments);

#endif // __ARRAY_H
