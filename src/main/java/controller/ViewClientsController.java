package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import model.Client;
import repository.ClientRepository;
import utils.DeleteConfirmationDialog;
import utils.Toast;
import utils.UndoDeleteManager;

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

	@FXML
	private TableColumn<Client, Void> actionCol; // ⭐ Required for buttons

	private final ClientRepository repo = new ClientRepository();
	private final ObservableList<Client> masterList = FXCollections.observableArrayList();

	private FilteredList<Client> filteredList;
	private SortedList<Client> sortedList;

	@Override
	public void initialize(URL url, ResourceBundle rb) {

		// Column mappings
		idCol.setCellValueFactory(c -> c.getValue().idProperty());
		businessCol.setCellValueFactory(c -> c.getValue().businessNameProperty());
		clientCol.setCellValueFactory(c -> c.getValue().clientNameProperty());
		nickCol.setCellValueFactory(c -> c.getValue().nickNameProperty());
		phoneCol.setCellValueFactory(c -> c.getValue().phoneProperty());
		altPhoneCol.setCellValueFactory(c -> c.getValue().altPhoneProperty());
		emailCol.setCellValueFactory(c -> c.getValue().emailProperty());
		gstCol.setCellValueFactory(c -> c.getValue().gstProperty());
		panCol.setCellValueFactory(c -> c.getValue().panProperty());

		// ⭐ Add edit/delete column
		addActionButtons();

		// Load DB data
		masterList.addAll(repo.findAllSortedById());

		// Filtered
		filteredList = new FilteredList<>(masterList, p -> true);

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

		// Sorted
		sortedList = new SortedList<>(filteredList);
		sortedList.comparatorProperty().bind(clientTable.comparatorProperty());

		clientTable.setItems(sortedList);
	}

	private void addActionButtons() {

		actionCol.setCellFactory(col -> new TableCell<>() {

			private final Button editBtn = new Button("Edit");
			private final Button deleteBtn = new Button("Delete");

			{
				editBtn.getStyleClass().add("edit-button");
				deleteBtn.getStyleClass().add("delete-button");

				editBtn.setOnAction(e -> {
					Client client = getTableView().getItems().get(getIndex());
					openEditClient(client);
				});

				deleteBtn.setOnAction(e -> {
					Client client = getTableView().getItems().get(getIndex());
					deleteClient(client);
				});
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);

				if (empty) {
					setGraphic(null);
				} else {
					HBox box = new HBox(10, editBtn, deleteBtn);
					box.setAlignment(Pos.CENTER);
					setGraphic(box);
				}
			}
		});
	}

	private void deleteClient(Client client) {

		boolean confirmed = DeleteConfirmationDialog.show(client.getClientName());
		if (!confirmed)
			return;

		UndoDeleteManager.store(client);

		boolean deleted = repo.deleteById(client.getId());

		// Convert Window → Stage
		Stage stage = (Stage) clientTable.getScene().getWindow();

		if (deleted) {

			masterList.remove(client);

			Toast.showUndo(stage, "Client deleted!", () -> { // UNDO ACTION
				boolean restored = UndoDeleteManager.undo();
				if (restored) {
					masterList.clear();
					masterList.addAll(repo.findAllSortedById());
					Toast.show(stage, "Client restored!");
				}
			}, () -> { // TIMEOUT ACTION → CLEAR MEMORY
				UndoDeleteManager.clear();
			});

		} else {
			Toast.show(stage, "Delete failed!");
		}
	}

	private void openEditClient(Client client) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_client.fxml"));
			Parent view = loader.load();

			EditClientController controller = loader.getController();
			controller.setClientData(client);

			// Open inside main window
			MainController.getInstance().setCenterView(view);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
