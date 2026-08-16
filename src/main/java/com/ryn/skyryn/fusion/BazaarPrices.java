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

/**
 * Тянет цены шардов с публичного Hypixel Bazaar API и кеширует их.
 *
 * Для каждого шарда доступны:
 *   instaBuy (=buyPrice API) — высокая цена: почём КУПИТЬ мгновенно,
 *                                И почём ПРОДАТЬ через sell offer.
 *   sellOffer (=sellPrice API) — низкая цена: почём продать мгновенно (insta-sell)
 *                                или купить через buy order.
 *   buyVolume / sellVolume — объёмы (ликвидность рынка)
 *
 * API публичный, без ключа. Обновляется по запросу (не чаще раза в 60 сек).
 */
public class BazaarPrices {

	private static final String API_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	// Пороги подобраны по живым данным, а не на глаз. Медиана спреда по всем
	// 189 шардам — 1.32x, 90-й перцентиль 1.89x. Но спред сам по себе накрутку
	// не выдаёт: у Strider Surfer 3.85x при 30 продавцах и спросе 11.7k/день —
	// это просто широкий рынок. Опасен широкий спред только при малом числе
	// продавцов: тогда цену держит один-два человека.
	private static final int THIN_OFFERS = 5;
	private static final double THIN_SPREAD = 2.0;
	/** При таком спреде цена бредовая при любом числе продавцов. */
	private static final double ABSURD_SPREAD = 10.0;

	public static class Price {
		public final double instaBuy;   // buyPrice: insta-buy И продажа через sell offer
		public final double sellOffer;  // sellPrice: insta-sell / покупка через buy order
		public final long buyVolume;    // глубина стакана: сколько висит в ордерах
		public final long sellVolume;
		/**
		 * Сколько штук игроки ВЫКУПИЛИ за неделю — это спрос.
		 * Именно он отвечает на «как быстро я продам»: продаём мы через sell offer,
		 * а его должен кто-то выкупить. Глубина стакана (volume) на это не отвечает.
		 */
		public final long buyMovingWeek;
		/** Сколько штук игроки продали за неделю — предложение. */
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

		/**
		 * Сколько выручишь, если продать qty штук МГНОВЕННО прямо сейчас.
		 * Идём по заявкам сверху вниз, как это сделает игра. Если заявок не хватает —
		 * возвращаем то, что удалось продать, вместе с реальным количеством.
		 *
		 * @return [выручка до налога, сколько штук удалось продать]
		 */
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

		/** Сколько офферов на продажу висит в стакане. 1 = цену держит один человек. */
		public final int sellOfferCount;
		/** Нижний оффер на продажу — по нему покупают мгновенно. */
		public final double lowestOffer;
		/** Верхняя заявка на покупку — по ней продают мгновенно. */
		public final double highestBuyOrder;
		/**
		 * Заявки на покупку целиком: [цена, количество] по убыванию цены.
		 * Нужны, чтобы честно считать insta-sell партии: верхних заявок обычно
		 * на десятки штук, и продавая сотню ты съезжаешь вниз по стакану.
		 * У Mimic из-за этого «прибыль» превращается в убыток.
		 */
		public final double[][] buyOrderBook;

		/** Спрос в день — сколько рынок реально съедает. */
		public double demandPerDay() {
			return buyMovingWeek / 7.0;
		}

		/**
		 * Во сколько раз цена продавцов выше цены покупателей.
		 * У здорового шарда 1.1–1.35. Когда кто-то задирает оффер, сторона
		 * покупателей не двигается — и спред разлетается: у накрученных 5–13.
		 */
		public double spread() {
			return highestBuyOrder > 0 ? lowestOffer / highestBuyOrder : 0;
		}

		/**
		 * Стоит ли верить цене этого шарда.
		 *
		 * Ключевое: широкий спред сам по себе НЕ признак накрутки. По живым данным
		 * у Strider Surfer спред 3.85x при 30 продавцах и спросе 11.7k/день — это
		 * просто широкий рынок. А вот тот же спред при одном-двух продавцах —
		 * уже чья-то личная цена. Поэтому спред смотрим только вместе с числом
		 * офферов. Медиана по всем шардам — 1.32x.
		 */
		public Warning warning() {
			if (sellOfferCount == 0) return Warning.NO_OFFERS;
			double sp = spread();
			if (sp > ABSURD_SPREAD) return Warning.DETACHED;   // абсурд при любом раскладе
			if (sellOfferCount <= THIN_OFFERS && sp > THIN_SPREAD) return Warning.DETACHED;
			if (sellOfferCount == 1) return Warning.SINGLE_SELLER;
			if (instaBuy <= 0) return Warning.NO_PRICE;
			return Warning.NONE;
		}
	}

	/** Почему цене шарда нельзя верить. severity: 2 — цифре верить нельзя, 1 — хрупко, 0 — нет данных. */
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

		/** Короткая пометка (двуязычная). */
		public String tag() { return Lang.tr(tagEn, tagRu); }
		/** Пояснение построчно (двуязычное). */
		public String[] explain() { return RynConfig.isRu() ? explainRu : explainEn; }
		public boolean isBad() { return this != NONE; }
	}

	// Кеш: internal_id (напр. "SHARD_FIREFLY") -> Price
	private static final Map<String, Price> cache = new HashMap<>();
	private static long lastFetch = 0;
	private static boolean fetching = false;
	/** Хоть раз пытались загрузить цены (чтобы отличить «ещё не начинали» от «не вышло»). */
	private static volatile boolean attempted = false;
	/** Последняя попытка провалилась (нет сети / API лёг). Сбрасывается при успехе. */
	private static volatile boolean lastFailed = false;
	/** Растёт при каждом обновлении цен — по нему инвалидируется кеш расчёта. */
	private static volatile int version = 0;

	/** Цены недоступны: пробовали, не загрузились и последняя попытка упала. */
	public static boolean unavailable() {
		return !isLoaded() && attempted && lastFailed;
	}

	/** Версия цен. Менялась — значит пересчитывать. */
	public static int version() { return version; }

	/**
	 * Насколько свежие цены, коротким текстом. Нужно, чтобы понимать, почему
	 * наши числа расходятся с другими калькуляторами: у всех свой снапшот,
	 * и цены на базаре двигаются постоянно.
	 */
	public static String ageText() {
		if (lastFetch == 0) return "—";
		long sec = (System.currentTimeMillis() - lastFetch) / 1000;
		if (sec < 60) return sec + Lang.tr("s", "с");
		return (sec / 60) + Lang.tr("m", "м");
	}

	/** Просит обновить цены прямо сейчас, игнорируя минутный интервал. */
	public static void forceRefresh() {
		lastFetch = 0;
		refreshIfNeeded();
	}

	private static final long MIN_INTERVAL_MS = 60_000; // не чаще раза в минуту

	/** Есть ли уже загруженные цены. */
	public static boolean isLoaded() {
		return !cache.isEmpty();
	}

	/** Цена шарда по internal_id, или null если не загружено/не найдено. */
	public static Price get(String internalId) {
		return cache.get(internalId);
	}

	/**
	 * Запускает обновление цен, если прошло достаточно времени.
	 * Асинхронно, не блокирует игру. Вызывать при открытии панели.
	 */
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

				// Стакан. Внимание на имена: у Hypixel они наизнанку —
				//   buy_summary  = ОФФЕРЫ НА ПРОДАЖУ (по ним ты покупаешь)
				//   sell_summary = ЗАЯВКИ НА ПОКУПКУ (по ним ты продаёшь)
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
