package water.codegen.java;

import java.lang.reflect.Modifier;
import water.codegen.*;

import static water.codegen.util.ArrayUtils.append;


/**
 * FIXME: WIP
 */
public class MethodCodeGenerator extends SimpleCodeGenerator<MethodCodeGenerator> {

  public static MethodCodeGenerator codegen(String name) {
    return new MethodCodeGenerator(name);
  }

  final String name;

  private int modifiers;
  private Class[] paramTypes;
  private String[] paramNames;
  private Class returnType = void.class;
  private boolean override = false;
  private CodeGeneratorB body;
  private boolean parantheses = true;

  /** The method owner */
  private ClassCodeGenerator ccg;

  protected MethodCodeGenerator(String name) {
    this.name = name;
  }

  public MethodCodeGenerator withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return self();
  }

  public MethodCodeGenerator withReturnType(Class returnType) {
    this.returnType = returnType;
    return self();
  }

  public MethodCodeGenerator withBody(CodeGeneratorB body) {
    assert body != null : "Body generator should be not null";
    this.body = body;
    return self().withParentheses(true);
  }

  public MethodCodeGenerator withBody(final JCodeSB body) {
    // Add a new generator generating body of the method directly
    return withBody(new CodeGeneratorB() {
      @Override
      public void generate(JCodeSB out) {
        out.p(body);
      }
    });
  }

  public MethodCodeGenerator withOverride(boolean flag) {
    this.override = flag;
    return self();
  }

  public MethodCodeGenerator withParams(Class type, String name) {
    this.paramTypes = append(this.paramTypes, type);
    this.paramNames = append(this.paramNames, name);
    return self();
  }

  public Class getReturnType() {
    return returnType;
  }

  public boolean isCtor() { return returnType == null; }

  public MethodCodeGenerator withParentheses(boolean flag) {
    this.parantheses = flag;
    return self();
  }

  @Override
  public void generate(JCodeSB out) {
    // Output method preamble
    if (override) out.p("@Override ");
    out.p(Modifier.toString(modifiers)).p(' ');
    if (!isCtor()) {
      out.pj(returnType).p(' ');
    }
    // Append method name and types
    printMethodParams(out.p(name).p('('), paramTypes, paramNames).p(") ");
    if (parantheses) out.p("{").ii(2).nl();
    // Generate method body
    body.generate(out);
    // Close method
    if (parantheses) {
      out.di(2).nl().p("}");
    }
    out.p(" // End of method ").p(name).nl(2);
  }

  ClassCodeGenerator ccg() {
    return ccg;
  }

  void setCcg(ClassCodeGenerator ccg) {
    this.ccg = ccg;
  }

  @Override
  final public ClassGenContainer classContainer(CodeGenerator caller) {
    return ccg.classContainer(caller);
  }

  static JCodeSB printMethodParams(JCodeSB sb, Class[] types, String[] names) {
    assert types == null && names == null || types.length == names.length : "Length of types does not match length of names";
    if (types != null) {
      for (int i = 0; i < types.length; i++) {
        if (i > 0)
          sb.p(", ");
        sb.pj(types[i]).p(' ').p(names[i]);
      }
    }
    return sb;
  }

  @Override
  public MethodCodeGenerator build() {
    if (body != null) body.build();
    return super.build();
  }
}
