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

public class FilteredServerPack implements PackResources {
	private final PackResources src;
	private static final Map<String, Boolean> vanillaHas = new ConcurrentHashMap<>();

	public FilteredServerPack(PackResources src) {
		this.src = src;
	}

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
		}
		vanillaHas.put(key, has);
		return has;
	}

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
