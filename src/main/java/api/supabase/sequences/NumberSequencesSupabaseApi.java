package api.supabase.sequences;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.NumberSequence;
import model.User;
import utils.SessionManager;

public final class NumberSequencesSupabaseApi {

	private static final String COL_KEY = "sequence_key";

	private final SupabaseRestClient http;

	public NumberSequencesSupabaseApi(SupabaseRestClient http) {
		this.http = http;
	}

	public List<NumberSequence> listAll() throws IOException, InterruptedException {
		HttpResponse<String> res = http.get(SupabaseEndpoints.NUMBER_SEQUENCES, "select=*&order=sequence_key.asc");
		int code = res.statusCode();
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP " + code + " " + res.body());
		}
		return parseArray(res.body());
	}

	public java.util.Optional<NumberSequence> fetchByKey(String sequenceKey) throws IOException, InterruptedException {
		if (sequenceKey == null || sequenceKey.isBlank()) {
			return java.util.Optional.empty();
		}
		String enc = java.net.URLEncoder.encode(sequenceKey.trim(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
		HttpResponse<String> res = http.get(SupabaseEndpoints.NUMBER_SEQUENCES,
				"select=*&sequence_key=eq." + enc + "&limit=1");
		int code = res.statusCode();
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP " + code + " " + res.body());
		}
		List<NumberSequence> rows = parseArray(res.body());
		return rows.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(rows.get(0));
	}

	public NumberSequence incrementAtomic(String sequenceKey, String financialYear) throws IOException, InterruptedException {
		if (sequenceKey == null || sequenceKey.isBlank()) {
			throw new IllegalArgumentException("sequenceKey cannot be null or empty");
		}
		if (financialYear == null || financialYear.isBlank()) {
			throw new IllegalArgumentException("financialYear cannot be null or empty");
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("seq_key", sequenceKey.trim());
		payload.addProperty("ref_fy", financialYear.trim());

		HttpResponse<String> res = http.postJsonRaw("rpc/increment_number_sequence", payload.toString(), "return=representation");
		int code = res.statusCode();
		String body = res.body() != null ? res.body().trim() : "";
		System.out.println("[NumberSequencesSupabaseApi] raw RPC response for " + sequenceKey + ": " + body);
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP " + code + " " + body);
		}
		
		long newNumber = 0;
		try {
			JsonElement element = JsonParser.parseString(body);
			if (element.isJsonArray()) {
				JsonArray arr = element.getAsJsonArray();
				if (arr.size() > 0) {
					JsonElement first = arr.get(0);
					if (first.isJsonObject()) {
						JsonObject obj = first.getAsJsonObject();
						if (obj.has("increment_number_sequence")) {
							newNumber = obj.get("increment_number_sequence").getAsLong();
						} else {
							throw new IOException("Missing expected field 'increment_number_sequence' in response object");
						}
					} else if (first.isJsonPrimitive()) {
						newNumber = first.getAsLong();
					} else {
						throw new IOException("Expected a JSON object or primitive as the first element in array");
					}
				} else {
					throw new IOException("RPC returned empty array");
				}
			} else if (element.isJsonPrimitive()) {
				newNumber = element.getAsLong();
			} else if (element.isJsonObject()) {
				JsonObject obj = element.getAsJsonObject();
				if (obj.has("increment_number_sequence")) {
					newNumber = obj.get("increment_number_sequence").getAsLong();
				} else {
					throw new IOException("Missing expected field 'increment_number_sequence' in response object");
				}
			} else {
				throw new IOException("Unsupported JSON structure: " + body);
			}
		} catch (Exception ex) {
			try {
				newNumber = Long.parseLong(body);
			} catch (NumberFormatException nfe) {
				throw new IOException("Failed to parse sequence increment response as JSON or scalar: " + body, ex);
			}
		}

		NumberSequence row = new NumberSequence();
		row.setSequenceKey(sequenceKey.trim());
		row.setCurrentNumber(newNumber);
		row.setFinancialYear(financialYear.trim());
		row.setDigitWidth(4);
		row.setPrefix("");
		row.setDisplayName(sequenceKey.trim());
		return row;
	}


	private boolean isCurrentUserAdmin() {
		try {
			User current = SessionManager.getInstance().getCurrentUser();
			return current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());
		} catch (Exception e) {
			return false;
		}
	}

	public NumberSequence incrementAndPersist(NumberSequence row) throws IOException, InterruptedException {
		if (!isCurrentUserAdmin()) {
			throw new SecurityException("Only admin is allowed to push number sequences to Supabase.");
		}
		if (row == null || row.getSequenceKey() == null || row.getSequenceKey().isBlank()) {
			throw new IllegalArgumentException("sequence row");
		}
		row.setCurrentNumber(row.getCurrentNumber() + 1);
		upsertAll(java.util.List.of(row));
		return row;
	}


	public static List<NumberSequence> findChanged(List<NumberSequence> local, Map<String, NumberSequence> remoteByKey) {
		List<NumberSequence> changed = new ArrayList<>();
		if (local == null || local.isEmpty()) {
			return changed;
		}
		Map<String, NumberSequence> remote = remoteByKey != null ? remoteByKey : Map.of();
		for (NumberSequence row : local) {
			if (row == null || row.getSequenceKey() == null || row.getSequenceKey().isBlank()) {
				continue;
			}
			NumberSequence remoteRow = remote.get(row.getSequenceKey().trim());
			if (remoteRow == null || differsFromRemote(row, remoteRow)) {
				changed.add(row);
			}
		}
		return changed;
	}

	public static Map<String, NumberSequence> indexByKey(List<NumberSequence> rows) {
		Map<String, NumberSequence> map = new HashMap<>();
		if (rows == null) {
			return map;
		}
		for (NumberSequence row : rows) {
			if (row != null && row.getSequenceKey() != null && !row.getSequenceKey().isBlank()) {
				map.put(row.getSequenceKey().trim(), row);
			}
		}
		return map;
	}

	public static boolean differsFromRemote(NumberSequence local, NumberSequence remote) {
		if (local == null || remote == null) {
			return true;
		}
		if (local.getCurrentNumber() != remote.getCurrentNumber()) {
			return true;
		}
		if (local.getDigitWidth() != remote.getDigitWidth()) {
			return true;
		}
		return !nz(local.getDisplayName()).equals(nz(remote.getDisplayName()))
				|| !nz(local.getPrefix()).equals(nz(remote.getPrefix()))
				|| !nz(local.getFinancialYear()).equals(nz(remote.getFinancialYear()));
	}

	public void upsertAll(List<NumberSequence> rows) throws IOException, InterruptedException {
		if (!isCurrentUserAdmin()) {
			throw new SecurityException("Only admin is allowed to push number sequences to Supabase.");
		}
		if (rows == null || rows.isEmpty()) {
			return;
		}
		JsonArray body = new JsonArray();
		for (NumberSequence row : rows) {
			if (row == null || row.getSequenceKey() == null || row.getSequenceKey().isBlank()) {
				continue;
			}
			body.add(toRemoteRow(row));
		}
		if (body.isEmpty()) {
			return;
		}
		String prefer = "resolution=merge-duplicates,return=minimal";
		HttpResponse<String> res = http.postJsonWithQuery(SupabaseEndpoints.NUMBER_SEQUENCES,
				"on_conflict=" + COL_KEY, body.toString(), prefer);
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	private static JsonObject toRemoteRow(NumberSequence row) {
		JsonObject o = new JsonObject();
		o.addProperty("sequence_key", row.getSequenceKey().trim());
		o.addProperty("display_name", nz(row.getDisplayName()));
		o.addProperty("prefix", nz(row.getPrefix()));
		o.addProperty("current_number", row.getCurrentNumber());
		o.addProperty("digit_width", Math.max(1, row.getDigitWidth()));
		o.addProperty("financial_year", nz(row.getFinancialYear()));
		o.addProperty("updated_at", Instant.now().toString());
		return o;
	}

	private static String nz(String s) {
		return s != null ? s.trim() : "";
	}

	private static List<NumberSequence> parseArray(String json) {
		List<NumberSequence> out = new ArrayList<>();
		if (json == null || json.isBlank()) {
			return out;
		}
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonArray()) {
			return out;
		}
		for (JsonElement el : root.getAsJsonArray()) {
			if (el.isJsonObject()) {
				out.add(fromJson(el.getAsJsonObject()));
			}
		}
		return out;
	}

	private static NumberSequence fromJson(JsonObject o) {
		NumberSequence row = new NumberSequence();
		row.setSequenceKey(text(o, "sequence_key"));
		row.setDisplayName(text(o, "display_name"));
		row.setPrefix(text(o, "prefix"));
		row.setCurrentNumber(o.has("current_number") && !o.get("current_number").isJsonNull()
				? o.get("current_number").getAsLong()
				: 0L);
		row.setDigitWidth(o.has("digit_width") && !o.get("digit_width").isJsonNull()
				? o.get("digit_width").getAsInt()
				: 3);
		row.setFinancialYear(text(o, "financial_year"));
		return row;
	}

	private static String text(JsonObject o, String key) {
		if (o == null || key == null || !o.has(key) || o.get(key).isJsonNull()) {
			return "";
		}
		return o.get(key).getAsString();
	}
}
