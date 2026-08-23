package dev.overgrown.apoli.script.generator;

public record MemberInfo(
        String name,
        String descriptor,
        String signature,
        int access
) {}
