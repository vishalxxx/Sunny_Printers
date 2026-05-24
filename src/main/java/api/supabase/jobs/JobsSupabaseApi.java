package api.supabase.jobs;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.Job;

/** PostgREST for {@code jobs}. Upsert merges on {@code uuid}. */
public final class JobsSupabaseApi {

	private static final String COL_UUID = "uuid";
	private final SupabaseRestClient http;

	public JobsSupabaseApi(SupabaseRestClient http) {
		this.http = http;
	}

	public void upsert(Job job) throws IOException, InterruptedException {
		if (job == null || !job.hasUuid()) {
			return;
		}
		JsonArray body = new JsonArray();
		JsonObject row = toRemoteRow(job);
		row.addProperty("sync_status", "SYNCED");
		row.addProperty("synced_at", Instant.now().toString());
		body.add(row);
		String prefer = "resolution=merge-duplicates,return=minimal";
		HttpResponse<String> res = http.postJsonWithQuery(SupabaseEndpoints.JOBS, "on_conflict=" + COL_UUID,
				body.toString(), prefer);
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	private static JsonObject toRemoteRow(Job job) {
		JsonObject o = new JsonObject();
		o.addProperty(COL_UUID, job.getUuid().trim());
		addNullableUuid(o, "client_uuid", job.getClientUuid());
		addNullableUuid(o, "invoice_uuid", job.getInvoiceUuid());
		o.addProperty("job_code", nz(job.getJobCode()));
		o.addProperty("job_title", nz(job.getJobTitle()));
		o.addProperty("job_type", nz(job.getJobType()));
		o.addProperty("description", nz(job.getDescription()));
		o.addProperty("amount", resolveAmount(job));
		o.addProperty("status", nz(job.getStatus(), "Created"));
		o.addProperty("child_status", nz(job.getChildStatus()));
		o.addProperty("job_number_mode", nz(job.getJobNumberMode(), "AUTO"));
		o.addProperty("image_path", nz(job.getImagePath()));
		o.addProperty("remarks", nz(job.getRemarks()));
		addDateOrNull(o, "job_date", job.getJobDate());
		addDateOrNull(o, "delivery_date", job.getDeliveryDate());
		o.addProperty("is_deleted", job.getIsDeleted());
		o.addProperty("is_active", job.getIsActive() > 0 ? 1 : 0);
		addTimestampOrNull(o, "deleted_at", null);
		o.addProperty("sync_version", Math.max(1, job.getSyncVersion()));
		addTimestampOrNull(o, "created_at", job.getCreatedAt());
		addTimestampOrNull(o, "updated_at", job.getUpdatedAt());
		addNullableUuid(o, "created_by_user_uuid", job.getCreatedByUserUuid());
		addNullableUuid(o, "updated_by_user_uuid", job.getUpdatedByUserUuid());
		return o;
	}

	/** Use denormalized {@code jobs.amount}, falling back to line-item sum when needed. */
	static double resolveAmount(Job job) {
		if (job == null) {
			return 0;
		}
		double headerAmount = job.getAmount();
		Double lineTotal = job.getJobTotal();
		if (headerAmount > 0) {
			return headerAmount;
		}
		if (lineTotal != null && lineTotal > 0) {
			return lineTotal;
		}
		return lineTotal != null ? lineTotal : 0;
	}

	private static void addNullableUuid(JsonObject o, String key, String value) {
		if (value == null || value.isBlank()) {
			o.add(key, JsonNull.INSTANCE);
		} else {
			o.addProperty(key, value.trim());
		}
	}

	private static void addDateOrNull(JsonObject o, String key, LocalDate date) {
		if (date == null) {
			o.add(key, JsonNull.INSTANCE);
		} else {
			o.addProperty(key, date.toString());
		}
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
		if (s == null || s.isBlank()) {
			return defaultVal;
		}
		return s;
	}
}
