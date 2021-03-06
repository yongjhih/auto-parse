/*
 * Copyright (C) 2012 Google, Inc.
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
package auto.parse.processor;

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import auto.parse.AutoParse;

/**
 * Javac annotation processor (compiler plugin) for value types; user code never references this
 * class.
 *
 * @see auto.parse.AutoParse
 * @author Éamonn McManus
 */
@AutoService(Processor.class)
@SupportedOptions(EclipseHack.ENABLING_OPTION)
public class AutoParseProcessor extends AbstractProcessor {
  private static final boolean SILENT = true;

  public AutoParseProcessor() {}

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(AutoParse.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private void note(String msg) {
    if (!SILENT) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
    }
  }

  @SuppressWarnings("serial")
  // CHECKSTYLE:OFF:WhitespaceAround
  private static class CompileException extends Exception {}
  // CHECKSTYLE:ON

  /**
   * Issue a compilation error. This method does not throw an exception, since we want to
   * continue processing and perhaps report other errors. It is a good idea to introduce a
   * test case in CompilationErrorsTest for any new call to reportError(...) to ensure that we
   * continue correctly after an error.
   */
  private void reportError(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }

  /**
   * Issue a compilation error and abandon the processing of this class. This does not prevent
   * the processing of other classes.
   */
  private void abortWithError(String msg, Element e) throws CompileException {
    reportError(msg, e);
    throw new CompileException();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    boolean claimed = (annotations.size() == 1
        && annotations.iterator().next().getQualifiedName().toString().equals(
            AutoParse.class.getName()));
    if (claimed) {
      process(roundEnv);
      return true;
    } else {
      return false;
    }
  }

  private void process(RoundEnvironment roundEnv) {
    Collection<? extends Element> annotatedElements =
        roundEnv.getElementsAnnotatedWith(AutoParse.class);
    Collection<? extends TypeElement> types = ElementFilter.typesIn(annotatedElements);
    for (TypeElement type : types) {
      try {
        processType(type);
      } catch (CompileException e) {
        // We abandoned this type, but continue with the next.
      } catch (RuntimeException e) {
        // Don't propagate this exception, which will confusingly crash the compiler.
        reportError("@AutoParse processor threw an exception: " + e, type);
      }
    }
  }

  private String generatedClassName(TypeElement type, String prefix) {
    String name = type.getSimpleName().toString();
    while (type.getEnclosingElement() instanceof TypeElement) {
      type = (TypeElement) type.getEnclosingElement();
      name = type.getSimpleName() + "_" + name;
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    String dot = pkg.isEmpty() ? "" : ".";
    return pkg + dot + prefix + name;
  }

  private String generatedSubclassName(TypeElement type) {
    return generatedClassName(type, "AutoParse_");
  }

  private static String simpleNameOf(String s) {
    if (s.contains(".")) {
      return s.substring(s.lastIndexOf('.') + 1);
    } else {
      return s;
    }
  }

  // Return the name of the class, including any enclosing classes but not the package.
  private static String classNameOf(TypeElement type) {
    String name = type.getQualifiedName().toString();
    String pkgName = TypeSimplifier.packageNameOf(type);
    if (!pkgName.isEmpty()) {
      return name.substring(pkgName.length() + 1);
    } else {
      return name;
    }
  }

  // This is just because I hate typing    "...\n" +   all the time.
  private static String concatLines(String... lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append("\n");
    }
    return sb.toString();
  }

