package dev.overgrown.apoli.macros;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.power.builtin.FunctionWarnings;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Stream;

public record Macro(
        List<String> params,
        JsonElement json
) {
    public static @Nullable Macro of(List<String> params, JsonElement json) {
        Set<String> found = new LinkedHashSet<>();
        collectPlaceholders(new Dynamic<>(JsonOps.INSTANCE, json), found);

        if (params.isEmpty()) {
            params = found.stream().toList();
        }

        for (String declared : params) {
            if (!found.contains(declared)) {
                Apoli.LOGGER.error("apoli:function declares parameter [{}] but never uses it", declared);
                return null;
            }
        }
        for (String used : found) {
            if (!params.contains(used)) {
                Apoli.LOGGER.error("apoli:function uses [{}] but does not declare it in \"parameters\"", used);
                return null;
            }
        }

        return new Macro(params, json);
    }

    private static <T> void collectPlaceholders(Dynamic<T> source, Set<String> out) {
        collectPlaceholders(source.getOps(), source.getValue(), out);
    }

    private static <T> void collectPlaceholders(DynamicOps<T> ops, T input, Set<String> out) {
        Optional<String> text = ops.getStringValue(input).result();
        if (text.isPresent()) {
            collectNames(text.get(), out);
            return;
        }
        Optional<Stream<Pair<T, T>>> entries = ops.getMapValues(input).result();
        if (entries.isPresent()) {
            entries.get().forEach(entry -> collectPlaceholders(ops, entry.getSecond(), out));
            return;
        }
        ops.getStream(input).result()
                .ifPresent(values -> values.forEach(value -> collectPlaceholders(ops, value, out)));
    }

    private static void collectNames(String text, Set<String> out) {
        int from = 0;
        while (true) {
            int open = text.indexOf('[', from);
            if (open < 0) return;
            int close = text.indexOf(']', open + 1);
            if (close < 0) return;
            String name = text.substring(open + 1, close);
            if (!name.isEmpty() && name.indexOf('[') < 0) out.add(name);
            from = close + 1;
        }
    }

    private static final Codec<JsonElement> PASSTHROUGH_JSON = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json)
    );

    public static final Codec<Macro> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.list(Codec.STRING).optionalFieldOf("parameters", List.of()).forGetter(Macro::params),
            PASSTHROUGH_JSON.fieldOf("value").forGetter(Macro::json)
    ).apply(i, Macro::of));

    public JsonElement get(Map<String, Dynamic<?>> args, ResourceLocation location) {
        for (String parameter : params) {
            Dynamic<?> value = args.get(parameter);
            if (value == null) {
                if (FunctionWarnings.first(location, "missing:" + parameter)) {
                    Apoli.LOGGER.warn("[Apoli] apoli:macro on {} is missing argument [{}].", location, parameter);
                }
                return null;
            }
        }

        return substitute(JsonOps.INSTANCE, json, params, args);
    }

    private static <T> T substitute(DynamicOps<T> ops, T input, List<String> parameters,
                                    Map<String, Dynamic<?>> arguments) {
        Optional<String> text = ops.getStringValue(input).result();
        if (text.isPresent()) {
            return substituteString(ops, input, text.get(), parameters, arguments);
        }
        Optional<Stream<Pair<T, T>>> entries = ops.getMapValues(input).result();
        if (entries.isPresent()) {
            return ops.createMap(entries.get().map(entry -> Pair.of(entry.getFirst(),
                    substitute(ops, entry.getSecond(), parameters, arguments))));
        }
        Optional<Stream<T>> values = ops.getStream(input).result();
        if (values.isPresent()) {
            return ops.createList(values.get().map(value -> substitute(ops, value, parameters, arguments)));
        }
        return input;
    }

    private static <T> T substituteString(DynamicOps<T> ops, T input, String text, List<String> parameters,
                                          Map<String, Dynamic<?>> arguments) {
        for (int i = 0; i < parameters.size(); i++) {
            String parameter = parameters.get(i);
            if (text.equals("[" + parameter + "]")) {
                return arguments.get(parameter).convert(ops).getValue();
            }
        }
        String replaced = text;
        for (int i = 0; i < parameters.size(); i++) {
            String parameter = parameters.get(i);
            String placeholder = "[" + parameter + "]";
            if (replaced.contains(placeholder)) {
                replaced = replaced.replace(placeholder, stringify(arguments.get(parameter)));
            }
        }
        return replaced.equals(text) ? input : ops.createString(replaced);
    }

    private static <T> String stringify(Dynamic<T> value) {
        DynamicOps<T> ops = value.getOps();
        T raw = value.getValue();
        Optional<String> text = ops.getStringValue(raw).result();
        if (text.isPresent()) return text.get();
        Optional<Number> number = ops.getNumberValue(raw).result();
        if (number.isPresent()) return NUMBER_FORMAT.format(number.get());
        Optional<Boolean> flag = ops.getBooleanValue(raw).result();
        if (flag.isPresent()) return flag.get().toString();
        return String.valueOf(raw);
    }

    private static final DecimalFormat NUMBER_FORMAT = Util.make(new DecimalFormat("#"), format -> {
        format.setMaximumFractionDigits(15);
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
    });
}
