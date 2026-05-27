package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import model.DocumentNumberMapping;
import utils.ClientIdentifiers;
import utils.DBConnection;

public class DocumentNumberMappingRepository {

	public void recordPromotion(Connection con, String entityType, String entityUuid, String sequenceKey,
			String temporaryNumber, String permanentNumber, String allocationSource) throws Exception {
		if (entityType == null || entityType.isBlank() || entityUuid == null || entityUuid.isBlank()
				|| temporaryNumber == null || temporaryNumber.isBlank()
				|| permanentNumber == null || permanentNumber.isBlank()) {
			throw new IllegalArgumentException("entityType, entityUuid, temporaryNumber, permanentNumber required");
		}
		String key = sequenceKey != null && !sequenceKey.isBlank() ? sequenceKey.trim() : entityType.trim();
		String source = allocationSource != null && !allocationSource.isBlank()
				? allocationSource.trim()
				: DocumentNumberMapping.SOURCE_REMOTE;
		try (PreparedStatement ps = con.prepareStatement("""
				INSERT INTO document_number_mappings (
				  uuid, entity_type, entity_uuid, sequence_key,
				  temporary_number, permanent_number, allocation_source, sync_status, sync_version, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, datetime('now'), datetime('now'))
				ON CONFLICT(temporary_number) DO UPDATE SET
				  entity_type = excluded.entity_type,
				  entity_uuid = excluded.entity_uuid,
				  sequence_key = excluded.sequence_key,
				  permanent_number = excluded.permanent_number,
				  allocation_source = excluded.allocation_source,
				  sync_status = 'PENDING',
				  sync_version = sync_version + 1,
				  updated_at = datetime('now')
				""")) {
			ps.setString(1, ClientIdentifiers.newUuidV7String());
			ps.setString(2, entityType.trim());
			ps.setString(3, entityUuid.trim());
			ps.setString(4, key);
			ps.setString(5, temporaryNumber.trim());
			ps.setString(6, permanentNumber.trim());
			ps.setString(7, source);
			ps.executeUpdate();
		}
	}

	public Optional<DocumentNumberMapping> findByTemporaryNumber(String temporaryNumber) {
		if (temporaryNumber == null || temporaryNumber.isBlank()) {
			return Optional.empty();
		}
		String sql = """
				SELECT uuid, entity_type, entity_uuid, sequence_key,
				       temporary_number, permanent_number, allocation_source, created_at
				FROM document_number_mappings
				WHERE temporary_number = ?
				""";
		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, temporaryNumber.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapRow(rs));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}

	public Optional<DocumentNumberMapping> findByEntity(String entityType, String entityUuid) {
		if (entityType == null || entityType.isBlank() || entityUuid == null || entityUuid.isBlank()) {
			return Optional.empty();
		}
		String sql = """
				SELECT uuid, entity_type, entity_uuid, sequence_key,
				       temporary_number, permanent_number, allocation_source, created_at
				FROM document_number_mappings
				WHERE entity_type = ? AND entity_uuid = ?
				ORDER BY created_at DESC
				LIMIT 1
				""";
		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, entityType.trim());
			ps.setString(2, entityUuid.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapRow(rs));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}

	public List<DocumentNumberMapping> findAll() {
		List<DocumentNumberMapping> list = new ArrayList<>();
		String sql = """
				SELECT uuid, entity_type, entity_uuid, sequence_key,
				       temporary_number, permanent_number, allocation_source, created_at
				FROM document_number_mappings
				ORDER BY created_at DESC
				""";
		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapRow(rs));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public void recordPromotionImmediate(String entityType, String entityUuid, String sequenceKey,
			String temporaryNumber, String permanentNumber, String allocationSource) throws Exception {
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(true);
			recordPromotion(con, entityType, entityUuid, sequenceKey, temporaryNumber, permanentNumber, allocationSource);
		}
	}

	private static DocumentNumberMapping mapRow(ResultSet rs) throws Exception {
		DocumentNumberMapping m = new DocumentNumberMapping();
		m.setUuid(rs.getString("uuid"));
		m.setEntityType(rs.getString("entity_type"));
		m.setEntityUuid(rs.getString("entity_uuid"));
		m.setSequenceKey(rs.getString("sequence_key"));
		m.setTemporaryNumber(rs.getString("temporary_number"));
		m.setPermanentNumber(rs.getString("permanent_number"));
		m.setAllocationSource(rs.getString("allocation_source"));
		m.setCreatedAt(rs.getString("created_at"));
		return m;
	}
}