  // The code below uses a small templating language. This is not hugely readable, but is much more
  // so than sb.append(this).append(that) with ifs and fors scattered around everywhere.
  // See the Template class for an explanation of the various constructs.
  private static final String TEMPLATE_STRING = concatLines(
    // CHECKSTYLE:OFF:OperatorWrap
    // Package declaration
    "$[pkg?package $[pkg];\n]",

    // Imports
    "$[imports:i||import $[i];\n]",

    "import com.parse.ParseClassName;",
    "import java.util.Date;",
    "import org.json.JSONArray;",
    "import org.json.JSONObject;",
    "import java.util.List;",
    "import java.util.Map;",
    "import com.parse.ParseFile;",
    "import com.parse.ParseGeoPoint;",
    "import com.parse.ParseObject;",
    "import com.parse.ParseQuery;",
    "import com.parse.ParseRelation;",
    "import com.parse.ParseUser;\n",

    // Class declaration

    "@ParseClassName(\"$[origclass]\")",
    "public class $[subclass]$[formaltypes] extends $[origclass]$[actualtypes] {",

    // Fields
    //"$[props:p||    public $[p.type] $[p];\n]",

    "    ParseObject parseObject;\n",

    // Constructor
    "    public $[subclass]() {",
    "        this.parseObject = this;",
    "    }\n",

    "    public $[subclass](ParseObject parseObject) {",
    "        this.parseObject = parseObject;",
    "    }\n",

    "    public $[subclass](\n            $[getters:p|,\n            |$[p.type] $[p.getField]]) {",
    "        this();",
    "$[setters:p|\n|        $[p]($[p.getField]);]",
    "    }\n",

    "    private Object _get(String key, Object defValue) {",
    "        return parseObject.get(key);",
    "    }\n",

    "    private boolean _get(String key, Boolean defValue) {",
    "        return parseObject.getBoolean(key);",
    "    }\n",

    "    private byte[] _get(String key, byte[] defValue) {",
    "        return parseObject.getBytes(key);",
    "    }\n",

    "    private Date _get(String key, Date defValue) {",
    "        return parseObject.getDate(key);",
    "    }\n",

    "    private double _get(String key, Double defValue) {",
    "        return parseObject.getDouble(key);",
    "    }\n",

    "    private int _get(String key, Integer defValue) {",
    "        return parseObject.getInt(key);",
    "    }\n",

    "    private JSONArray _get(String key, JSONArray defValue) {",
    "        return parseObject.getJSONArray(key);",
    "    }\n",

    "    private JSONObject _get(String key, JSONObject defValue) {",
    "        return parseObject.getJSONObject(key);",
    "    }\n",

    "    @SuppressWarnings(\"unchecked\")",
    "    private <T> List<T> _get(String key, List<T> defValue) {",
    "        return (List<T>) parseObject.getList(key);",
    "    }\n",

    "    @SuppressWarnings(\"unchecked\")",
    "    private <V> Map<String, V> _get(String key, Map<String, V> defValue) {",
    "        return (Map<String, V>) parseObject.getMap(key);",
    "    }\n",

    "    @SuppressWarnings(\"unchecked\")",
    "    private <T extends ParseObject> ParseRelation<T> _get(String key, ParseRelation<T> defValue) {",
    "        return (ParseRelation<T>) parseObject.getRelation(key);",
    "    }\n",

    "    private long _get(String key, Long defValue) {",
    "        return parseObject.getLong(key);",
    "    }\n",

    "    private Number _get(String key, Number defValue) {",
    "        return parseObject.getNumber(key);",
    "    }\n",

    "    private ParseFile _get(String key, ParseFile defValue) {",
    "        return parseObject.getParseFile(key);",
    "    }\n",

    "    private ParseGeoPoint _get(String key, ParseGeoPoint defValue) {",
    "        return parseObject.getParseGeoPoint(key);",
    "    }\n",

    "    private <T extends ParseObject> T _get(String key, T defValue) {",
    "        return (T) parseObject.getParseObject(key);",
    "    }\n",

    "    private ParseUser _get(String key, ParseUser defValue) {",
    "        return parseObject.getParseUser(key);",
    "    }\n",

    "    private String _get(String key, String defValue) {",
    "        return parseObject.getString(key);",
    "    }",

    // Property getters
    "$[getters:p|\n|\n    @Override",
    "    $[p.access]$[p.type] $[p]($[p.getTypeArgs]) {",
    //"        if (int.class.equals($[p.type].class)) {",
    //"            return parseObject.getInt(key);",
    //"        }",
    //"        if (boolean.class.equals($[p.type].class)) {",
    //"            return parseObject.getBoolean(key);",
    //"        }",
    //"        if (ParseObject.class.equals($[p.type].class)) {",
    //"            return parseObject.getParseObject(key);",
    //"        }",
    //"        if (Boolean.class.equals($[p.type].class)) {",
    //"            return parseObject.getBoolean(key);",
    //"        }",
    //"        if (Bytes.class.equals($[p.type].class)) {",
    //"            return parseObject.getBytes(key);",
    //"        }",
    //"        if (Date.class.equals($[p.type].class)) {",
    //"            return parseObject.getDate(key);",
    //"        }",
    //"        if (Double.class.equals($[p.type].class)) {",
    //"            return parseObject.getDouble(key);",
    //"        }",
    //"        if (Int.class.equals($[p.type].class)) {",
    //"            return parseObject.getInt(key);",
    //"        }",
    //"        if (JSONArray.class.equals($[p.type].class)) {",
    //"            return parseObject.getJSONArray(key);",
    //"        }",
    //"        if (JSONObject.class.equals($[p.type].class)) {",
    //"            return parseObject.getJSONObject(key);",
    //"        }",
    //"        if (List.class.equals($[p.type].class)) {",
    //"            return (List<T>) parseObject.getList(key);",
    //"        }",
    //"        if (Map.class.equals($[p.type].class)) {",
    //"            return (Map<String, V>) parseObject.getMap(key);",
    //"        }",
    //"        if (Relation.class.equals($[p.type].class)) {",
    //"            return (ParseRelation<T>) parseObject.getRelation(key);",
    //"        }",
    //"        if (Long.class.equals($[p.type].class)) {",
    //"            return parseObject.getLong(key);",
    //"        }",
    //"        if (Number.class.equals($[p.type].class)) {",
    //"            return parseObject.getNumber(key);",
    //"        }",
    //"        if (ParseFile.class.equals($[p.type].class)) {",
    //"            return parseObject.getParseFile(key);",
    //"        }",
    //"        if (ParseGeoPoint.class.equals($[p.type].class)) {",
    //"            return parseObject.getParseGeoPoint(key);",
    //"        }",
    //"        if (ParseObject.class.equals($[p.type].class)) {",
    //"            return parseObject.getParseObject(key);",
    //"        }",
    //"        if (ParseUser.class.equals($[p.type].class)) {",
    //"            return parseObject.getParseUser(key);",
    //"        }",
    //"        if (String.class.equals($[p.type].class)) {",
    //"            return parseObject.getString(key);",
    //"        }]]",
    "        return _get(\"$[p.getField]\", $[p.getDefalutValue]);",
    "    }]",

    // Property setters
    "$[setters:p|\n|\n    @Override",
    "    $[p.access]$[origclass] $[p]($[p.getTypeArgs]) {",
    "        if ($[p.getArgs] == null) return this;",
    "        put(\"$[p.getField]\", $[p.getArgs]);",
    "        return this;",
    "    }]",

    // toString()
    "$[toString?\n    @Override",
    "    public String toString() {",
    "        return \"$[simpleclassname]{\"$[getters?\n                + \"]" +
    "$[getters:p|\n                + \", |" +
                        "$[p.getField]=\" + $[p.array?[$[Arrays].toString($[p]())][$[p]()]]]",
    "                + \"}\";",
    "    }]",

    // parcelable
    "$[parcelable?\n\n",
    "    public static final android.os.Parcelable.Creator<$[origclass]> CREATOR = new android.os.Parcelable.Creator<$[origclass]>() {",
    "        @Override public $[origclass] createFromParcel(android.os.Parcel in) {",
    "            return new $[subclass](in);",
    "        }",
    "        @Override public $[origclass][] newArray(int size) {",
    "            return new $[origclass][size];",
    "        }",
    "    };",
    "",
    "    private final static java.lang.ClassLoader CL = $[subclass].class.getClassLoader();",
    "",
    "    private $[subclass](android.os.Parcel in) {",
    "        this(\n            $[getters:p|,\n            |($[p.castType]) in.readValue(CL)]);",
    "    }",
    "",
    "    @Override public void writeToParcel(android.os.Parcel dest, int flags) {",
    "$[getters:p||        dest.writeValue($[p]());\n]",
    "    }",
    "",
    "    @Override public int describeContents() {",
    "        return 0;",
    "    }",
    "",
    "    public static ParseQuery<$[origclass]> getQuery() {",
    "        return ParseQuery.getQuery($[origclass].class);",
    "    }",
    "",
    "    public static ParseQuery<$[subclass]> getAutoQuery() {",
    "        return ParseQuery.getQuery($[subclass].class);",
    "    }",
    "]",

    "}"
    // CHECKSTYLE:ON
  );
  private static final Template template = Template.compile(TEMPLATE_STRING);

