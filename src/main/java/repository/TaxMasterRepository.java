package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.TaxMasterItem;

public class TaxMasterRepository {

	public List<String> distinctItemTypes(Connection con) throws Exception {
		List<String> out = new ArrayList<>();
		String sql = """
				SELECT DISTINCT trim(item_type) AS t
				FROM hsn_sac_master
				WHERE trim(coalesce(item_type,'')) != ''
				ORDER BY upper(t)
				""";
		try (PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				String t = rs.getString("t");
				if (t != null && !t.isBlank()) {
					out.add(t.trim());
				}
			}
		}
		return out;
	}

	public TaxMasterItem findByUuid(Connection con, String uuid) throws Exception {
		if (uuid == null || uuid.isBlank()) {
			return null;
		}
		String sql = """
				SELECT uuid, item_type, item_name, keyword, code_type, hsn_sac, gst_rate,
				       unit_default, description, is_favorite, is_active, created_at
				FROM hsn_sac_master
				WHERE uuid = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, uuid.trim());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? map(rs) : null;
			}
		}
	}

	/**
	 * @param activeFilter {@code 1} = active rows only, {@code 0} = inactive only, {@code null} = both
	 */
	public int countFiltered(Connection con, String search, String categoryOrNull, String codeTypeOrNull,
			Integer activeFilter) throws Exception {
		QueryParts q = buildFilterSql(search, categoryOrNull, codeTypeOrNull, activeFilter, true);
		try (PreparedStatement ps = con.prepareStatement(q.sql())) {
			q.bind(ps);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	public List<TaxMasterItem> listFiltered(Connection con, String search, String categoryOrNull,
			String codeTypeOrNull, Integer activeFilter, int offset, int limit) throws Exception {
		QueryParts q = buildFilterSql(search, categoryOrNull, codeTypeOrNull, activeFilter, false);
		String sql = q.sql() + """
				ORDER BY is_active DESC, is_favorite DESC, item_name COLLATE NOCASE, uuid
				LIMIT ? OFFSET ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = q.bind(ps);
			ps.setInt(i++, Math.max(1, limit));
			ps.setInt(i, Math.max(0, offset));
			List<TaxMasterItem> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(map(rs));
				}
			}
			return out;
		}
	}

	private record QueryParts(String sql, List<Object> params) {
		int bind(PreparedStatement ps) throws Exception {
			int i = 1;
			for (Object p : params) {
				if (p instanceof String s) {
					ps.setString(i++, s);
				} else if (p instanceof Integer n) {
					ps.setInt(i++, n);
				} else {
					ps.setObject(i++, p);
				}
			}
			return i;
		}
	}

	private QueryParts buildFilterSql(String search, String categoryOrNull, String codeTypeOrNull,
			Integer activeFilter, boolean countOnly) {
		StringBuilder sb = new StringBuilder();
		if (countOnly) {
			sb.append("SELECT COUNT(*) FROM hsn_sac_master WHERE IFNULL(is_deleted, 0) = 0 ");
		} else {
			sb.append("""
					SELECT uuid, item_type, item_name, keyword, code_type, hsn_sac, gst_rate,
					       unit_default, description, is_favorite, is_active, created_at
					FROM hsn_sac_master
					WHERE IFNULL(is_deleted, 0) = 0
					""");
		}
		List<Object> params = new ArrayList<>();
		if (activeFilter != null) {
			sb.append(" AND IFNULL(is_active, 1) = ? ");
			params.add(activeFilter.intValue() != 0 ? 1 : 0);
		}
		if (categoryOrNull != null && !categoryOrNull.isBlank()) {
			sb.append(" AND upper(trim(item_type)) = upper(?) ");
			params.add(categoryOrNull.trim());
		}
		if (codeTypeOrNull != null && !codeTypeOrNull.isBlank()) {
			String cType = codeTypeOrNull.trim().toUpperCase(java.util.Locale.ROOT);
			if ("HSN".equals(cType)) {
				sb.append(" AND (upper(trim(COALESCE(code_type, ''))) = 'HSN' OR trim(COALESCE(code_type, '')) = '') ");
			} else {
				sb.append(" AND upper(trim(COALESCE(code_type, ''))) = ? ");
				params.add(cType);
			}
		}
		if (search != null && !search.isBlank()) {
			String term = "%" + search.trim().replace("%", "\\%").replace("_", "\\_") + "%";
			sb.append("""
					 AND (
					   item_name LIKE ? ESCAPE '\\'
					   OR hsn_sac LIKE ? ESCAPE '\\'
					   OR keyword LIKE ? ESCAPE '\\'
					 )
					""");
			params.add(term);
			params.add(term);
			params.add(term);
		}
		return new QueryParts(sb.toString(), params);
	}

	public String insert(Connection con, TaxMasterItem row) throws Exception {
		String uuid = row.getUuid();
		if (uuid == null || uuid.isBlank()) {
			uuid = java.util.UUID.randomUUID().toString();
			row.setUuid(uuid);
		}
		String sql = """
				INSERT INTO hsn_sac_master (
				  uuid, item_type, item_name, keyword, code_type, hsn_sac, gst_rate,
				  unit_default, description, is_favorite, is_active, sync_status
				) VALUES (?,?,?,?,?,?,?,?,?,?,?, 'PENDING')
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, uuid);
			ps.setString(2, nz(row.getItemType()));
			ps.setString(3, nz(row.getItemName()));
			ps.setString(4, nz(row.getKeyword()));
			ps.setString(5, nz(row.getCodeType()));
			ps.setString(6, nz(row.getHsnSac()));
			ps.setDouble(7, row.getGstRate());
			ps.setString(8, nz(row.getUnitDefault()));
			ps.setString(9, nz(row.getDescription()));
			ps.setInt(10, row.isFavorite() ? 1 : 0);
			ps.setInt(11, row.isActive() ? 1 : 0);
			ps.executeUpdate();
		}
		return uuid;
	}

	public void update(Connection con, TaxMasterItem row) throws Exception {
		String sql = """
				UPDATE hsn_sac_master SET
				  item_type = ?, item_name = ?, keyword = ?, code_type = ?, hsn_sac = ?, gst_rate = ?,
				  unit_default = ?, description = ?, is_favorite = ?, is_active = ?, sync_status = 'PENDING', updated_at = datetime('now')
				WHERE uuid = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			int i = fill(ps, row);
			ps.setString(i, row.getUuid());
			ps.executeUpdate();
		}
	}

	public void setActive(Connection con, String uuid, boolean active) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("UPDATE hsn_sac_master SET is_active = ?, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?")) {
			ps.setInt(1, active ? 1 : 0);
			ps.setString(2, uuid);
			ps.executeUpdate();
		}
	}

	private static int fill(PreparedStatement ps, TaxMasterItem row) throws Exception {
		ps.setString(1, nz(row.getItemType()));
		ps.setString(2, nz(row.getItemName()));
		ps.setString(3, nz(row.getKeyword()));
		ps.setString(4, nz(row.getCodeType()));
		ps.setString(5, nz(row.getHsnSac()));
		ps.setDouble(6, row.getGstRate());
		ps.setString(7, nz(row.getUnitDefault()));
		ps.setString(8, nz(row.getDescription()));
		ps.setInt(9, row.isFavorite() ? 1 : 0);
		ps.setInt(10, row.isActive() ? 1 : 0);
		return 11;
	}

	private static TaxMasterItem map(ResultSet rs) throws Exception {
		TaxMasterItem r = new TaxMasterItem();
		r.setUuid(rs.getString("uuid"));
		r.setItemType(rs.getString("item_type"));
		r.setItemName(rs.getString("item_name"));
		r.setKeyword(rs.getString("keyword"));
		r.setCodeType(rs.getString("code_type"));
		r.setHsnSac(rs.getString("hsn_sac"));
		r.setGstRate(rs.getDouble("gst_rate"));
		r.setUnitDefault(rs.getString("unit_default"));
		r.setDescription(rs.getString("description"));
		r.setFavorite(rs.getInt("is_favorite") != 0);
		r.setActive(rs.getInt("is_active") != 0);
		return r;
	}

	private static String nz(String s) {
		return s != null ? s : "";
	}
}
