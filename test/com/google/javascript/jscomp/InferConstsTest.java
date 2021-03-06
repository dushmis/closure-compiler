/*
 * Copyright 2014 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link InferConsts}.
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public final class InferConstsTest extends TestCase {
  public void testSimple() {
    testConsts("var x = 3;", "x");
    testConsts("/** @const */ var x;", "x");
    testConsts("var x = 3, y = 4;", "x", "y");
    testConsts("var x = 3, y;", "x");
    testConsts("var x = 3;  function f(){x;}", "x");
  }

  public void testSimpleLetConst() {
    testConsts("let x = 3, y", "x");
    testConsts("let x = 3; let y = 4;", "x", "y");
    testConsts("let x = 3, y = 4; x++;", "y");
    testConsts("let x = 3;  function f(){let x = 4;}", "x");
    testConsts("/** @const */ let x;", "x");
    testConsts("const x = 1;", "x");
  }

  public void testUnfound() {
    testNotConsts("var x = 2; x++;", "x");
    testNotConsts("var x = 2; x = 3;", "x");
    testNotConsts("var x = 3;  function f(){x++;}", "x");
    testNotConsts("let x = 3; x++;", "x");
    testNotConsts("let x = 3; x = 2;", "x", "y");
    testNotConsts("/** @const */let x; let y;", "y");
    testNotConsts("let x = 3;  function f() {let x = 4; x++;} x++;", "x");
  }

  public void testArguments() {
    testConsts("var arguments = 3;");
  }

  private void testConsts(String js, String... constants) {
    testInferConstsHelper(true, js, constants);
  }

  private void testNotConsts(String js, String... constants) {
    testInferConstsHelper(false, js, constants);
  }

  private void testInferConstsHelper(boolean constExpected,
      String js, String... constants) {
    Compiler compiler = new Compiler();

    SourceFile extern = SourceFile.fromCode("extern", "");
    SourceFile input = SourceFile.fromCode("js", js);
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.of(input),
        new CompilerOptions());

    compiler.options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    compiler.setLanguageMode(LanguageMode.ECMASCRIPT6);
    compiler.parseInputs();

    CompilerPass inferConsts = new InferConsts(compiler);
    inferConsts.process(
        compiler.getRoot().getFirstChild(),
        compiler.getRoot().getLastChild());

    Node n = compiler.getRoot().getLastChild();

    FindConstants constFinder = new FindConstants(constants);
    NodeTraversal.traverse(compiler, n, constFinder);

    for (String name : constants) {
      if (constExpected) {
        assertTrue("Expect constant: " + name,
            constFinder.foundNodes.containsKey(name));
      } else {
        assertTrue("Unexpected constant: " + name,
            !constFinder.foundNodes.containsKey(name));
      }
    }
  }

  private static class FindConstants extends NodeTraversal.AbstractPostOrderCallback {
    final String[] names;
    final Map<String, Node> foundNodes;

    FindConstants(String[] names) {
      this.names = names;
      foundNodes = new HashMap<>();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      for (String name : names) {
        if (n.matchesQualifiedName(name)
            && n.getBooleanProp(Node.IS_CONSTANT_VAR)) {
          foundNodes.put(name, n);
        }
      }
    }
  }
}
