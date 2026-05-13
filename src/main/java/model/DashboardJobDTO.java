package model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DashboardJobDTO {

    private final StringProperty orderClient = new SimpleStringProperty();
    private final StringProperty projectDetails = new SimpleStringProperty();
    private final StringProperty received = new SimpleStringProperty();
    private final StringProperty dueDate = new SimpleStringProperty();
    private final StringProperty valuation = new SimpleStringProperty();
    private final StringProperty workflow = new SimpleStringProperty();

    public DashboardJobDTO(String orderClient, String projectDetails, String received,
            String dueDate, String valuation, String workflow) {
        this.orderClient.set(orderClient);
        this.projectDetails.set(projectDetails);
        this.received.set(received);
        this.dueDate.set(dueDate);
        this.valuation.set(valuation);
        this.workflow.set(workflow);
    }

    public String getOrderClient() {
        return orderClient.get();
    }

    public StringProperty orderClientProperty() {
        return orderClient;
    }

    public String getProjectDetails() {
        return projectDetails.get();
    }

    public StringProperty projectDetailsProperty() {
        return projectDetails;
    }

    public String getReceived() {
        return received.get();
    }

    public StringProperty receivedProperty() {
        return received;
    }

    public String getDueDate() {
        return dueDate.get();
    }

    public StringProperty dueDateProperty() {
        return dueDate;
    }

    public String getValuation() {
        return valuation.get();
    }

    public StringProperty valuationProperty() {
        return valuation;
    }

    public String getWorkflow() {
        return workflow.get();
    }

    public StringProperty workflowProperty() {
        return workflow;
    }
}
