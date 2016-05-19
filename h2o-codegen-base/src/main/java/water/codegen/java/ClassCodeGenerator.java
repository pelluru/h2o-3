package water.codegen.java;

import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.InitializerDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import hex.genmodel.annotations.CG;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.codegen.JCodeSB;
import water.codegen.util.ArrayUtils;
import water.codegen.util.ReflectionUtils;

import static water.codegen.util.ArrayUtils.append;

/**
 * FIXME:
 */
public class ClassCodeGenerator extends CodeGeneratorPipeline<ClassCodeGenerator, CodeGeneratorB> {

  /** Name of class to generate. */
  final String name;
  /** Class modifiers - e.g., "public static" */
  int modifiers;
  /** Extend given class */
  Class extendClass;
  /** Implements interface */
  Class[] interfaces;
  /** Annotation generators */   // FIXME remove
  JCodeSB[] acgs;

  private CompilationUnitGenerator cug;

  ClassCodeGenerator(String className) {
    this.name = className;
  }

  public ClassCodeGenerator withMixin(Object source, Class... mixins) {
    return withMixin(source, false, mixins);
  }

  public ClassCodeGenerator withMixin(Class mixin, Class... mixins) {
    return withMixin(null, false, ArrayUtils.append(mixins, mixin));
  }

  public ClassCodeGenerator withMixin(Object source, boolean includeParent, Class... mixins) {
    // Copy all fields and generate their content based on delegation to source object
    for (Class mixin : mixins) {
      for (Field f : ReflectionUtils.findAllFields(mixin, includeParent)) {
        // Process only fields
        CG.Delegate delegAnno = f.getAnnotation(CG.Delegate.class);
        CG.Manual manualAnno = f.getAnnotation(CG.Manual.class);

        if (delegAnno != null) {
          // Skip field value generation
          boolean skip = !delegAnno.when().equals(CG.NA) && ReflectionUtils.getValue(source, delegAnno.when(), Boolean.class);

          FieldCodeGenerator fcg = new FieldCodeGenerator(f);
          // Append comment from @CG annotation
          if (!delegAnno.comment().equals(CG.NA)) {
            fcg.withComment(delegAnno.comment());
          }
          // Append value from @CG annotation or leave empty for manual filling
          if (!delegAnno.target().equals(CG.NA)) {
            Class fieldType = f.getType();
            Object value = skip ? ReflectionUtils.getValue(null, f, fieldType) : ReflectionUtils.getValue(source, delegAnno.target(), fieldType);
            fcg.withValue(JCodeGenUtil.VALUE(value, fieldType));
            System.out.println(f.getName() + " : " + fieldType.getCanonicalName() + " :=: " + value + ":" + (value != null ? value.getClass().getCanonicalName() : null));
          }
          withField(fcg);
        } else if (manualAnno != null) {
          FieldCodeGenerator fcg = new FieldCodeGenerator(f);
          if (!manualAnno.comment().equals(CG.NA)) {
            fcg.withComment(manualAnno.comment());
          }
          withField(fcg);
        }
      }

      // Now try to find mixin source code and inject all remaining method code
      ClassLoader cl = ClassCodeGenerator.class.getClassLoader();  // Should be in the same jar as the generator
      InputStream is = cl.getResourceAsStream("codegen/java/mixins/" + mixin.getSimpleName() + ".mixin");
      final Method[] declaredMethods = mixin.getDeclaredMethods();
      try {
        CompilationUnit cu = JavaParser.parse(is);

        new VoidVisitorAdapter() {
          // Copy methods
          @Override
          public void visit(MethodDeclaration n, Object arg) {
            MethodCodeGenerator mcg = method(n.getName());
            if (mcg == null) { // Method not found
              final Method declaredMethod = JCodeGenUtil.find(declaredMethods, n);
              mcg = JCodeGenUtil.method(declaredMethod, n);
              withMethod(mcg);
            }
            String methodBody = n.getBody().toString();
            mcg.withBody(JCodeGenUtil.s(methodBody)).withParentheses(false);
          }

          @Override
          public void visit(final InitializerDeclaration n, Object arg) {
            add(new CodeGeneratorB() {
              @Override
              public void generate(JCodeSB out) {
                out.p(n.toString()).nl(2);
              }
            });
          }
        }.visit(cu, null);
        cu = null;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return self();
  }

  public ClassCodeGenerator withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return this;
  }

  public ClassCodeGenerator withImplements(Class ... interfaces) {
    this.interfaces = append(this.interfaces, interfaces);
    return this;
  }

  public ClassCodeGenerator withExtend(Class extendClass) {
    this.extendClass = extendClass;
    return this;
  }

  public ClassCodeGenerator withAnnotation(JCodeSB... acgs) {
    this.acgs = append(this.acgs, acgs);
    return this;
  }

  public ClassCodeGenerator withCtor(MethodCodeGenerator... mcgs) {
    for (MethodCodeGenerator m : mcgs) {
      assert m.getReturnType() == null : "Declared method does not represent constructor! Method: " + m.name;
      assert m.name.equals(this.name) : "Name of constructor does not match name of class: " + m.name;
      add(m);
      m.setCcg(this);
    }
    return this;
  }

  public ClassCodeGenerator withMethod(MethodCodeGenerator... mcgs) {
    for (MethodCodeGenerator m : mcgs) {
      add(m);
      m.setCcg(this);
    }
    return this;
  }

  public ClassCodeGenerator withField(FieldCodeGenerator... fcgs) {
    for (FieldCodeGenerator fcg : fcgs) {
      add(fcg);
      fcg.setCcg(this);
    }
    return this;
  }

  public MethodCodeGenerator method(String name) {
    for (CodeGenerator cg : this) {
      if (cg instanceof MethodCodeGenerator && ((MethodCodeGenerator) cg).name.equals(name)) {
        return (MethodCodeGenerator) cg;
      }
    }
    return null;
  }

  public FieldCodeGenerator field(String name) {
    for (CodeGenerator cg : this) {
      if (cg instanceof FieldCodeGenerator && ((FieldCodeGenerator) cg).name.equals(name)) {
        return (FieldCodeGenerator) cg;
      }
    }
    return null;
  }

  @Override
  public void generate(JCodeSB out) {
    // Generate class preamble
    genClassHeader(out);
    // Generate the body defined by a chain of code generators
    out.ii(2).i();
    super.generate(out);
    out.di(2).nl();
    // Close this class
    genClassFooter(out);
  }

  @Override
  final public ClassGenContainer classContainer(CodeGenerator caller) {
    return cug().classContainer(caller);
  }

  protected JCodeSB genClassHeader(JCodeSB sb) {
    // Generate annotations
    if (acgs != null) {
      for (JCodeSB acg : acgs) {
        sb.p(acg).nl();
      }
    }
    // Starts to define class
    sb.p(Modifier.toString(modifiers)).p(" class ").p(name);
    if (extendClass != null) {
      sb.p(" extends ").pj(extendClass);
    }
    if (interfaces != null && interfaces.length > 0) {
      sb.p(" implements ").pj(interfaces[0]);
      for (int i = 1; i < interfaces.length; i++) {
        sb.p(", ").pj(interfaces[i]);
      }
    }
    sb.p(" {").nl();

    return sb;
  }

  protected JCodeSB genClassFooter(JCodeSB sb) {
    sb.p("} // End of class ").p(name).nl();
    return sb;
  }

  CompilationUnitGenerator cug() {
    return cug;
  }

  void setCug(CompilationUnitGenerator cug) {
    this.cug = cug;
  }

}