  static class Property {
    private final ExecutableElement method;
    private final String type;
    private final Map<String, Object> vars;

    Property(ExecutableElement method, String type, Map<String, Object> vars) {
      this.method = method;
      this.type = type;
      this.vars = vars;
    }

    @Override
    public String toString() {
      return method.getSimpleName().toString();
    }

    public String getField() {
      String field = method.getSimpleName().toString().replaceFirst("^set", "")
          .replaceFirst("^get", "");
      return field.substring(0, 1).toLowerCase() + field.substring(1);
    }

    public String getTypeArgs() {
      return formalTypeArgsString(method, (TypeSimplifier) vars.get("typeSimplifier"));
    }

    public String getArgs() {
      return formalArgsString(method);
    }

    public String getDefalutValue() {
        String defaultValue = formalArgsString(method);
        if (defaultValue.isEmpty()) {
            defaultValue = "(" + type + ") null";
        }
        return defaultValue;
    }

    TypeElement owner() {
      return (TypeElement) method.getEnclosingElement();
    }

    public String type() {
      return type;
    }

    // That wouldn't be necessary if we supported Java 7+. Oh well.
    public String castType() {
      return primitive() ? box(method.getReturnType().getKind()) : type();
    }

    private String box(TypeKind kind) {
      switch(kind) {
        case BOOLEAN: return "Boolean";
        case BYTE: return "Byte";
        case SHORT: return "Short";
        case INT: return "Integer";
        case LONG: return "Long";
        case CHAR: return "Character";
        case FLOAT: return "Float";
        case DOUBLE: return "Double";
        default: throw new RuntimeException("Found a new Primitive type on the Java platform. "
            + "Go ahead and claim " + kind + " yours");
      }
    }

