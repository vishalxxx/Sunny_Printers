package service.sync;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.Optional;

import model.MasterDocumentSeries;
import service.NumberSequenceAllocationService;
import service.NumberSequenceAllocationService.AllocatedNumber;
import utils.NumberSequenceCatalog;

/**
 * Universal allocator: Supabase number_sequences when reachable, otherwise UniversalTemporaryNumberEngine.
 */
public final class UniversalNumberAllocator {

	private static final UniversalNumberAllocator INSTANCE = new UniversalNumberAllocator();

	private final NumberSequenceAllocationService delegate = new NumberSequenceAllocationService();
	private final UniversalTemporaryNumberEngine temporary = UniversalTemporaryNumberEngine.getInstance();

	private UniversalNumberAllocator() {
	}

	public static UniversalNumberAllocator getInstance() {
		return INSTANCE;
	}

	public boolean isRemoteReachable(String sequenceKey) {
		return temporary.isRemoteSequenceReachable(sequenceKey);
	}

	public AllocatedNumber allocate(Connection con, String sequenceKey, LocalDate refDate) throws Exception {
		return delegate.allocate(con, sequenceKey, refDate);
	}

	public AllocatedNumber allocate(Connection con, MasterDocumentSeries series, LocalDate refDate) throws Exception {
		return delegate.allocate(con, series, refDate);
	}

	public AllocatedNumber allocateClientCode(Connection con) throws Exception {
		return delegate.allocateClientCode(con);
	}

	public AllocatedNumber allocateSupplierCode(Connection con) throws Exception {
		return delegate.allocateSupplierCode(con);
	}

	public AllocatedNumber allocateJobCode(Connection con) throws Exception {
		return delegate.allocate(con, "job", LocalDate.now());
	}

	public Optional<AllocatedNumber> tryAllocatePermanent(Connection con, String sequenceKey, LocalDate refDate)
			throws Exception {
		return delegate.tryAllocatePermanent(con, sequenceKey, refDate);
	}

	public Optional<AllocatedNumber> tryAllocatePermanentClientCode(Connection con) throws Exception {
		return delegate.tryAllocatePermanentClientCode(con);
	}

	public Optional<AllocatedNumber> tryAllocatePermanentSupplierCode(Connection con) throws Exception {
		return delegate.tryAllocatePermanentSupplierCode(con);
	}

	public Optional<AllocatedNumber> tryAllocatePermanentJobCode(Connection con) throws Exception {
		return delegate.tryAllocatePermanent(con, "job", LocalDate.now());
	}

	/** Draft invoice number ({@code TEMP-*}) — always local offline counter, never remote. */
	public AllocatedNumber allocateTempInvoiceNumber(Connection con) throws Exception {
		return temporary.allocateTemporary(con, "temp_invoice");
	}

	public AllocatedNumber allocateInvoiceNumber(Connection con, MasterDocumentSeries series, LocalDate refDate)
			throws Exception {
		MasterDocumentSeries s = series != null ? series : MasterDocumentSeries.GST_INVOICE;
		return delegate.allocate(con, s, refDate != null ? refDate : LocalDate.now());
	}

	public Optional<AllocatedNumber> tryAllocatePermanentInvoice(Connection con, MasterDocumentSeries series,
			LocalDate refDate) throws Exception {
		String key = NumberSequenceCatalog.moduleNameFor(series);
		if (key == null) {
			key = "gst_invoice";
		}
		return delegate.tryAllocatePermanent(con, key, refDate != null ? refDate : LocalDate.now());
	}
}