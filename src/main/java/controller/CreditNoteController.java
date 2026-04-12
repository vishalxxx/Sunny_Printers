package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Client;
import repository.ClientRepository;
import utils.Toast;

import java.time.LocalDate;
import java.util.List;

public class CreditNoteController {

    @FXML
    private ComboBox<Client> clientComboBox;

    @FXML
    private DatePicker datePicker;

    @FXML
    private TextField amountField;

    @FXML
    private TextArea reasonField;

    private ClientRepository clientRepository = new ClientRepository();

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());
        loadClients();
    }

    private void loadClients() {
        List<Client> clients = clientRepository.findAllSortedById();
        ObservableList<Client> clientList = FXCollections.observableArrayList(clients);
        clientComboBox.setItems(clientList);

        clientComboBox.setConverter(new javafx.util.StringConverter<Client>() {
            @Override
            public String toString(Client client) {
                return client != null ? client.getBusinessName() + " (" + client.getClientName() + ")" : "";
            }

            @Override
            public Client fromString(String string) {
                return null;
            }
        });
    }

    @FXML
    private void handleSaveCreditNote(ActionEvent event) {
        Client selectedClient = clientComboBox.getValue();
        if (selectedClient == null || amountField.getText().isEmpty()) {
            System.out.println("⚠ Required fields missing!");
            return;
        }

        System.out.println("Credit Note Saved: " + amountField.getText() + " for " + selectedClient.getBusinessName());
        
        Stage stage = (Stage) amountField.getScene().getWindow();
        Toast.show(stage, "Credit Note saved successfully!");

        // Clear fields after saving
        amountField.clear();
        reasonField.clear();
        clientComboBox.getSelectionModel().clearSelection();
        datePicker.setValue(LocalDate.now());
    }
}
