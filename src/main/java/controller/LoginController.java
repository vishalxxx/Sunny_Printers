package controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import service.LoginService;


public class LoginController {

    @FXML
    private TextField nameField;

    @FXML
    private void onSubmitClick() {
    	LoginService login = new LoginService();
    	login.login(nameField.getText());
    }

    @FXML
    private void onCancelClick() {
        
    }
}
