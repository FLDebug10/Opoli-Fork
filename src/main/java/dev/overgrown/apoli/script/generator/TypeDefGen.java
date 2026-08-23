package dev.overgrown.apoli.script.generator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class TypeDefGen {
    private static final Set<String> ROOTS = Set.of(
            "dev/overgrown/apoli/condition/context/EntityCtx",
            "org/slf4j/Logger"
    );

    private static final Map<String, String> GLOBAL_BINDINGS = Map.of(
            "logger", "org/slf4j/Logger",
            "ctx", "dev/overgrown/apoli/condition/context/EntityCtx"
    );

    private static final Map<String, ClassNode> classes = new HashMap<>();
    private static final Set<String> visited = new HashSet<>();

    private static final List<TypeInfo> result = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String classpath = System.getProperty("typeDef.scanClasspath");

        if (classpath == null) {
            throw new IllegalStateException("Missing typeDef.scanClasspath");
        }

        for (String entry : classpath.split(File.pathSeparator)) {
            Path path = Path.of(entry);

            if (Files.isDirectory(path)) {
                scanDirectory(path);
            } else if (path.toString().endsWith(".jar")) {
                scanJar(path);
            }
        }

        System.out.println("Indexed " + classes.size() + " classes");

        for (String root : ROOTS) {
            visit(root);
        }

        System.out.println("Reachable classes: " + visited.size());

        Map<String, Integer> packages = new TreeMap<>();

        for (String name : visited) {
            int separator = -1;

            for (int i = 0; i < 3; i++) {
                separator = name.indexOf('/', separator + 1);

                if (separator == -1) {
                    break;
                }
            }

            String pkg = separator == -1
                    ? name
                    : name.substring(0, separator);

            packages.merge(pkg, 1, Integer::sum);
        }

        packages.forEach((pkg, count) ->
                System.out.println(count + " " + pkg)
        );

        new TypeDefGen().generate(result);
    }

    private static void scanJar(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            var entries = file.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();

                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                if (entry.getName().equals("module-info.class")) {
                    continue;
                }

                try (var input = file.getInputStream(entry)) {
                    indexClass(input.readAllBytes());
                }
            }
        }
    }

    private static void scanDirectory(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            stream
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        try {
                            indexClass(Files.readAllBytes(path));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static void indexClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);

        ClassNode node = new ClassNode();

        reader.accept(
                node,
                ClassReader.SKIP_CODE |
                        ClassReader.SKIP_DEBUG |
                        ClassReader.SKIP_FRAMES
        );

        classes.put(node.name, node);
    }

    private static void visit(String name) {
        if (!shouldFollow(name) || !visited.add(name)) {
            return;
        }

        ClassNode node = classes.get(name);

        if (node == null) {
            return;
        }

        if (node.superName != null) {
            visit(node.superName);
        }

        for (String iface : node.interfaces) {
            visit(iface);
        }

        for (FieldNode field : node.fields) {
            visitType(Type.getType(field.desc));
        }

        for (MethodNode method : node.methods) {
            visitDescriptor(method.desc);
        }

        result.add(new TypeInfo(
                node.name,
                getTypeKind(node),
                node.superName,
                List.copyOf(node.interfaces),
                node.fields.stream()
                        .filter(field -> (field.access & Opcodes.ACC_PUBLIC) != 0)
                        .map(field -> new MemberInfo(
                                field.name,
                                field.desc,
                                field.signature,
                                field.access
                        ))
                        .toList(),
                node.methods.stream()
                        .filter(method -> (method.access & Opcodes.ACC_PUBLIC) != 0)
                        .filter(method -> !method.name.equals("<init>"))
                        .map(method -> new MemberInfo(
                                method.name,
                                method.desc,
                                method.signature,
                                method.access
                        ))
                        .toList()
        ));
    }

    private static void visitDescriptor(String descriptor) {
        Type method = Type.getMethodType(descriptor);

        visitType(method.getReturnType());

        for (Type parameter : method.getArgumentTypes()) {
            visitType(parameter);
        }
    }

    private static void visitType(Type type) {
        switch (type.getSort()) {
            case Type.OBJECT -> visit(type.getInternalName());
            case Type.ARRAY -> visitType(type.getElementType());
        }
    }

    private static boolean shouldFollow(String name) {
        return (name.startsWith("net/minecraft/")
                || name.startsWith("dev/overgrown/apoli/")
                || name.startsWith("org/slf4j/")
        )
                &&(!(name.startsWith("net/minecraft/network/protocol/")));
    }

    private static TypeKind getTypeKind(ClassNode node) {
        if ((node.access & Opcodes.ACC_ANNOTATION) != 0) {
            return TypeKind.ANNOTATION;
        }

        if ((node.access & Opcodes.ACC_ENUM) != 0) {
            return TypeKind.ENUM;
        }

        if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
            return TypeKind.INTERFACE;
        }

        if ((node.access & Opcodes.ACC_RECORD) != 0) {
            return TypeKind.RECORD;
        }

        return TypeKind.CLASS;
    }

    private void generate(List<TypeInfo> types) {
        File output = new File("build/typeDefGen/api.d.ts");

        try {
            Files.createDirectories(Path.of("build/typeDefGen/"));
            output.createNewFile();
            System.out.println("\nGenerated Output File at " + output.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter("build/typeDefGen/api.d.ts"))) {
            for (TypeInfo type : types) {
                bw.write(generateTypeDef(type));
                bw.newLine();
            }

            for (var entry : GLOBAL_BINDINGS.entrySet()) {
                bw.write("declare const " + entry.getKey() + ": " + getTypeName(entry.getValue()) + ";");
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String generateTypeDef(TypeInfo type) {
        StringBuilder out = new StringBuilder();
        String name = getTypeName(type.name());

        switch (type.kind()) {
            case CLASS, RECORD -> out.append("declare class ").append(name);
            case INTERFACE -> out.append("declare interface ").append(name);
            case ENUM -> out.append("declare enum ").append(name);
            default -> {
                return "";
            }
        }

        if (type.kind() != TypeKind.ENUM) {
            if (type.superClass() != null
                    && !type.superClass().equals("java/lang/Object")
                    && visited.contains(type.superClass())) {
                out.append(" extends ").append(getTypeName(type.superClass()));
            }

            List<String> knownInterfaces = type.interfaces().stream()
                    .filter(visited::contains)
                    .toList();
            if (!knownInterfaces.isEmpty()) {
                out.append(type.kind() == TypeKind.INTERFACE ? " extends " : " implements ");
                out.append(knownInterfaces.stream()
                        .map(this::getTypeName)
                        .collect(Collectors.joining(", ")));
            }
        }

        out.append(" {\n");

        if (type.kind() == TypeKind.ENUM) {
            String constants = type.fields().stream()
                    .filter(f -> (f.access() & Opcodes.ACC_ENUM) != 0)
                    .map(MemberInfo::name)
                    .collect(Collectors.joining(",\n    "));
            out.append("    ").append(constants).append("\n");
            out.append("}");
            return out.toString();
        }

        for (MemberInfo field : type.fields()) {
            if ((field.access() & Opcodes.ACC_ENUM) != 0) {
                continue;
            }
            if (type.kind() == TypeKind.INTERFACE && (field.access() & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            out.append("    ");
            if ((field.access() & Opcodes.ACC_STATIC) != 0) out.append("static ");
            if ((field.access() & Opcodes.ACC_FINAL) != 0) out.append("readonly ");
            out.append(field.name())
                    .append(": ")
                    .append(jvmTypeToTs(Type.getType(field.descriptor())))
                    .append(";\n");
        }

        for (MemberInfo method : type.methods()) {
            if (method.name().equals("<clinit>")) continue;
            if (type.kind() == TypeKind.INTERFACE && (method.access() & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            Type methodType = Type.getMethodType(method.descriptor());
            out.append("    ");
            if ((method.access() & Opcodes.ACC_STATIC) != 0) out.append("static ");
            out.append(method.name()).append("(");

            Type[] params = methodType.getArgumentTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) out.append(", ");
                out.append("arg").append(i).append(": ").append(jvmTypeToTs(params[i]));
            }
            out.append("): ").append(jvmTypeToTs(methodType.getReturnType())).append(";\n");
        }

        out.append("}");
        return out.toString();
    }

    private String jvmTypeToTs(Type type) {
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.BYTE, Type.SHORT, Type.INT, Type.FLOAT, Type.DOUBLE, Type.LONG -> "number";
            case Type.CHAR -> "string";
            case Type.ARRAY -> jvmTypeToTs(type.getElementType()) + "[]".repeat(type.getDimensions());
            case Type.OBJECT -> objectTypeToTs(type.getInternalName());
            default -> "any";
        };
    }

    private String objectTypeToTs(String internalName) {
        return switch (internalName) {
            case "java/lang/String", "java/lang/CharSequence", "java/lang/Character" -> "string";
            case "java/lang/Object" -> "any";
            case "java/lang/Boolean" -> "boolean";
            case "java/lang/Integer", "java/lang/Long", "java/lang/Short",
                 "java/lang/Byte", "java/lang/Float", "java/lang/Double" -> "number";
            default -> visited.contains(internalName) ? getTypeName(internalName) : "any";
        };
    }

    private String getTypeName(String name) {
        int separator = name.lastIndexOf('/');

        String simpleName = separator == -1
                ? name
                : name.substring(separator + 1);

        return simpleName.replace('$', '_');
    }
}

