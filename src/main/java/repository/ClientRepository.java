package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import model.Client;
import utils.DBConnection;

public class ClientRepository {

	// SAVE CLIENT INTO DATABASE
	public boolean save(Client client) {

		String sql = "INSERT INTO clients (business_name, client_name, nick_name, phone, alt_phone, email, gst, pan, billing_address, shipping_address, notes) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

			ps.setString(1, client.getBusinessName());
			ps.setString(2, client.getClientName());
			ps.setString(3, client.getNickName());
			ps.setString(4, client.getPhone());
			ps.setString(5, client.getAltPhone());
			ps.setString(6, client.getEmail());
			ps.setString(7, client.getGst());
			ps.setString(8, client.getPan());
			ps.setString(9, client.getBillingAddress());
			ps.setString(10, client.getShippingAddress());
			ps.setString(11, client.getNotes());

			return ps.executeUpdate() > 0;

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return false;
	}

	public List<Client> findAllSortedById() {
		List<Client> list = new ArrayList<>();

		String sql = "SELECT * FROM clients ORDER BY id ASC";

		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				Client client = new Client(rs.getString("business_name"), rs.getString("client_name"),
						rs.getString("nick_name"), rs.getString("phone"), rs.getString("alt_phone"),
						rs.getString("email"), rs.getString("gst"), rs.getString("pan"),
						rs.getString("billing_address"), rs.getString("shipping_address"), rs.getString("notes"));
				client.setId(rs.getInt("id"));
				list.add(client);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	public List<Client> search(String keyword) {

		List<Client> list = new ArrayList<>();

		String sql = "SELECT * FROM clients WHERE " + "CAST(id AS TEXT) LIKE ? OR " + // <-- added this
				"business_name LIKE ? OR " + "client_name LIKE ? OR " + "nick_name LIKE ? OR " + "phone LIKE ? OR "
				+ "alt_phone LIKE ? OR " + "email LIKE ? OR " + "gst LIKE ? OR " + "pan LIKE ? "
				+ "ORDER BY business_name ASC";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

			String value = "%" + keyword + "%";

			for (int i = 1; i <= 9; i++) {
				ps.setString(i, value);
			}

			ResultSet rs = ps.executeQuery();

			while (rs.next()) {

				Client client = new Client(rs.getString("business_name"), rs.getString("client_name"),
						rs.getString("nick_name"), rs.getString("phone"), rs.getString("alt_phone"),
						rs.getString("email"), rs.getString("gst"), rs.getString("pan"),
						rs.getString("billing_address"), rs.getString("shipping_address"), rs.getString("notes"));

				client.setId(rs.getInt("id"));
				list.add(client);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	public boolean deleteById(int id) {

		String sql = "DELETE FROM clients WHERE id = ?";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

			ps.setInt(1, id);

			return ps.executeUpdate() > 0;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	private void openEditClient(Client client) {
		System.out.println("Edit client: " + client.getId());

		// NEXT STEP: load edit_client.fxml and pass the client data
	}

	public boolean update(Client client) {
		String sql = "UPDATE clients SET business_name=?, client_name=?, nick_name=?, phone=?, alt_phone=?, email=?, gst=?, pan=?, billing_address=?, shipping_address=?, notes=? WHERE id=?";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

			ps.setString(1, client.getBusinessName());
			ps.setString(2, client.getClientName());
			ps.setString(3, client.getNickName());
			ps.setString(4, client.getPhone());
			ps.setString(5, client.getAltPhone());
			ps.setString(6, client.getEmail());
			ps.setString(7, client.getGst());
			ps.setString(8, client.getPan());
			ps.setString(9, client.getBillingAddress());
			ps.setString(10, client.getShippingAddress());
			ps.setString(11, client.getNotes());
			ps.setInt(12, client.getId());

			return ps.executeUpdate() > 0;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

}
