package api.supabase.invoices;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.InvoiceMaster;
import utils.DocumentNumbering;

/** PostgREST for {@code invoice_master}. Upsert merges on {@code uuid}. */
public final class InvoicesSupabaseApi {

	private static final String COL_UUID = "uuid";
	private final SupabaseRestClient http;

	public InvoicesSupabaseApi(SupabaseRestClient http) {
		this.http = http;
	}

	public void upsert(InvoiceMaster inv) throws IOException, InterruptedException {
		if (inv == null || inv.getUuid() == null || inv.getUuid().isBlank()) {
			return;
		}
		JsonArray body = new JsonArray();
		JsonObject row = toRemoteRow(inv);
		row.addProperty("sync_status", "SYNCED");
		row.addProperty("synced_at", Instant.now().toString());
		body.add(row);
		HttpResponse<String> res = http.postJsonWithQuery(SupabaseEndpoints.INVOICE_MASTER,
				"on_conflict=" + COL_UUID, body.toString(), "resolution=merge-duplicates,return=minimal");
		int code = res.statusCode();
		if (code >= 200 && code < 300) {
			return;
		}
		throw new IOException("HTTP " + code + " " + res.body());
	}

	private static JsonObject toRemoteRow(InvoiceMaster inv) {
		JsonObject o = new JsonObject();
		o.addProperty(COL_UUID, inv.getUuid().trim());
		o.addProperty("invoice_no", DocumentNumbering.stripLeadingHash(nz(inv.getInvoiceNo())));
		addNullableUuid(o, "client_uuid", inv.getClientUuid());
		o.addProperty("client_name", nz(inv.getClientName()));
		addDateOrNull(o, "invoice_date", inv.getInvoiceDate());
		addDateOrNull(o, "period_from", inv.getPeriodFrom());
		addDateOrNull(o, "period_to", inv.getPeriodTo());
		o.addProperty("amount", inv.getAmount());
		o.addProperty("total_after_tax", inv.getTotalAfterTax());
		o.addProperty("round_off", inv.getRoundOff());
		o.addProperty("paid_amount", inv.getPaidAmount());
		o.addProperty("due_amount", inv.getDueAmount());
		o.addProperty("payment_status", nz(inv.getPaymentStatus()));
		addDateOrNull(o, "last_payment_date", inv.getLastPaymentDate());
		o.addProperty("type", nz(inv.getType()));
		o.addProperty("status", nz(inv.getStatus()));
		o.addProperty("is_void", inv.isVoid() ? 1 : 0);
		o.addProperty("void_reason", nz(inv.getVoidReason()));
		addDateOrNull(o, "void_date", inv.getVoidDate());
		addNullableUuid(o, "replaced_by_invoice_uuid", inv.getReplacedByInvoiceUuid());
		addNullableUuid(o, "parent_invoice_uuid", inv.getParentInvoiceUuid());
		o.addProperty("status_updated_by", nz(inv.getStatusUpdatedBy()));
		o.addProperty("file_path", nz(inv.getFilePath()));
		o.addProperty("document_series", nz(inv.getDocumentSeries()));
		
		o.addProperty("place_of_supply", nz(inv.getPlaceOfSupply()));
		o.addProperty("payment_terms", nz(inv.getPaymentTerms()));
		addDateOrNull(o, "due_date", inv.getDueDate());
		o.addProperty("vehicle_dispatch", nz(inv.getVehicleDispatch()));
		o.addProperty("po_no", nz(inv.getPoNo()));
		addDateOrNull(o, "po_date", inv.getPoDate());
		o.addProperty("dispatch_through", nz(inv.getDispatchThrough()));
		o.addProperty("lr_tracking_no", nz(inv.getLrTrackingNo()));
		o.addProperty("remarks", nz(inv.getRemarks()));
		o.addProperty("eway_bill_no", nz(inv.getEwayBillNo()));

		o.addProperty("is_deleted", inv.getIsDeleted());
		o.addProperty("is_active", inv.getIsActive() > 0);
		o.addProperty("sync_version", Math.max(1, inv.getSyncVersion()));
		addTimestampOrNull(o, "created_at", inv.getCreatedAt());
		addTimestampOrNull(o, "updated_at", inv.getUpdatedAt());
		return o;
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
}
