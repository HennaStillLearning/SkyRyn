package com.ryn.skyryn.fusion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ryn.skyryn.config.Lang;
import com.ryn.skyryn.config.RynConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BazaarPrices {
	private static final String API_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private static final int THIN_OFFERS = 5;
	private static final double THIN_SPREAD = 2.0;
	private static final double ABSURD_SPREAD = 10.0;

	public static class Price {
		public final double instaBuy;
		public final double sellOffer;
		public final long buyVolume;
		public final long sellVolume;
		public final long buyMovingWeek;
		public final long sellMovingWeek;

		public Price(double instaBuy, double sellOffer, long buyVolume, long sellVolume,
					 long buyMovingWeek, long sellMovingWeek,
					 int sellOfferCount, double lowestOffer, double highestBuyOrder,
					 double[][] buyOrderBook) {
			this.instaBuy = instaBuy;
			this.sellOffer = sellOffer;
			this.buyVolume = buyVolume;
			this.sellVolume = sellVolume;
			this.buyMovingWeek = buyMovingWeek;
			this.sellMovingWeek = sellMovingWeek;
			this.sellOfferCount = sellOfferCount;
			this.lowestOffer = lowestOffer;
			this.highestBuyOrder = highestBuyOrder;
			this.buyOrderBook = buyOrderBook;
		}

		public double[] instaSellRevenue(int qty) {
			double total = 0;
			int left = qty;
			for (double[] lvl : buyOrderBook) {
				if (left <= 0) break;
				int take = (int) Math.min(left, lvl[1]);
				total += take * lvl[0];
				left -= take;
			}
			return new double[] { total, qty - left };
		}

		public final int sellOfferCount;
		public final double lowestOffer;
		public final double highestBuyOrder;
		public final double[][] buyOrderBook;

		public double demandPerDay() {
			return buyMovingWeek / 7.0;
		}

		public double spread() {
			return highestBuyOrder > 0 ? lowestOffer / highestBuyOrder : 0;
		}

		public Warning warning() {
			if (sellOfferCount == 0) return Warning.NO_OFFERS;
			double sp = spread();
			if (sp > ABSURD_SPREAD) return Warning.DETACHED;
			if (sellOfferCount <= THIN_OFFERS && sp > THIN_SPREAD) return Warning.DETACHED;
			if (sellOfferCount == 1) return Warning.SINGLE_SELLER;
			if (instaBuy <= 0) return Warning.NO_PRICE;
			return Warning.NONE;
		}
	}

	public enum Warning {
		NONE(0, "", "", new String[0], new String[0]),
		DETACHED(2, "possible manipulation", "возможна манипуляция",
				new String[]{
						"Few sellers, and they ask far more than buyers are willing to pay.",
						"This usually happens when a shard sells out and the first seller back",
						"jacks up the price. Or a player buys out all sell offers and relists",
						"everything as one offer of their own, to inflate the price.",
						"",
						"Profit at that price is paper: you most likely can't sell for it."},
				new String[]{
						"Продавцов мало, и просят они намного больше, чем готовы платить покупатели.",
						"Обычно так бывает, когда шард разобрали, а первый вернувшийся продавец",
						"заломил цену. Либо игрок сам выкупил все sell offer и выставил всё",
						"в один свой, чтобы задрать цену.",
						"",
						"Профит по такой цене бумажный: продать по ней ты скорее всего не сможешь."}),
		SINGLE_SELLER(1, "single seller", "один продавец",
				new String[]{"One person holds the whole price. Pull their offer and it flies anywhere."},
				new String[]{"Всю цену держит один человек. Уберёт свой оффер — цена улетит куда угодно."}),
		NO_OFFERS(0, "no offers", "нет офферов",
				new String[]{"Nobody is selling this shard right now, so there's nothing to compare against."},
				new String[]{"Шард сейчас никто не продаёт, поэтому сравнивать не с чем."}),
		NO_PRICE(0, "no price", "нет цены",
				new String[]{"The bazaar doesn't return a buy price for this shard."},
				new String[]{"Базар не отдаёт цену покупки для этого шарда."});

		public final int severity;
		private final String tagEn, tagRu;
		private final String[] explainEn, explainRu;

		Warning(int severity, String tagEn, String tagRu, String[] explainEn, String[] explainRu) {
			this.severity = severity;
			this.tagEn = tagEn;
			this.tagRu = tagRu;
			this.explainEn = explainEn;
			this.explainRu = explainRu;
		}

		public String tag() { return Lang.tr(tagEn, tagRu); }
		public String[] explain() { return RynConfig.isRu() ? explainRu : explainEn; }
		public boolean isBad() { return this != NONE; }
	}

	private static final Map<String, Price> cache = new HashMap<>();
	private static long lastFetch = 0;
	private static boolean fetching = false;
	private static volatile boolean attempted = false;
	private static volatile boolean lastFailed = false;
	private static volatile int version = 0;

	public static boolean unavailable() {
		return !isLoaded() && attempted && lastFailed;
	}

	public static int version() { return version; }

	public static String ageText() {
		if (lastFetch == 0) return "—";
		long sec = (System.currentTimeMillis() - lastFetch) / 1000;
		if (sec < 60) return sec + Lang.tr("s", "с");
		return (sec / 60) + Lang.tr("m", "м");
	}

	public static void forceRefresh() {
		lastFetch = 0;
		refreshIfNeeded();
	}

	private static final long MIN_INTERVAL_MS = 60_000;

	public static boolean isLoaded() {
		return !cache.isEmpty();
	}

	public static Price get(String internalId) {
		return cache.get(internalId);
	}

	public static void refreshIfNeeded() {
		long now = System.currentTimeMillis();
		if (fetching) return;
		if (now - lastFetch < MIN_INTERVAL_MS && isLoaded()) return;

		fetching = true;
		attempted = true;
		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(API_URL))
						.timeout(Duration.ofSeconds(10))
						.GET()
						.build();
				HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (resp.statusCode() == 200) {
					parseAndCache(resp.body());
					lastFetch = System.currentTimeMillis();
					lastFailed = false;
				} else {
					lastFailed = true;
				}
			} catch (Exception e) {
				lastFailed = true;
				com.ryn.skyryn.config.SkyLog.d("Bazaar API error: " + e.getMessage());
			} finally {
				fetching = false;
			}
		});
	}

	private static void parseAndCache(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("products")) return;
		JsonObject products = root.getAsJsonObject("products");

		Map<String, Price> fresh = new HashMap<>();
		for (String key : products.keySet()) {
			try {
				JsonObject prod = products.getAsJsonObject(key);
				if (!prod.has("quick_status")) continue;
				JsonObject qs = prod.getAsJsonObject("quick_status");
				double buyPrice = qs.has("buyPrice") ? qs.get("buyPrice").getAsDouble() : 0;
				double sellPrice = qs.has("sellPrice") ? qs.get("sellPrice").getAsDouble() : 0;
				long buyVol = qs.has("buyVolume") ? qs.get("buyVolume").getAsLong() : 0;
				long sellVol = qs.has("sellVolume") ? qs.get("sellVolume").getAsLong() : 0;
				long buyWeek = qs.has("buyMovingWeek") ? qs.get("buyMovingWeek").getAsLong() : 0;
				long sellWeek = qs.has("sellMovingWeek") ? qs.get("sellMovingWeek").getAsLong() : 0;

				JsonArray offers = prod.has("buy_summary") ? prod.getAsJsonArray("buy_summary") : new JsonArray();
				JsonArray orders = prod.has("sell_summary") ? prod.getAsJsonArray("sell_summary") : new JsonArray();
				int offerCount = offers.size();
				double lowestOffer = offerCount > 0
						? offers.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble() : 0;
				double highestOrder = orders.size() > 0
						? orders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble() : 0;

				double[][] book = new double[orders.size()][2];
				for (int i = 0; i < orders.size(); i++) {
					JsonObject o = orders.get(i).getAsJsonObject();
					book[i][0] = o.get("pricePerUnit").getAsDouble();
					book[i][1] = o.get("amount").getAsDouble();
				}

				fresh.put(key, new Price(buyPrice, sellPrice, buyVol, sellVol, buyWeek, sellWeek,
						offerCount, lowestOffer, highestOrder, book));
			} catch (Exception ignored) {
			}
		}
		synchronized (cache) {
			cache.clear();
			cache.putAll(fresh);
		}
		version++;
		com.ryn.skyryn.config.SkyLog.d("Bazaar prices loaded: " + fresh.size() + " products");
	}
}
