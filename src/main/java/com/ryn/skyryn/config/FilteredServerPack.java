package com.ryn.skyryn.config;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный пак, из которого берётся только НОВОЕ.
 *
 * Зачем. Свои предметы Hypixel делает на базе обычной бумаги: сама вещь — `minecraft:paper`,
 * а её вид задаёт модель, которая живёт только в серверном паке. Выключишь пак целиком —
 * и половина инвентаря честно превращается в стопки бумаги. Но тот же пак заодно
 * перекрашивает ванильные текстуры и звуки, а вот этого игрок как раз не хотел.
 *
 * Поэтому режем не пак, а его половину: файл, который ПЕРЕКРЫВАЕТ ванильный, прячем —
 * тогда игра берёт свой, — а файл, которого в ванили нет, пропускаем. Кастомные предметы
 * остаются собой, мир и интерфейс возвращаются к обычному виду.
 *
 * Прячем только текстуры и звуки. Модели, определения предметов и атласы трогать нельзя:
 * атлас — это список того, что сшивать в общую текстуру, и без него кастомные картинки
 * просто не попадут в атлас, то есть мы вернём ровно ту поломку, от которой уходим.
 */
public class FilteredServerPack implements PackResources {

	private final PackResources src;
	/** Есть ли такой файл в ванили. Спрашивают тысячи раз за загрузку — помним ответы. */
	private static final Map<String, Boolean> vanillaHas = new ConcurrentHashMap<>();

	public FilteredServerPack(PackResources src) {
		this.src = src;
	}

	/** Файл перекрывает ванильный — значит игрок его видеть не должен. */
	private static boolean hidden(PackType type, Identifier id) {
		String path = id.getPath();
		if (!path.startsWith("textures/") && !path.startsWith("sounds/")) return false;
		String key = type.name() + "|" + id;
		Boolean known = vanillaHas.get(key);
		if (known != null) return known;
		boolean has = false;
		try {
			var mc = Minecraft.getInstance();
			has = mc != null && mc.getVanillaPackResources().getResource(type, id) != null;
		} catch (Throwable ignored) {
			// Ванильный пак не спросить — пусть файл проходит: лучше лишняя текстура,
			// чем дыра на месте предмета.
		}
		vanillaHas.put(key, has);
		return has;
	}

	/** Ответы про ваниль живут до перезагрузки ресурсов: пак мог смениться. */
	public static void forget() { vanillaHas.clear(); }

	@Override
	public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
		return hidden(type, id) ? null : src.getResource(type, id);
	}

	@Override
	public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
		src.listResources(type, namespace, path, (id, supplier) -> {
			if (!hidden(type, id)) out.accept(id, supplier);
		});
	}

	@Override
	public IoSupplier<InputStream> getRootResource(String... path) { return src.getRootResource(path); }

	@Override
	public Set<String> getNamespaces(PackType type) { return src.getNamespaces(type); }

	@Override
	public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException { return src.getMetadataSection(type); }

	@Override
	public PackLocationInfo location() { return src.location(); }

	@Override
	public void close() { src.close(); }
}