    public boolean primitive() {
      return method.getReturnType().getKind().isPrimitive();
    }

    public boolean array() {
      return method.getReturnType().getKind() == TypeKind.ARRAY;
    }

    public boolean nullable() {
      for (AnnotationMirror annotationMirror : method.getAnnotationMirrors()) {
        String name = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
        if (name.equals("Nullable")) {
          return true;
        }
      }
      return false;
    }

    private static final Template PRIMITIVE_EQUALS_TEMPLATE =
        Template.compile("this.$[p] == that.$[p]()");
    private static final Template ARRAY_EQUALS_TEMPLATE =
        Template.compile("$[Arrays].equals(this.$[p], "
            + "(that instanceof $[subclass]) ? (($[subclass]) that).$[p] : that.$[p]())");
    private static final Template FLOAT_EQUALS_TEMPLATE = Template.compile(
        "Float.floatToIntBits(this.$[p]) == Float.floatToIntBits(that.$[p]())");
    private static final Template DOUBLE_EQUALS_TEMPLATE = Template.compile(
        "Double.doubleToLongBits(this.$[p]) == Double.doubleToLongBits(that.$[p]())");
    // CHECKSTYLE:OFF:OperatorWrap
    private static final Template OBJECT_EQUALS_TEMPLATE = Template.compile(
        "$[p.nullable?" +
          "(this.$[p] == null) ? (that.$[p]() == null) : ]" +
          "this.$[p].equals(that.$[p]())");
    // CHECKSTYLE:ON

    /**
     * A string representing an expression that compares this property with the same property
     * in another variable called "that" whose type is the class marked {@code @AutoParse}.
     */
    public String equalsThatExpression() {
      // If the templating language had a case statement we wouldn't need this function, but the
      // language is unreadable enough as it is.
      Template template;
      switch (method.getReturnType().getKind()) {
        case BYTE:
        case SHORT:
        case CHAR:
        case INT:
        case LONG:
        case BOOLEAN:
          template = PRIMITIVE_EQUALS_TEMPLATE;
          break;
        case FLOAT:
          template = FLOAT_EQUALS_TEMPLATE;
          break;
        case DOUBLE:
          template = DOUBLE_EQUALS_TEMPLATE;
          break;
        case ARRAY:
          template = ARRAY_EQUALS_TEMPLATE;
          break;
        default:
          template = OBJECT_EQUALS_TEMPLATE;
          break;
      }
      Map<String, Object> newVars = new TreeMap<String, Object>(vars);
      newVars.put("p", this);
      return template.rewrite(newVars);
    }

