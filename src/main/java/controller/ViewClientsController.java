package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.Client;
import repository.ClientRepository;

public class ViewClientsController implements Initializable {

	@FXML
	private TextField searchField;
	@FXML
	private TableView<Client> clientTable;

	@FXML
	private TableColumn<Client, Number> idCol;
	@FXML
	private TableColumn<Client, String> businessCol;
	@FXML
	private TableColumn<Client, String> clientCol;
	@FXML
	private TableColumn<Client, String> nickCol;
	@FXML
	private TableColumn<Client, String> phoneCol;
	@FXML
	private TableColumn<Client, String> altPhoneCol;
	@FXML
	private TableColumn<Client, String> emailCol;
	@FXML
	private TableColumn<Client, String> gstCol;
	@FXML
	private TableColumn<Client, String> panCol;

	private final ClientRepository repo = new ClientRepository();
	private final ObservableList<Client> masterList = FXCollections.observableArrayList();

	private FilteredList<Client> filteredList;
	private SortedList<Client> sortedList;

	@Override
	public void initialize(URL url, ResourceBundle rb) {

		// ----------------------------
		// 1️⃣ Map Table Columns
		// ----------------------------
		idCol.setCellValueFactory(c -> c.getValue().idProperty());
		businessCol.setCellValueFactory(c -> c.getValue().businessNameProperty());
		clientCol.setCellValueFactory(c -> c.getValue().clientNameProperty());
		nickCol.setCellValueFactory(c -> c.getValue().nickNameProperty());
		phoneCol.setCellValueFactory(c -> c.getValue().phoneProperty());
		altPhoneCol.setCellValueFactory(c -> c.getValue().altPhoneProperty());
		emailCol.setCellValueFactory(c -> c.getValue().emailProperty());
		gstCol.setCellValueFactory(c -> c.getValue().gstProperty());
		panCol.setCellValueFactory(c -> c.getValue().panProperty());

		// ----------------------------
		// 2️⃣ Load all clients from DB
		// ----------------------------
		masterList.addAll(repo.findAllSortedById());

		// ----------------------------
		// 3️⃣ Wrap inside FilteredList
		// ----------------------------
		filteredList = new FilteredList<>(masterList, p -> true);

		// ----------------------------
		// 4️⃣ Live search listener
		// ----------------------------
		searchField.textProperty().addListener((obs, oldVal, newVal) -> {

			String keyword = newVal == null ? "" : newVal.toLowerCase().trim();

			filteredList.setPredicate(client -> {
				if (keyword.isBlank())
					return true;

				return client.getBusinessName().toLowerCase().contains(keyword)
						|| client.getClientName().toLowerCase().contains(keyword)
						|| client.getNickName().toLowerCase().contains(keyword)
						|| client.getPhone().toLowerCase().contains(keyword)
						|| client.getAltPhone().toLowerCase().contains(keyword)
						|| client.getEmail().toLowerCase().contains(keyword)
						|| client.getGst().toLowerCase().contains(keyword)
						|| client.getPan().toLowerCase().contains(keyword)
						|| String.valueOf(client.getId()).contains(keyword);
			});
		});

		// ----------------------------
		// 5️⃣ SortedList binds table sort
		// ----------------------------
		sortedList = new SortedList<>(filteredList);
		sortedList.comparatorProperty().bind(clientTable.comparatorProperty());

		// ----------------------------
		// 6️⃣ Show data in Table
		// ----------------------------
		clientTable.setItems(sortedList);
	}
}
