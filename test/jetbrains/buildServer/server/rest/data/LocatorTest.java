/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.server.rest.errors.LocatorProcessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.junit.Assert.*;

/**
 * @author Yegor.Yarko
 *         Date: 14.08.2010
 */
public class LocatorTest {

  @Test
  public void testSingleValue() {
    final Locator locator = new Locator("abc");
    assertEquals(true, locator.isSingleValue());
    assertEquals(0, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue(""));
    assertEquals(null, locator.getSingleDimensionValue("name"));

    try {
      locator.getSingleValueAsLong();
      fail();
    } catch (LocatorProcessException ex) {
    }
  }

  @Test
  public void testSingleNumericValue() {
    final Locator locator = new Locator("123");
    assertEquals(true, locator.isSingleValue());
    assertEquals(new Long(123), locator.getSingleValueAsLong());
    assertEquals(0, locator.getDimensionsCount());
  }

  @Test(expectedExceptions =  LocatorProcessException.class)
  public void testEmpty() {
    new Locator("");
  }

  @Test
  public void testSingleDimension() {
    final Locator locator = new Locator("name:1Vasiliy");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(null, locator.getSingleValueAsLong());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue(""));
    assertEquals(null, locator.getSingleDimensionValue("missing"));
    assertEquals("1Vasiliy", locator.getSingleDimensionValue("name"));
    assertEquals(null, locator.getSingleDimensionValue("Name"));
    try {
      locator.getSingleDimensionValueAsLong("name");
      fail();
    } catch (LocatorProcessException ex) {
    }
  }

  @Test
  public void testSingleDimensionComplexValue() {
    final Locator locator = new Locator("a:!@#$%^&*()_+\"\'iqhjbw`0912");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(null, locator.getSingleValueAsLong());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("!@#$%^&*()_+\"\'iqhjbw`0912", locator.getSingleDimensionValue("a"));
  }

  @Test
  public void testSingleDimension2() {
    final Locator locator = new Locator("aaa(x");
    assertEquals(true, locator.isSingleValue());
    assertEquals("aaa(x", locator.getSingleValue());
    assertEquals(0, locator.getDimensionsCount());
  }

  @Test
  public void testNoColon() {
    final Locator locator = new Locator("aaa(x:y)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("x:y", locator.getSingleDimensionValue("aaa"));
  }

  @Test
  public void testNoColon2() {
    final Locator locator = new Locator("aaa(x)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("x", locator.getSingleDimensionValue("aaa"));
  }

  @Test
  public void testNoColon3() {
    final Locator locator = new Locator("aaa(x(y))");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("x(y)", locator.getSingleDimensionValue("aaa"));
  }

  @Test
  public void testAnySpecialValue1() {
    final Locator locator = new Locator("$any"); //no special meaning for single value
    assertEquals(true, locator.isSingleValue());
    assertEquals("$any", locator.getSingleValue());
  }

  @Test
  public void testAnySpecialValue2() {
    final Locator locator = new Locator("a:$any");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue("a"));
    assertEquals(Arrays.asList("$any"), locator.getDimensionValue("a"));
    assertEquals(Collections.emptySet(), locator.getUnusedDimensions());
    assertTrue(locator.getUsedDimensions().contains("a"));
  }

  @Test
  public void testAnySpecialValue3() {
    final Locator locator = new Locator("a:($any)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("$any", locator.getSingleDimensionValue("a"));
    assertEquals(Arrays.asList("$any"), locator.getDimensionValue("a"));
  }

  @Test
  public void testAnySpecialValue4() {
    final Locator locator = new Locator("a:($any),a:b");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals(Arrays.asList("$any", "b"), locator.getDimensionValue("a"));
  }

  @Test
  public void testAnySpecialValue5() {
    final Locator locator = new Locator("a:($any),b:c");
    locator.setDimensionIfNotPresent("a", "x");
    assertEquals("a:($any),b:c", locator.getStringRepresentation());
  }

  @Test
  public void testSetDimension() {
    assertEquals("a:b,x:y,aa:z", Locator.setDimensionIfNotPresent("a:b,x:y", "aa","z"));
    assertEquals("a:b,x:y", Locator.setDimensionIfNotPresent("a:b,x:y", "a","z"));
    assertEquals("a:$any,x:y", Locator.setDimensionIfNotPresent("a:$any,x:y", "a","z"));

    assertEquals("a:(b:10),b:20", Locator.setDimension("a:(b:10)", "b","20"));
  }

  @Test
  public void testEscaped() {
    check("(abc)", true, "abc");
    check("(a:b)", true, "a:b");
    check("(a:b,d(x:y))", true, "a:b,d(x:y)");
    check("(a:b,)d(x:y)", true, "a:b,)d(x:y");
    check("a:(bb)", false, null, "a", "bb");
    check("a:((bb))", false, null, "a", "(bb)");

    checkException("a:(a(b)", LocatorProcessException.class);
    checkException("a:(a)b)", LocatorProcessException.class);
    checkException("(a)b", LocatorProcessException.class);
    checkException("(a:b", LocatorProcessException.class);
  }

  @Test
  public void testParentheses() {
    check("branch:(name(1))", false, null, "branch", "name(1)");
    check("branch:((name(1)))", false, null, "branch", "(name(1))");

    check("branch:name(1)", false, null, "branch", "name(1)");
    check("branch:value:name(1)", false, null, "branch", "value:name(1)");

    check("branch:(name:(value:(name(1))))", false, null, "branch", "name:(value:(name(1)))");
    check("name:(value:(name(1)))", false, null, "name", "value:(name(1))");
  }

  @Test
  public void testBase64Encoded() {
    check("$base64", true, "$base64");
    check("a:$base64", false, null, "a", "$base64");
    check("$base64:YWFh", true, "aaa");
    check("$base64:(YWFh)", true, "aaa");
    check("($base64:YWFh)", true, "$base64:YWFh");
    check("$base64:YTooYjpjKQ==", true, "a:(b:c)");
    check("$base64:KGE6Yik=", true, "(a:b)");
    check("a:($base64:YWFh)", false, null, "a", "aaa");
    check("a:($base64:KQ==)", false, null, "a", ")");
    check("$base64:0KTQq9Cy0JAtQVNkRg==", true, "\u0424\u042B\u0432\u0410-ASdF");
    check("$base64:0JXQs9C+0YDQldCz0L/RgA==", true, "\u0415\u0433\u043E\u0440\u0415\u0433\u043F\u0440");
    check("$base64:0JXQs9C-0YDQldCz0L_RgA==", true, "\u0415\u0433\u043E\u0440\u0415\u0433\u043F\u0440"); //Base64 URL
    check("$base64:56if", true, "\u7A1F");

    check("$base64:JGJhc2U6WVE9PQ==", true, "$base:YQ==");

    check("$base64:8J+mhA==", true, "\uD83E\uDD84"); // U+1F984, &#129412;

    check("$base64:", true, "");

    checkException("$base64:((YWFh))", LocatorProcessException.class);  //tries to parse "(YWFh)" as base64
    checkException("$base64:YWFh)", LocatorProcessException.class); //tries to parse "YWFh)" as base64
    checkException("$aaa:bbb,a:b", LocatorProcessException.class);
    checkException("$base64:YWFh,a:b", LocatorProcessException.class);
    checkException("$base64:YWFh:", LocatorProcessException.class);
    checkException("$base64:YWFh:,", LocatorProcessException.class);
    checkException("$base64:YWFh,$base64:YWFh", LocatorProcessException.class);
    checkException("$base64:YWFh.", LocatorProcessException.class);
    checkException("$base64:YWF\u0415=", LocatorProcessException.class);
    checkException("$base64:a.", LocatorProcessException.class);
    checkException("$base64:=a", LocatorProcessException.class);
    checkException("$base64:aLJBNlkjblk+/===", LocatorProcessException.class);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testBooleanDimensions() {
    assertTrue(new Locator("a:b,c:true").getSingleDimensionValueAsBoolean("c"));
    assertFalse(new Locator("a:b,c:false").getSingleDimensionValueAsBoolean("c"));
    assertTrue(new Locator("a:b,c:yes").getSingleDimensionValueAsBoolean("c"));
    assertFalse(new Locator("a:b,c:no").getSingleDimensionValueAsBoolean("c"));
    assertNull(new Locator("a:b,c:any").getSingleDimensionValueAsBoolean("c"));
    assertNull(new Locator("a:b,c:all").getSingleDimensionValueAsBoolean("c"));
    assertNull(new Locator("a:b,c:$any").getSingleDimensionValueAsBoolean("c"));
    assertNull(new Locator("a:b,c:($any)").getSingleDimensionValueAsBoolean("c"));

    try {
      new Locator("a:b,c:xxx").getSingleDimensionValueAsBoolean("c");
      fail("No exception thrown");
    } catch (LocatorProcessException e) {
      //all OK
    }
  }

  @Test
  public void testSingleNumericDimension() {
    final Locator locator = new Locator("age:15");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(null, locator.getSingleValueAsLong());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals(new Long(15), locator.getSingleDimensionValueAsLong("age"));
    assertEquals(null, locator.getSingleDimensionValueAsLong("name"));
  }

  @Test
  public void testMultiDimension1() {
    final Locator locator = new Locator("name:Bob:32,age:2,mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(3, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue("Bob"));
    assertEquals("Bob:32", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
    assertEquals(new Long(2), locator.getSingleDimensionValueAsLong("age"));
  }

  @Test
  public void testComplexValues1() {
    final Locator locator = new Locator("name:(Bob:32_,age:2),mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals(null, locator.getSingleDimensionValue("age"));
    assertEquals("Bob:32_,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues1a(){
    try {
      final Locator locator = new Locator("name:(Bob:32(,age:2),mood:permissive");
      assertTrue("Should never reach here", false);
    } catch (LocatorProcessException e) {
      assertTrue(e.getMessage().contains("Could not find matching ')'"));
      assertTrue(e.getMessage().contains("at position 6"));
    }
  }

  @Test
  public void testComplexValues2() {
    final Locator locator = new Locator("a:smth,name:(Bob:32_,age:2),mood:permissive");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("smth", locator.getSingleDimensionValue("a"));
    assertEquals("Bob:32_,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues2a(){
    try {
      final Locator locator = new Locator("a:smth,name:(Bob:32(,age:2),mood:permissive");
      assertTrue("Should never reach here", false);
    } catch (LocatorProcessException e) {
      assertTrue(e.getMessage().contains("Could not find matching ')'"));
      assertTrue(e.getMessage().contains("at position 13"));
    }
  }

  @Test
  public void testComplexValues3() {
    final Locator locator = new Locator("name:(Bob:32_,age:2),mood:(permissive)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("Bob:32_,age:2", locator.getSingleDimensionValue("name"));
    assertEquals("permissive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValues3a(){
    try {
      final Locator locator = new Locator("name:(Bob:32(,age:2),mood:(permissive)");
      assertTrue("Should never reach here", false);
    } catch (LocatorProcessException e) {
      assertTrue(e.getMessage().contains("Could not find matching ')'"));
    }
  }

  @Test
  public void testComplexValues4() {
    final Locator locator = new Locator("name:17,mood:(permiss:ive)");
    assertEquals(false, locator.isSingleValue());
    assertEquals(null, locator.getSingleValue());
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("17", locator.getSingleDimensionValue("name"));
    assertEquals("permiss:ive", locator.getSingleDimensionValue("mood"));
  }

  @Test
  public void testComplexValueCommaAndBrackets() {
    check("x:y:z", false, null, "x", "y:z");
    check("x:y:z,a:b", false, null, "x", "y:z", "a", "b");
    check("x:(y:z,a:b)", false, null, "x", "y:z,a:b");
    check("x:y:(z,a:b)", false, null, "x", "y:(z,a:b)");
    check("x:y:(a:b,c:d)", false, null, "x", "y:(a:b,c:d)");
    check("a:b)", false, null, "a", "b)");
  }

  @Test
  public void testNestedComplexValues1() {
    final Locator locator = new Locator("buildType:(name:5,project:(id:Project_1))");
    assertEquals(false, locator.isSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("name:5,project:(id:Project_1)", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testNestedComplexValues2() {
    final Locator locator = new Locator("buildType:(name:5),project:(id:Project_1)");
    assertEquals(false, locator.isSingleValue());
    assertEquals("name:5", locator.getSingleDimensionValue("buildType"));
    assertEquals("id:Project_1", locator.getSingleDimensionValue("project"));
  }

  @Test
  public void testNestedComplexValues3() {
    final Locator locator = new Locator("buildType:((name:5,project:(id:Project_1)))");
    assertEquals(false, locator.isSingleValue());
    assertEquals("(name:5,project:(id:Project_1))", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testNestedComplexValues4() {
    final Locator locator = new Locator("buildType:(name:5,(project:(id:Project_1)),a:b(c),d),f:d");
    assertEquals(false, locator.isSingleValue());
    assertEquals("name:5,(project:(id:Project_1)),a:b(c),d", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testEmptyValues() {
    final Locator locator = new Locator("name:,y:aaa,x:");
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("aaa", locator.getSingleDimensionValue("y"));
    assertEquals("", locator.getSingleDimensionValue("x"));
  }

  @Test
  public void testMisc1() {
    final Locator locator = new Locator("a:,b:");
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("a"));
    assertEquals("", locator.getSingleDimensionValue("b"));
    assertEquals(null, locator.getSingleDimensionValue("c"));
  }

  @Test
  public void testValueLess1() {
    final Locator locator = new Locator("id,number,status", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("id"));
    assertEquals("", locator.getSingleDimensionValue("number"));
    assertEquals("", locator.getSingleDimensionValue("status"));
  }

  @Test
  public void testValueLess2() {
    final Locator locator = new Locator("buildType(name,project(id,name))", true, null);
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("name,project(id,name)", locator.getSingleDimensionValue("buildType"));
  }

  @Test
  public void testValueLess21() {
    final Locator locator = new Locator("buildType(name,project(id,name),builds)", true, null);
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("name,project(id,name),builds", locator.getSingleDimensionValue("buildType"));
    assertEquals(null, locator.getSingleDimensionValue("builds"));
  }

  @Test
  public void testValueLess22() {
    final Locator locator = new Locator("buildType(name,project(id,name),builds),href", true, null);
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("name,project(id,name),builds", locator.getSingleDimensionValue("buildType"));
    assertEquals("", locator.getSingleDimensionValue("href"));
  }

  @Test
  public void testValueLess23() {
    final Locator locator = new Locator("count,buildType:(name,project(id,name),builds),href", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("count"));
    assertEquals("name,project(id,name),builds", locator.getSingleDimensionValue("buildType"));
    assertEquals("", locator.getSingleDimensionValue("href"));
  }

  @Test
  public void testValueLess3() {
    final Locator locator = new Locator("name,project(id,name)", true, null);
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("id,name", locator.getSingleDimensionValue("project"));
  }

  @Test
  public void testValueLess4() {
    final Locator locator = new Locator("name,project(id,name),builds(),x", true, null);
    assertEquals(4, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("id,name", locator.getSingleDimensionValue("project"));
    assertEquals("", locator.getSingleDimensionValue("builds"));
    assertEquals("", locator.getSingleDimensionValue("x"));
  }

  @Test
  public void testValueLess5() {
    final Locator locator = new Locator("count,parentProject(id),projects(id)", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("count"));
    assertEquals(null, locator.getSingleDimensionValue("parentproject"));
    assertEquals("id", locator.getSingleDimensionValue("parentProject"));
    assertEquals("id", locator.getSingleDimensionValue("projects"));
  }

  @Test
  public void testMisc2() {
    final Locator locator = new Locator("a:x y ,b(x y),c", true, null);
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("x y ", locator.getSingleDimensionValue("a"));
    assertEquals("x y", locator.getSingleDimensionValue("b"));
    assertEquals("", locator.getSingleDimensionValue("c"));
  }

  @Test
  public void testMisc3() {
    final Locator locator = new Locator("name:,a", true, null);
    assertEquals(2, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("name"));
    assertEquals("", locator.getSingleDimensionValue("a"));
    assertEquals(null, locator.getSingleDimensionValue("b"));
  }

  @Test
  public void testSingleValueExtendedMode() {
    final Locator locator = new Locator("a", true, null);
    assertEquals(false, locator.isSingleValue());
    assertEquals(1, locator.getDimensionsCount());
    assertEquals("", locator.getSingleDimensionValue("a"));
  }

  @Test
  public void testCustomNames1() {
    final Locator locator = new Locator("~!@#$%^&*_+(c),+,$aaa:bbb", true, "~!@#$%^&*_+", "$aaa", "+", "-");
    assertEquals(3, locator.getDimensionsCount());
    assertEquals("c", locator.getSingleDimensionValue("~!@#$%^&*_+"));
    assertEquals("", locator.getSingleDimensionValue("+"));
    assertEquals("bbb", locator.getSingleDimensionValue("$aaa"));
    assertEquals(null, locator.getSingleDimensionValue("aaa"));
    assertEquals(null, locator.getSingleDimensionValue("~"));
  }

  @Test
  public void testStringRepresentation() {
    assertEquals("aaa:bbb", Locator.getStringLocator("aaa", "bbb"));
    assertEquals("a:b,c:d", Locator.getStringLocator("a", "b", "c", "d"));
    assertEquals("c:d,a:b", Locator.getStringLocator("c", "d", "a", "b"));

    //several values not supported yet
    //assertEquals("a:1,a:2", Locator.getStringLocator("a", "1", "a", "2"));
    //assertEquals("a:2,a:1", Locator.getStringLocator("a", "2", "a", "1"));

    assertEquals("c:(1:2),a:(,,)", Locator.getStringLocator("c", "1:2", "a", ",,"));

    assertEquals("c:d,a:b", new Locator("c:d,a:b").getStringRepresentation());
    assertEquals("a:b,c:d", new Locator("a:b,c:d").getStringRepresentation());

    Locator locator = new Locator("a:b,c:d");
    locator.setDimension("c", "y");
    locator.setDimension("a", "x");
    assertEquals("a:x,c:y", locator.getStringRepresentation());

    locator = new Locator("c:d,a:b");
    locator.setDimension("c", "y");
    locator.setDimension("a", "x");
    assertEquals("c:y,a:x", locator.getStringRepresentation());
  }

  @Test(expectedExceptions = LocatorProcessException.class)
  public void testCustomNamesErrors() {
    new Locator("~aa:b", true, "~a", "~aaa", "-");
  }

  @DataProvider(name = "invalid-complex-values")
  public String[][] getInvalidComplexValues() {
    return new String[][] {
        {"name:("},
        {"name:(value"},
        {":value"},
        {"name:value,:value2"},
        {"name:value,(a:b)"},
        {"name:(val)a"},
        {"-:x"},
        {"a-b:y"}
    };
  }

  @Test(dataProvider = "invalid-complex-values", expectedExceptions = LocatorProcessException.class)
  public void testComplexValuesParsingErrors(String value) {
    new Locator(value);
  }

  @DataProvider(name = "invalid-complex-values-extendedMode")
  public String[][] getInvalidComplexValuesExtendedMode() {
    return new String[][] {
        {"a(b)(c),d"},
        {"a,b(a ,( b)"},
        {"+"},
        {"$a"},
        {"a$b"}
    };
  }

  @Test(dataProvider = "invalid-complex-values-extendedMode", expectedExceptions = LocatorProcessException.class)
  public void testComplexValuesParsingErrorsExtendedMode(String value) {
    new Locator(value, true, null);
  }

  @DataProvider(name = "valid-complex-values-extendedMode")
  public String[][] getValidComplexValuesExtendedMode() {
    return new String[][]{
      {"-"},
      {"a-b(-)"},
      {"a-b"},
      {"a-b:ccc"},
      {"a-b:(ccc-ddd)"}
    };
  }

  @Test(dataProvider = "valid-complex-values-extendedMode")
  public void testComplexValuesParsingNoErrorsExtendedMode(String value) {
    new Locator(value, true, null);
  }

  static <E extends Throwable> void checkException(String locatorText, @NotNull Class<E> exception) {
    //noinspection ThrowableResultOfMethodCallIgnored
    BaseFinderTest.checkException(exception, () -> new Locator(locatorText), "creating locator for text '" + locatorText + "'");
  }

  void check(String locatorText, boolean isSingleValue, String singleValue, @Nullable String... dimensions) {
    check(new Locator(locatorText), isSingleValue, singleValue, dimensions);
  }

  void check(Locator locator, boolean isSingleValue, String singleValue, @Nullable String... dimensions) {
    assertEquals("is single value", isSingleValue, locator.isSingleValue());
    assertEquals("single value", singleValue, locator.getSingleValue());
    if (dimensions == null){
      assertEquals("dimensions count", locator.getDimensionsCount(), 0);
      return;
    }

    assertTrue("dimensions passed are invalid - should be [name, value], ...", dimensions.length % 2 == 0);
    assertEquals("dimensions count is wrong. Actual dimensions: " + locator.getDefinedDimensions(), locator.getDimensionsCount(), dimensions.length / 2);

    String previousName = null;
    int numberInCurrentName = 0;
    for (int i = 0; i < dimensions.length; i += 2) {
      @NotNull String name = dimensions[i];
      @Nullable String value = dimensions[i + 1];

      if (name.equals(previousName)) {
        numberInCurrentName++;
      } else {
        numberInCurrentName = 0;
      }
      previousName = name;

      if (numberInCurrentName == 0) {
        assertEquals("dimension value '" + name + "'", value, locator.getSingleDimensionValue(name));
      }

      List<String> actualValues = locator.getDimensionValue(name);
      assertFalse("dimension exists '" + name + "'", actualValues.isEmpty());
      assertEquals("dimension value '" + name + "'[" + numberInCurrentName + "]", value, actualValues.get(numberInCurrentName));
    }
  }
}