    /**
     * A string representing an expression that is the hashCode of this property.
     */
    public String hashCodeExpression() {
      switch (method.getReturnType().getKind()) {
        case BYTE:
        case SHORT:
        case CHAR:
        case INT:
          return this.toString();
        case LONG:
          return "(" + this + " >>> 32) ^ " + this;
        case FLOAT:
          return "Float.floatToIntBits(" + this + ")";
        case DOUBLE:
          return "(Double.doubleToLongBits(" + this + ") >>> 32) ^ "
              + "Double.doubleToLongBits(" + this + ")";
        case BOOLEAN:
          return this + " ? 1231 : 1237";
        case ARRAY:
          return vars.get("Arrays") + ".hashCode(" + this + ")";
        default:
          if (nullable()) {
            return "(" + this + " == null) ? 0 : " + this + ".hashCode()";
          } else {
            return this + ".hashCode()";
          }
      }
    }

    public String access() {
      Set<Modifier> mods = method.getModifiers();
      if (mods.contains(Modifier.PUBLIC)) {
        return "public ";
      } else if (mods.contains(Modifier.PROTECTED)) {
        return "protected ";
      } else {
        return "";
      }
    }
  }

  private static boolean isJavaLangObject(TypeElement type) {
    return type.getSuperclass().getKind() == TypeKind.NONE && type.getKind() == ElementKind.CLASS;
  }

  private static boolean isToStringOrEqualsOrHashCode(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    return ((name.equals("toString") || name.equals("hashCode"))
              && method.getParameters().isEmpty())
        || (name.equals("equals") && method.getParameters().size() == 1
              && method.getParameters().get(0).asType().toString().equals("java.lang.Object"));
  }

  private void findLocalAndInheritedMethods(TypeElement type, List<ExecutableElement> methods) {
    note("Looking at methods in " + type);
    Types typeUtils = processingEnv.getTypeUtils();
    Elements elementUtils = processingEnv.getElementUtils();
    for (TypeMirror superInterface : type.getInterfaces()) {
      findLocalAndInheritedMethods((TypeElement) typeUtils.asElement(superInterface), methods);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      findLocalAndInheritedMethods(
          (TypeElement) typeUtils.asElement(type.getSuperclass()), methods);
    }
    // Add each method of this class, and in so doing remove any inherited method it overrides.
    // This algorithm is quadratic in the number of methods but it's hard to see how to improve
    // that while still using Elements.overrides.
    List<ExecutableElement> theseMethods = ElementFilter.methodsIn(type.getEnclosedElements());
    eclipseHack().sortMethodsIfSimulatingEclipse(theseMethods);
    for (ExecutableElement method : theseMethods) {
      if (!method.getModifiers().contains(Modifier.PRIVATE)) {
        boolean alreadySeen = false;
        for (Iterator<ExecutableElement> methodIter = methods.iterator(); methodIter.hasNext();) {
          ExecutableElement otherMethod = methodIter.next();
          if (elementUtils.overrides(method, otherMethod, type)) {
            methodIter.remove();
          } else if (method.getSimpleName().equals(otherMethod.getSimpleName())
              && method.getParameters().equals(otherMethod.getParameters())) {
            // If we inherit this method on more than one path, we don't want to add it twice.
            alreadySeen = true;
          }
        }
        if (!alreadySeen) {
          methods.add(method);
        }
      }
    }
  }

