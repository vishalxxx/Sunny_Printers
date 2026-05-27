package api.supabase;

/**
 * Public PostgREST resource names under {@code /rest/v1/}.
 */
public enum SupabaseEndpoints {
	USERS("users"),
	CLIENTS("clients"),
	SUPPLIERS("suppliers"),
	SYSTEM_SETTINGS("system_settings"),
	EMAIL_SETTINGS("email_settings"),
	SUPABASE_SETTINGS("supabase_settings"),
	INVOICE_MASTER("invoice_master"),
	INVOICE_ADJUSTMENTS("invoice_adjustments"),
	INVOICE_HISTORY("invoice_history"),
	JOBS("jobs"),
	JOB_ITEMS("job_items"),
	PRINTING_ITEMS("printing_items"),
	PAPER_ITEMS("paper_items"),
	BINDING_ITEMS("binding_items"),
	LAMINATION_ITEMS("lamination_items"),
	CTP_ITEMS("ctp_items"),
	PAYMENTS("payments"),
	PAYMENT_DETAILS("payment_details"),
	PAYMENT_ALLOCATIONS("payment_allocations"),
	BILLING("billing"),
	INVOICE_JOB_MAPPING("invoice_job_mapping"),
	BANK_DETAILS("bank_details"),
	COMPANY_DETAILS("company_details"),
	HSN_SAC_MASTER("hsn_sac_master"),
	USER_PROFILES("user_profiles"),
	NUMBER_SEQUENCES("number_sequences"),
	DOCUMENT_NUMBER_MAPPINGS("document_number_mappings");

	private final String pathSegment;

	SupabaseEndpoints(String pathSegment) {
		this.pathSegment = pathSegment;
	}

	public String pathSegment() {
		return pathSegment;
	}
}
