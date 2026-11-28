package modules.shared.infrastructure.controllers;

import java.sql.SQLException;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;

import static core.config.Global.__;

import modules.shared.domain.enums.SharedMessageEnum;
import modules.shared.domain.services.SqlErrorCode;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.exceptions.InterfaceException;

/**
 * Lo comun de los controllers de JavaFX: como se ensena un error. Una excepcion controlada (InterfaceException) se
 * muestra con su mensaje; un error de la BD destino (SQLException, que los servicios dejan subir hasta aqui) con su
 * codigo; cualquier otra es un fallo inesperado y se ensena con su clase para poder rastrearla.
 */
public abstract class AbstractFxController {

    protected String getErrorMessage(Throwable throwable) {
        if (throwable instanceof InterfaceException) {
            return throwable.getMessage();
        }
        if (throwable instanceof SQLException sqlException) {
            return __(SharedMessageEnum.SQL_ERROR_X_X, SqlErrorCode.getCode(sqlException), sqlException.getMessage());
        }
        return __(SharedMessageEnum.UNEXPECTED_ERROR) + ": " + throwable.getClass().getSimpleName() + " " + throwable.getMessage();
    }

    protected void showError(Throwable throwable) {
        showErrorMessage(getErrorMessage(throwable));
    }

    protected void showErrorMessage(String errorMessage) {
        Alert alert = new Alert(Alert.AlertType.ERROR, errorMessage, ButtonType.OK);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    protected boolean isConfirmed(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    /**
     * Para borrar, como SQLyog: siempre se pregunta y el boton por defecto es No (un Intro de mas no borra nada).
     */
    protected boolean isDeleteConfirmed(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setHeaderText(null);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
        Button noButton = (Button) alert.getDialogPane().lookupButton(ButtonType.NO);
        noButton.setDefaultButton(true);
        alert.setOnShown(event -> noButton.requestFocus());
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }
}