  private void processType(TypeElement type) throws CompileException {
    AutoParse autoParse = type.getAnnotation(AutoParse.class);
    if (autoParse == null) {
      // This shouldn't happen unless the compilation environment is buggy,
      // but it has happened in the past and can crash the compiler.
      abortWithError("annotation processor for @AutoParse was invoked with a type that "
          + "does not have that annotation; this is probably a compiler bug", type);
    }
    if (type.getKind() != ElementKind.CLASS) {
      abortWithError("@" + AutoParse.class.getName() + " only applies to classes", type);
    }
    if (ancestorIsAndroidAutoParse(type)) {
      abortWithError("One @AutoParse class may not extend another", type);
    }
    Map<String, Object> vars = new TreeMap<String, Object>();
    vars.put("type", type);
    vars.put("pkg", TypeSimplifier.packageNameOf(type));
    vars.put("origclass", classNameOf(type));
    vars.put("simpleclassname", simpleNameOf(classNameOf(type)));
    vars.put("formaltypes", formalTypeString(type));
    vars.put("actualtypes", actualTypeString(type));
    vars.put("wildcardtypes", wildcardTypeString(type));
    vars.put("subclass", simpleNameOf(generatedSubclassName(type)));
    vars.put("cacheHashCode", autoParse.cacheHashCode());
    defineVarsForType(type, vars);
    String text = template.rewrite(vars);
    writeSourceFile(generatedSubclassName(type), text, type);
  }

  private void defineVarsForType(TypeElement type, Map<String, Object> vars)
      throws CompileException {
    List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
    findLocalAndInheritedMethods(type, methods);
    vars.putAll(objectMethodsToGenerate(methods));
    dontImplementAnnotationEqualsOrHashCode(type, vars);
    List<ExecutableElement> toImplement = methodsToImplement(methods, vars);
    Set<TypeMirror> types = new HashSet<TypeMirror>();
    types.addAll(returnTypesOf(toImplement));
    TypeMirror javaUtilArrays = getTypeMirror(Arrays.class);
    if (containsArrayType(types)) {
      // If there are array properties then we will be referencing java.util.Arrays.
      // Arrange to import it unless that would introduce ambiguity.
      types.add(javaUtilArrays);
    }
    String pkg = TypeSimplifier.packageNameOf(type);
    TypeSimplifier typeSimplifier = new TypeSimplifier(processingEnv.getTypeUtils(), pkg, types);
    vars.put("typeSimplifier", typeSimplifier);
    vars.put("imports", typeSimplifier.typesToImport());
    vars.put("Arrays", typeSimplifier.simplify(javaUtilArrays));
    List<Property> getters = new ArrayList<Property>();
    List<Property> setters = new ArrayList<Property>();
    for (ExecutableElement method : toImplement) {
      String propType = typeSimplifier.simplify(method.getReturnType());
      Property prop = new Property(method, propType, vars);
      if (method.getSimpleName().toString().startsWith("get")) {
        getters.add(prop);
      } else if (method.getSimpleName().toString().startsWith("set")) {
        setters.add(prop);
      }
    }
    // If we are running from Eclipse, undo the work of its compiler which sorts methods.
    eclipseHack().reorderProperties(getters);
    eclipseHack().reorderProperties(setters);
    vars.put("getters", getters);
    vars.put("setters", setters);
    vars.put("serialVersionUID", getSerialVersionUID(type));

    TypeMirror parcelable = getTypeMirror("android.os.Parcelable");
    vars.put("parcelable", processingEnv.getTypeUtils().isAssignable(type.asType(), parcelable));
  }

  private Set<TypeMirror> returnTypesOf(List<ExecutableElement> methods) {
    HashSet<TypeMirror> returnTypes = new HashSet<TypeMirror>();
    for (ExecutableElement method : methods) {
      returnTypes.add(method.getReturnType());
    }
    return returnTypes;
  }

  private static boolean containsArrayType(Set<TypeMirror> types) {
    for (TypeMirror type : types) {
      if (type.getKind() == TypeKind.ARRAY) {
        return true;
      }
    }
    return false;
  }

  private void dontImplementAnnotationEqualsOrHashCode(TypeElement type, Map<String, ?> vars) {
    TypeMirror javaLangAnnotationAnnotation = getTypeMirror(Annotation.class);
    Types typeUtils = processingEnv.getTypeUtils();
    if (typeUtils.isAssignable(type.asType(), javaLangAnnotationAnnotation)) {
      boolean equals = (Boolean) vars.get("equals");
      boolean hashCode = (Boolean) vars.get("hashCode");
      if (equals || hashCode) {
        String bad = equals
            ? (hashCode ? "equals(Object) and hashCode()" : "equals(Object)")
            : "hashCode()";
        reportError("The implementation of " + bad + " that would be generated for this @AutoParse "
            + "class would not obey the contract of " + bad + " in " + Annotation.class.getName(),
            type);
      }
    }
  }

