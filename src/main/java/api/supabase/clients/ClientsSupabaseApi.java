package api.supabase.clients;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.Client;

/**
 * PostgREST for {@code clients}. Upsert merges on {@code uuid} (same column
 * name as SQLite). Local {@code id} is never sent.
 */
public final class ClientsSupabaseApi {

	private static final String COL_UUID = "uuid";

	private final SupabaseRestClient http;

	public ClientsSupabaseApi(SupabaseRestClient http) {
		this.http = http;
	}

	public List<Client> listOrderById(int limit) throws IOException, InterruptedException {
		int lim = Math.max(1, Math.min(limit, 500));
		HttpResponse<String> res = http.get(SupabaseEndpoints.CLIENTS, "select=*&order=uuid.asc&limit=" + lim);
		int code = res.statusCode();
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP " + code + " " + res.body());
		}
		return parseClientArray(res.body());
	}

	public void upsert(Client c) throws IOException, InterruptedException {
		if (c == null || !c.hasClientUuid()) {
			return;
		}
		if (c.getClientUuid() == null || c.getClientUuid().isBlank()) {
			return;
		}
		JsonArray body = new JsonArray();
		JsonObject row = toRemoteRow(c);
		row.addProperty("sync_status", "SYNCED");
		row.addProperty("synced_at", Instant.now().toString());
		body.add(row);
		String prefer = "resolution=merge-duplicates,return=minimal";
		HttpResponse<String> res = http.postJsonWithQuery(SupabaseEndpoints.CLIENTS, "on_conflict=" + COL_UUID,
				body.toString(), prefer);
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	/**
	 * PostgREST PATCH by {@code uuid}. Body always includes {@code uuid}; other
	 * fields only when changed
	 * vs {@code before}. Local SQLite {@code id} is never sent. Falls back to
	 * {@link #upsert(Client)} when uuid is missing.
	 */
	public void patchUpdate(Client after, Client before) throws IOException, InterruptedException {
		if (after == null || !after.hasClientUuid()) {
			return;
		}
		String uuid = after.getClientUuid();
		if (uuid == null || uuid.isBlank()) {
			upsert(after);
			return;
		}
		JsonObject row = before == null ? toRemoteRow(after) : toRemotePatchDelta(after, before);
		row.addProperty(COL_UUID, uuid.trim());
		row.addProperty("sync_status", "SYNCED");
		row.addProperty("synced_at", Instant.now().toString());
		String v = URLEncoder.encode(uuid.trim(), StandardCharsets.UTF_8).replace("+", "%20");
		HttpResponse<String> res = http.patchJson(SupabaseEndpoints.CLIENTS, COL_UUID + "=eq." + v, row.toString(),
				"return=minimal");
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	public void deleteByClientUuid(String clientUuid) throws IOException, InterruptedException {
		if (clientUuid == null || clientUuid.isBlank()) {
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty(COL_UUID, clientUuid.trim());
		body.addProperty("is_deleted", true);
		body.addProperty("is_active", false);
		body.addProperty("sync_status", "SYNCED");
		body.addProperty("synced_at", Instant.now().toString());
		body.addProperty("deleted_at", Instant.now().toString());
		String v = URLEncoder.encode(clientUuid.trim(), StandardCharsets.UTF_8).replace("+", "%20");
		HttpResponse<String> res = http.patchJson(SupabaseEndpoints.CLIENTS, COL_UUID + "=eq." + v, body.toString(),
				"return=minimal");
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	/**
	 * PostgREST body for upsert/PATCH: all {@code public.clients} columns except
	 * local-only {@code id}.
	 */
	private static JsonObject toRemoteRow(Client c) {
		JsonObject o = new JsonObject();
		o.addProperty(COL_UUID, c.getClientUuid());
		o.addProperty("client_code", nz(c.getClientCode()));
		o.addProperty("client_name", nz(c.getClientName()));
		o.addProperty("business_name", nz(c.getBusinessName()));
		o.addProperty("mobile", nz(c.getPhone()));
		o.addProperty("alternate_mobile", nz(c.getAltPhone()));
		o.addProperty("email", nz(c.getEmail()));
		o.addProperty("gstin", nz(c.getGst()));
		o.addProperty("pan_number", nz(c.getPan()));
		o.addProperty("billing_address", nz(c.getBillingAddress()));
		o.addProperty("shipping_address", nz(c.getShippingAddress()));
		o.addProperty("client_type", nz(c.getClientType()));
		o.addProperty("price_category", nz(c.getPriceCategory()));
		o.addProperty("credit_limit", c.getCreditLimit());
		o.addProperty("payment_terms", nz(c.getPaymentTerms()));
		o.addProperty("opening_balance", c.getOpeningBalance());
		o.addProperty("balance_type", nz(c.getBalanceType()));
		o.addProperty("is_active", c.isActive());
		o.addProperty("notes", nz(c.getNotes()));
		o.addProperty("state", nz(c.getState()));
		o.addProperty("sync_version", c.getSyncVersion());
		o.addProperty("is_deleted", c.isDeleted());
		String deletedAt = c.getDeletedAt();
		if (deletedAt == null || deletedAt.isBlank()) {
			o.add("deleted_at", JsonNull.INSTANCE);
		} else {
			o.addProperty("deleted_at", deletedAt);
		}
		String createdAt = c.getCreatedAt();
		if (createdAt != null && !createdAt.isBlank()) {
			o.addProperty("created_at", createdAt);
		}
		String updatedAt = c.getUpdatedAt();
		if (updatedAt != null && !updatedAt.isBlank()) {
			o.addProperty("updated_at", updatedAt);
		}
		addUuidOrNull(o, "created_by_user_uuid", c.getCreatedByUserUuid());
		addUuidOrNull(o, "updated_by_user_uuid", c.getUpdatedByUserUuid());
		return o;
	}

	/**
	 * PATCH body: changed remote columns only (no {@code id}; {@code uuid} added by
	 * {@link #patchUpdate}).
	 */
	private static JsonObject toRemotePatchDelta(Client after, Client before) {
		JsonObject o = new JsonObject();
		putStrIfChanged(o, "client_code", after.getClientCode(), before.getClientCode());
		putStrIfChanged(o, "client_name", after.getClientName(), before.getClientName());
		putStrIfChanged(o, "business_name", after.getBusinessName(), before.getBusinessName());
		putStrIfChanged(o, "mobile", after.getPhone(), before.getPhone());
		putStrIfChanged(o, "alternate_mobile", after.getAltPhone(), before.getAltPhone());
		putStrIfChanged(o, "email", after.getEmail(), before.getEmail());
		putStrIfChanged(o, "gstin", after.getGst(), before.getGst());
		putStrIfChanged(o, "pan_number", after.getPan(), before.getPan());
		putStrIfChanged(o, "billing_address", after.getBillingAddress(), before.getBillingAddress());
		putStrIfChanged(o, "shipping_address", after.getShippingAddress(), before.getShippingAddress());
		putStrIfChanged(o, "client_type", after.getClientType(), before.getClientType());
		putStrIfChanged(o, "price_category", after.getPriceCategory(), before.getPriceCategory());
		putDblIfChanged(o, "credit_limit", after.getCreditLimit(), before.getCreditLimit());
		putStrIfChanged(o, "payment_terms", after.getPaymentTerms(), before.getPaymentTerms());
		putDblIfChanged(o, "opening_balance", after.getOpeningBalance(), before.getOpeningBalance());
		putStrIfChanged(o, "balance_type", after.getBalanceType(), before.getBalanceType());
		putBoolIfChanged(o, "is_active", after.isActive(), before.isActive());
		putStrIfChanged(o, "notes", after.getNotes(), before.getNotes());
		putStrIfChanged(o, "state", after.getState(), before.getState());
		putIntIfChanged(o, "sync_version", after.getSyncVersion(), before.getSyncVersion());
		putBoolIfChanged(o, "is_deleted", after.isDeleted(), before.isDeleted());
		putDeletedAtIfChanged(o, after.getDeletedAt(), before.getDeletedAt());
		putStrIfChanged(o, "created_at", after.getCreatedAt(), before.getCreatedAt());
		putStrIfChanged(o, "updated_at", after.getUpdatedAt(), before.getUpdatedAt());
		putUuidIfChanged(o, "created_by_user_uuid", after.getCreatedByUserUuid(), before.getCreatedByUserUuid());
		putUuidIfChanged(o, "updated_by_user_uuid", after.getUpdatedByUserUuid(), before.getUpdatedByUserUuid());
		return o;
	}

	private static void putStrIfChanged(JsonObject o, String key, String after, String before) {
		String a = nz(after);
		String b = nz(before);
		if (!a.equals(b)) {
			o.addProperty(key, a);
		}
	}

	private static void addUuidOrNull(JsonObject o, String key, String val) {
		if (val == null || val.trim().isEmpty()) {
			o.add(key, JsonNull.INSTANCE);
		} else {
			o.addProperty(key, val.trim());
		}
	}

	private static void putUuidIfChanged(JsonObject o, String key, String after, String before) {
		String a = after == null || after.trim().isEmpty() ? null : after.trim();
		String b = before == null || before.trim().isEmpty() ? null : before.trim();
		if ((a == null && b != null) || (a != null && !a.equals(b))) {
			if (a == null) {
				o.add(key, JsonNull.INSTANCE);
			} else {
				o.addProperty(key, a);
			}
		}
	}

	private static void putIntIfChanged(JsonObject o, String key, int after, int before) {
		if (after != before) {
			o.addProperty(key, after);
		}
	}

	private static void putDblIfChanged(JsonObject o, String key, double after, double before) {
		if (Double.compare(after, before) != 0) {
			o.addProperty(key, after);
		}
	}

	private static void putBoolIfChanged(JsonObject o, String key, boolean after, boolean before) {
		if (after != before) {
			o.addProperty(key, after);
		}
	}

	private static void putDeletedAtIfChanged(JsonObject o, String after, String before) {
		String a = nz(after);
		String b = nz(before);
		if (a.equals(b)) {
			return;
		}
		if (a.isBlank()) {
			o.add("deleted_at", JsonNull.INSTANCE);
		} else {
			o.addProperty("deleted_at", a);
		}
	}

	private static String nz(String s) {
		return s != null ? s : "";
	}

	private static List<Client> parseClientArray(String json) {
		List<Client> out = new ArrayList<>();
		if (json == null || json.isBlank()) {
			return out;
		}
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonArray()) {
			return out;
		}
		JsonArray arr = root.getAsJsonArray();
		for (JsonElement el : arr) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String bill = jstr(o, "billing_address");
			if (bill.isBlank()) {
				bill = jstr(o, "address_line1");
			}
			String ship = jstr(o, "shipping_address");
			if (ship.isBlank()) {
				ship = jstr(o, "address_line2");
			}
			Client c = new Client(jstr(o, "business_name"), jstr(o, "client_name"), jstr(o, "mobile"),
					jstr(o, "alternate_mobile"), jstr(o, "email"), jstr(o, "gstin"), jstr(o, "pan_number"), bill, ship,
					jstr(o, "notes"));
			c.setClientUuid(jstrUuid(o));
			c.setClientCode(jstr(o, "client_code"));
			c.setState(jstr(o, "state"));
			c.setClientType(jstr(o, "client_type"));
			c.setPriceCategory(jstr(o, "price_category"));
			c.setPaymentTerms(jstr(o, "payment_terms"));
			c.setBalanceType(jstr(o, "balance_type"));
			c.setSyncStatus(jstr(o, "sync_status"));
			c.setSyncVersion(jint(o, "sync_version"));
			c.setIsDeleted(jbool(o, "is_deleted"));
			c.setIsActive(jbool(o, "is_active"));
			c.setDeletedAt(jstr(o, "deleted_at"));
			c.setCreatedAt(jstr(o, "created_at"));
			c.setUpdatedAt(jstr(o, "updated_at"));
			c.setSyncedAt(jstr(o, "synced_at"));
			c.setCreatedByUserUuid(jstr(o, "created_by_user_uuid"));
			c.setUpdatedByUserUuid(jstr(o, "updated_by_user_uuid"));
			if (o.has("credit_limit") && !o.get("credit_limit").isJsonNull()) {
				c.setCreditLimit(o.get("credit_limit").getAsDouble());
			}
			if (o.has("opening_balance") && !o.get("opening_balance").isJsonNull()) {
				c.setOpeningBalance(o.get("opening_balance").getAsDouble());
			}
			out.add(c);
		}
		return out;
	}

	private static String jstrUuid(JsonObject o) {
		String v = jstr(o, COL_UUID);
		if (!v.isBlank()) {
			return v;
		}
		return jstr(o, "client_uuid");
	}

	private static String jstr(JsonObject o, String key) {
		if (!o.has(key) || o.get(key).isJsonNull()) {
			return "";
		}
		return o.get(key).getAsString();
	}

	private static int jint(JsonObject o, String key) {
		if (!o.has(key) || o.get(key).isJsonNull()) {
			return 0;
		}
		return o.get(key).getAsInt();
	}

	private static boolean jbool(JsonObject o, String key) {
		if (!o.has(key) || o.get(key).isJsonNull()) {
			return false;
		}
		try {
			com.google.gson.JsonElement el = o.get(key);
			if (el.isJsonPrimitive()) {
				com.google.gson.JsonPrimitive prim = el.getAsJsonPrimitive();
				if (prim.isBoolean()) {
					return prim.getAsBoolean();
				}
				if (prim.isNumber()) {
					return prim.getAsInt() != 0;
				}
				if (prim.isString()) {
					String s = prim.getAsString();
					return "true".equalsIgnoreCase(s) || "1".equals(s);
				}
			}
		} catch (Exception ignored) {}
		return false;
	}
}
