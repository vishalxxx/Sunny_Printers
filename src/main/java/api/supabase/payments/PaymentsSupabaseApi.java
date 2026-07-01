package api.supabase.payments;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.Payment;

/** PostgREST for {@code payments}. Upsert merges on {@code uuid}. */
public final class PaymentsSupabaseApi {

	private static final String COL_UUID = "uuid";
	private final SupabaseRestClient http;

	public PaymentsSupabaseApi(SupabaseRestClient http) {
		this.http = http;
	}

	public void upsert(Payment payment) throws IOException, InterruptedException {
		if (payment == null || payment.getUuid() == null || payment.getUuid().isBlank()) {
			return;
		}
		JsonArray body = new JsonArray();
		JsonObject row = toRemoteRow(payment);
		row.addProperty("sync_status", "SYNCED");
		row.addProperty("synced_at", Instant.now().toString());
		body.add(row);
		HttpResponse<String> res = http.postJsonWithQuery(SupabaseEndpoints.PAYMENTS, "on_conflict=" + COL_UUID,
				body.toString(), "resolution=merge-duplicates,return=minimal");
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	private static JsonObject toRemoteRow(Payment p) {
		JsonObject o = new JsonObject();
		o.addProperty(COL_UUID, p.getUuid().trim());
		if (p.getClientUuid() == null || p.getClientUuid().isBlank()) {
			o.add("client_uuid", JsonNull.INSTANCE);
		} else {
			o.addProperty("client_uuid", p.getClientUuid().trim());
		}
		o.addProperty("amount", p.getAmount());
		if (p.getPaymentDate() == null || p.getPaymentDate().isBlank()) {
			o.add("payment_date", JsonNull.INSTANCE);
		} else {
			o.addProperty("payment_date", p.getPaymentDate());
		}
		o.addProperty("method", nz(p.getMethod()));
		o.addProperty("type", nz(p.getType(), "Payment"));
		o.addProperty("is_deleted", p.getIsDeleted());
		o.addProperty("is_active", p.getIsActive() > 0 ? 1 : 0);
		o.addProperty("sync_version", Math.max(1, p.getSyncVersion()));
		addTimestampOrNull(o, "created_at", p.getCreatedAt());
		addTimestampOrNull(o, "updated_at", p.getUpdatedAt());
		return o;
	}

	private static void addTimestampOrNull(JsonObject o, String key, String value) {
		if (value == null || value.isBlank()) {
			o.add(key, JsonNull.INSTANCE);
		} else {
			o.addProperty(key, value);
		}
	}

	private static String nz(String s) {
		return s != null ? s : "";
	}

	private static String nz(String s, String defaultVal) {
		return s == null || s.isBlank() ? defaultVal : s;
	}
}