  /**
   * Given a list of all methods defined in or inherited by a class, returns a map with keys
   * "toString", "equals", "hashCode" and corresponding value true if that method should be
   * generated.
   */
  private static Map<String, Boolean> objectMethodsToGenerate(List<ExecutableElement> methods) {
    Map<String, Boolean> vars = new TreeMap<String, Boolean>();
    // The defaults here only come into play when an ancestor class doesn't exist.
    // Compilation will fail in that case, but we don't want it to crash the compiler with
    // an exception before it does. If all ancestors do exist then we will definitely find
    // definitions of these three methods (perhaps the ones in Object) so we will overwrite these:
    vars.put("equals", false);
    vars.put("hashCode", false);
    vars.put("toString", false);
    for (ExecutableElement method : methods) {
      if (isToStringOrEqualsOrHashCode(method)) {
        boolean canGenerate = method.getModifiers().contains(Modifier.ABSTRACT)
            || isJavaLangObject((TypeElement) method.getEnclosingElement());
        vars.put(method.getSimpleName().toString(), canGenerate);
      }
    }
    assert vars.size() == 3;
    return vars;
  }

  private boolean isSuperType(TypeMirror type, TypeMirror origClass) {
    if (origClass.equals(type)) {
      return true;
    }

    List<? extends TypeMirror> superTypes = processingEnv.getTypeUtils().directSupertypes(origClass);
    for (TypeMirror superType : superTypes) {
      if (superType.equals(type)) {
        return true;
      }
    }
    return false;
  }

