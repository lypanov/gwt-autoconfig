package com.glubble.auto.generator;

import com.google.gwt.core.ext.*;
import com.google.gwt.core.ext.typeinfo.*;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AutoConfigurationGenerator extends Generator {

    public String generate(TreeLogger logger, GeneratorContext context, String typeName) throws UnableToCompleteException {
        TypeOracle typeOracle = context.getTypeOracle();

        if (!typeName.endsWith("Configuration")) {
            logger.log(TreeLogger.ERROR, "Classname must end in 'Configuration'", null);
            throw new UnableToCompleteException();
        }

        JClassType converterClass;
        try {
            converterClass = typeOracle.getType(typeName);
        } catch (NotFoundException e) {
            logger.log(TreeLogger.ERROR, "No class found " + typeName, e);
            throw new UnableToCompleteException();
        }

        String packageName = converterClass.getPackage().getName();
        String simpleStubClassName = "__" + converterClass.getSimpleSourceName() + "_impl__";
        String qualifiedStubClassName = packageName + "." + simpleStubClassName;

        SourceWriter sw = getSourceWriter(logger, context, packageName, simpleStubClassName, converterClass.getQualifiedSourceName());
        if (sw == null) {
            return qualifiedStubClassName;
        }

        generateGetters(context, converterClass, sw);

        sw.commit(logger);

        return qualifiedStubClassName;
    }

    public class BeanProperty {
        private JMethod getter;
        private String name;

        public BeanProperty(String capitalizedName, JMethod getter){
            this.name = recapitalize(capitalizedName);
            this.getter = getter;
        }

        public String getName(){
            return name;
        }

        public JMethod getGetter(){
            return getter;
        }

        private String recapitalize(String capitalized){
            StringBuffer buffer = new StringBuffer(capitalized);
            char first = buffer.charAt(0);
            char recap = Character.isUpperCase(first)? Character.toLowerCase(first): Character.toUpperCase(first);
            buffer.setCharAt(0, Character.toLowerCase(recap));
            return buffer.toString();
        }
    }

    private void generateGetters(GeneratorContext context, JClassType converterClass, SourceWriter sw) {
        ArrayList<BeanProperty> properties1 = new ArrayList<BeanProperty>();
        JMethod[] methods = converterClass.getMethods();

        for (JMethod getter : methods) {
            String getterName = getter.getName();
            if (!getter.isStatic() && getter.isPublic() && getterName.startsWith("get") && getter.getParameters().length == 0) {
                String capitalizedName = getterName.substring(3);
                properties1.add(new BeanProperty(capitalizedName, getter));
            }
        }

        Map<String, String> foo = new HashMap<String, String>();
        for (BeanProperty property : properties1) {
            try {
                foo.put(property.getName(), context.getPropertyOracle().getConfigurationProperty(property.getName()).getValues().get(0));
            } catch (BadPropertyValueException e) {
                e.printStackTrace();
            }
        }
        StringTemplate stringTemplate = new StringTemplate(
                "$props:{ public $it.getter.returnType.qualifiedSourceName$ $it.getter.name$() { return \"$foo.(it.name)$\"; } }$",
                DefaultTemplateLexer.class);
        stringTemplate.setAttribute("props", properties1);
        stringTemplate.setAttribute("foo", foo);
        sw.println(stringTemplate.toString());
    }

    private SourceWriter getSourceWriter(TreeLogger logger,
                                         GeneratorContext ctx, String packageName, String className,
                                         String interfaceName) {
        PrintWriter printWriter = ctx.tryCreate(logger, packageName, className);
        if (printWriter == null) {
            return null;
        }
        ClassSourceFileComposerFactory composerFactory = new ClassSourceFileComposerFactory(packageName, className);
        composerFactory.addImplementedInterface(interfaceName);
        return composerFactory.createSourceWriter(ctx, printWriter);
    }
}
