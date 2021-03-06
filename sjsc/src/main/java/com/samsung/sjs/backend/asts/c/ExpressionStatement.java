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
 * Representation of C expression wrapped as a statement
 *
 * @author colin.gordon
 */
package com.samsung.sjs.backend.asts.c;
public class ExpressionStatement extends Statement {
    private String post_label;
    private String dirty_label;
    private Expression e;
    public ExpressionStatement(Expression exp) {
        this.e = exp;
        post_label = null;
        dirty_label = null;
    }
    public void setPostLabel(String s) { post_label = s; }
    public void setDirtyLabel(String s, Variable... transfers) {
        dirty_label = s;
    }
    @Override
    public String toSource(int x) {
        if (e == null) { return ";"; }
        StringBuilder sb = new StringBuilder();
        indent(x,sb);
        String src = e.toSource(0);
        sb.append(src);
        if (src.charAt(0) == '#' || src.equals("extern \"C\" {")) {
            // preprocessor directive
            sb.append("\n");
        } else {
            sb.append(";\n");
        }
        if (post_label != null) {
            sb.append(post_label+": EMPTY_STATEMENT;\n");
        }
        if (dirty_label != null) {
            indent(x,sb);
            sb.append("if (__dirty) { goto "+dirty_label+"; }\n");
        }
        return sb.toString();
    }
}