  private List<ExecutableElement> methodsToImplement(List<ExecutableElement> methods,
      Map<String, Object> vars) throws CompileException {
    List<ExecutableElement> toImplement = new ArrayList<ExecutableElement>();
    boolean errors = false;
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)
          && !isToStringOrEqualsOrHashCode(method) && !isFromParcelable(method)) {
        if (method.getParameters().isEmpty() && method.getReturnType().getKind() != TypeKind.VOID &&
            (method.getSimpleName().toString().startsWith("get"))) {
          if (isReferenceArrayType(method.getReturnType())) {
            reportError("An @AutoParse class cannot define an array-valued property unless it is "
                + "a byte array", method);
            errors = true;
          }

          toImplement.add(method);
        } else if ((method.getParameters().size() == 1) &&
            (method.getReturnType().getKind() != TypeKind.VOID) &&
            (method.getSimpleName().toString().startsWith("get"))) {
          if (!method.getParameters().get(0).asType().equals(method.getReturnType())) {
            reportError("An @AutoParse class cannot define an default-value that is difference" +
                "to return-value ", method);
            errors = true;
          }

          toImplement.add(method);
        } else if ((method.getParameters().size() == 1) &&
            isSuperType(method.getReturnType(), ((TypeElement) vars.get("type")).asType()) &&
            (method.getSimpleName().toString().startsWith("set"))) {
          if (isReferenceArrayType(method.getParameters().get(0).asType())) {
            reportError("An @AutoParse class cannot define an array-valued property unless it is "
                + "a byte array", method);
            errors = true;
          }

          toImplement.add(method);
        } else {
          reportError("@AutoParse classes cannot have abstract methods other than property getters and setters",
              method);
          errors = true;
        }
      }
    }
    if (errors) {
      throw new CompileException();
    }
    return toImplement;
  }

  private boolean isFromParcelable(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    boolean isDescribeContents = name.equals("describeContents") && method.getParameters().isEmpty()
        && method.getReturnType().toString().equals("int");
    boolean isWriteToParcel = name.equals("writeToParcel") && method.getParameters().size() == 2
        && method.getReturnType().toString().equals("void")
        && method.getParameters().get(0).asType().toString().equals("android.os.Parcel")
        && method.getParameters().get(1).asType().toString().equals("int");
    return isDescribeContents || isWriteToParcel;
  }

  private static boolean isReferenceArrayType(TypeMirror type) {
    return type.getKind() == TypeKind.ARRAY
        && (((ArrayType) type).getComponentType().getKind() != TypeKind.BYTE);
  }

  private void writeSourceFile(String className, String text, TypeElement originatingType) {
    try {
      note(text);
      JavaFileObject sourceFile =
          processingEnv.getFiler().createSourceFile(className, originatingType);
      Writer writer = sourceFile.openWriter();
      try {
        writer.write(text);
      } finally {
        writer.close();
      }
    } catch (IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Could not write generated class " + className + ": " + e);
    }
  }

  private boolean ancestorIsAndroidAutoParse(TypeElement type) {
    while (true) {
      TypeMirror parentMirror = type.getSuperclass();
      if (parentMirror.getKind() == TypeKind.NONE) {
        return false;
      }
      Types typeUtils = processingEnv.getTypeUtils();
      TypeElement parentElement = (TypeElement) typeUtils.asElement(parentMirror);
      if (parentElement.getAnnotation(AutoParse.class) != null) {
        return true;
      }
      type = parentElement;
    }
  }

  // Return a string like "1234L" if type instanceof Serializable and defines
  // serialVersionUID = 1234L, otherwise "".
  private String getSerialVersionUID(TypeElement type) {
    Types typeUtils = processingEnv.getTypeUtils();
    TypeMirror serializable = getTypeMirror(Serializable.class);
    if (typeUtils.isAssignable(type.asType(), serializable)) {
      List<VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
      for (VariableElement field : fields) {
        if (field.getSimpleName().toString().equals("serialVersionUID")) {
          Object value = field.getConstantValue();
          if (field.getModifiers().containsAll(Arrays.asList(Modifier.STATIC, Modifier.FINAL))
              && field.asType().getKind() == TypeKind.LONG
              && value != null) {
            return value + "L";
          } else {
            reportError(
                "serialVersionUID must be a static final long compile-time constant", field);
            break;
          }
        }
      }
    }
    return "";
  }

  private TypeMirror getTypeMirror(Class<?> c) {
    return getTypeMirror(c.getName());
  }

  private TypeMirror getTypeMirror(String className) {
    return processingEnv.getElementUtils().getTypeElement(className).asType();
  }

  // Why does TypeParameterElement.toString() not return this? Grrr.
  private static String typeParameterString(TypeParameterElement type) {
    String s = type.getSimpleName().toString();
    List<? extends TypeMirror> bounds = type.getBounds();
    if (bounds.isEmpty()) {
      return s;
    } else {
      s += " extends ";
      String sep = "";
      for (TypeMirror bound : bounds) {
        s += sep + bound;
        sep = " & ";
      }
      return s;
    }
  }

  private static String formalTypeString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "<";
      String sep = "";
      for (TypeParameterElement typeParameter : typeParameters) {
        s += sep + typeParameterString(typeParameter);
        sep = ", ";
      }
      return s + ">";
    }
  }

  private static String actualTypeString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "<";
      String sep = "";
      for (TypeParameterElement typeParameter : typeParameters) {
        s += sep + typeParameter.getSimpleName();
        sep = ", ";
      }
      return s + ">";
    }
  }

  // The @AutoParse type, with a ? for every type.
  private static String wildcardTypeString(TypeElement type) {
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "<";
      String sep = "";
      for (int i = 0; i < typeParameters.size(); i++) {
        s += sep + "?";
        sep = ", ";
      }
      return s + ">";
    }
  }

  private static String formalArgsString(ExecutableElement method) {
    List<? extends VariableElement> typeParameters = method.getParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "";
      String sep = "";
      for (VariableElement typeParameter : typeParameters) {
        s += sep + typeParameter.getSimpleName();
        sep = ", ";
      }
      return s;
    }
  }

  private static String formalTypeArgsString(ExecutableElement method, TypeSimplifier typeSimplifier) {
    List<? extends VariableElement> typeParameters = method.getParameters();
    if (typeParameters.isEmpty()) {
      return "";
    } else {
      String s = "";
      String sep = "";
      for (VariableElement typeParameter : typeParameters) {
        s += sep + typeSimplifier.simplify(typeParameter.asType()) + " " + typeParameter.getSimpleName();
        sep = ", ";
      }
      return s;
    }
  }

  private EclipseHack eclipseHack() {
    return new EclipseHack(processingEnv);
  }
}
