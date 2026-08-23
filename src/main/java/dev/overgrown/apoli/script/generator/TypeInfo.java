package dev.overgrown.apoli.script.generator;

import java.util.List;

public record TypeInfo(
        String name,
        TypeKind kind,
        String superClass,
        List<String> interfaces,
        List<MemberInfo> fields,
        List<MemberInfo> methods
) {}

