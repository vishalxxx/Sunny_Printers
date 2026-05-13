package service;

import model.CompanyDetails;
import repository.CompanyDetailsRepository;
import utils.CompanyProfile;
import utils.DBConnection;

import java.sql.Connection;
import java.util.List;

public class CompanyDetailsService {

	private final CompanyDetailsRepository repo = new CompanyDetailsRepository();

	public List<CompanyDetails> listActive() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listAll(con, false);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load companies", e);
		}
	}

	public List<CompanyDetails> listAllIncludingInactive() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.listAll(con, true);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load companies", e);
		}
	}

	public CompanyDetails getDefault() {
		try (Connection con = DBConnection.getConnection()) {
			return repo.findDefault(con);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load default company", e);
		}
	}

	public CompanyDetails save(CompanyDetails c) {
		if (c == null) throw new IllegalArgumentException("company");
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				if (c.isDefault()) {
					repo.clearDefault(con);
				}
				if (c.getId() <= 0) {
					int id = repo.insert(con, c);
					c.setId(id);
				} else {
					repo.update(con, c);
				}
				con.commit();

				// Backward-compatible: keep Preferences in sync with the default company
				if (c.isDefault()) {
					CompanyProfile.setName(c.getTradeName());
					CompanyProfile.setAddress(c.getAddress());
					CompanyProfile.setPhone(c.getPhone());
					CompanyProfile.setEmail(c.getEmail());
					CompanyProfile.setGst(c.getGstin());
				}
				return c;
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception ignored) {}
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception ignored) {}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to save company", e);
		}
	}

	public void setDefaultCompany(int companyId) {
		if (companyId <= 0) {
			return;
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				repo.setDefault(con, companyId);
				con.commit();
			} catch (Exception e) {
				try { con.rollback(); } catch (Exception ignored) {}
				throw e;
			} finally {
				try { con.setAutoCommit(true); } catch (Exception ignored) {}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to set default company", e);
		}

		// sync Preferences with default company (for legacy invoice services)
		try {
			CompanyDetails def = getDefault();
			if (def != null) {
				CompanyProfile.setName(def.getTradeName());
				CompanyProfile.setAddress(def.getAddress());
				CompanyProfile.setPhone(def.getPhone());
				CompanyProfile.setEmail(def.getEmail());
				CompanyProfile.setGst(def.getGstin());
			}
		} catch (Exception ignored) {
		}
	}

	public void delete(int id) {
		if (id <= 0) return;
		try (Connection con = DBConnection.getConnection()) {
			repo.delete(con, id);
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete company", e);
		}
	}
}

